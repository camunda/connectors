/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.agentcore.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.aws.agentcore.memory.model.request.AwsAgentCoreMemoryRequest;
import io.camunda.connector.aws.agentcore.memory.model.request.MetadataFilter;
import io.camunda.connector.aws.agentcore.memory.model.request.RetrieveMemoryRecordsOperation;
import io.camunda.connector.aws.agentcore.memory.model.response.AwsAgentCoreMemoryResponse;
import io.camunda.connector.aws.agentcore.memory.model.response.MemoryRecord;
import io.camunda.connector.aws.agentcore.memory.model.response.MemoryRetrievalResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.BedrockAgentCoreException;
import software.amazon.awssdk.services.bedrockagentcore.model.LeftExpression;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryMetadataFilterExpression;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryRecordSummary;
import software.amazon.awssdk.services.bedrockagentcore.model.MetadataValue;
import software.amazon.awssdk.services.bedrockagentcore.model.OperatorType;
import software.amazon.awssdk.services.bedrockagentcore.model.RetrieveMemoryRecordsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.RetrieveMemoryRecordsResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.RightExpression;
import software.amazon.awssdk.services.bedrockagentcore.model.ServiceQuotaExceededException;
import software.amazon.awssdk.services.bedrockagentcore.model.ThrottledException;

public class AwsAgentCoreMemoryExecutor {

  private final BedrockAgentCoreClient client;
  private final ObjectMapper objectMapper;

  public AwsAgentCoreMemoryExecutor(BedrockAgentCoreClient client, ObjectMapper objectMapper) {
    this.client = client;
    this.objectMapper = objectMapper;
  }

  public MemoryRetrievalResult execute(
      AwsAgentCoreMemoryRequest request,
      Function<DocumentCreationRequest, Document> documentFactory) {
    return switch (request.getOperation()) {
      case RetrieveMemoryRecordsOperation op -> retrieve(op, request, documentFactory);
    };
  }

  private MemoryRetrievalResult retrieve(
      RetrieveMemoryRecordsOperation op,
      AwsAgentCoreMemoryRequest request,
      Function<DocumentCreationRequest, Document> documentFactory) {
    try {
      var searchCriteriaBuilder =
          software.amazon.awssdk.services.bedrockagentcore.model.SearchCriteria.builder()
              .searchQuery(op.searchQuery());

      if (op.memoryStrategyId() != null) {
        searchCriteriaBuilder.memoryStrategyId(op.memoryStrategyId());
      }
      if (op.topK() != null) {
        searchCriteriaBuilder.topK(op.topK());
      }
      if (op.metadataFilters() != null && !op.metadataFilters().isEmpty()) {
        searchCriteriaBuilder.metadataFilters(mapMetadataFilters(op.metadataFilters()));
      }

      var sdkRequestBuilder =
          RetrieveMemoryRecordsRequest.builder()
              .memoryId(request.getMemoryId())
              .namespace(op.namespace())
              .searchCriteria(searchCriteriaBuilder.build());

      if (request.getMaxResults() != null) {
        sdkRequestBuilder.maxResults(request.getMaxResults());
      }
      if (op.nextToken() != null) {
        sdkRequestBuilder.nextToken(op.nextToken());
      }

      RetrieveMemoryRecordsResponse sdkResponse =
          client.retrieveMemoryRecords(sdkRequestBuilder.build());

      var response = mapSdkResponse(sdkResponse);
      byte[] json = objectMapper.writeValueAsBytes(response);

      Document doc =
          documentFactory.apply(
              DocumentCreationRequest.from(json)
                  .contentType("application/json")
                  .fileName("agentcore-memory-" + Instant.now().toEpochMilli() + ".json")
                  .build());

      return new MemoryRetrievalResult(doc, response.memoryRecords().size(), response.nextToken());

    } catch (ThrottledException e) {
      throw ConnectorRetryException.builder()
          .errorCode("THROTTLED")
          .message("AWS AgentCore Memory request was throttled: " + e.getMessage())
          .cause(e)
          .build();
    } catch (ServiceQuotaExceededException e) {
      throw new ConnectorException("QUOTA_EXCEEDED", e.getMessage(), e);
    } catch (BedrockAgentCoreException e) {
      var errorMsg =
          e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
      throw new ConnectorException("AWS_ERROR", "AWS AgentCore Memory error: " + errorMsg, e);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          "SERIALIZATION_ERROR", "Failed to serialize memory records to JSON", e);
    }
  }

  private AwsAgentCoreMemoryResponse mapSdkResponse(RetrieveMemoryRecordsResponse sdkResponse) {
    var records =
        sdkResponse.memoryRecordSummaries().stream()
            .map(
                s ->
                    new MemoryRecord(
                        s.memoryRecordId(),
                        extractContent(s),
                        s.memoryStrategyId(),
                        s.hasNamespaces() ? s.namespaces() : List.of(),
                        s.createdAt(),
                        s.score(),
                        flattenMetadata(s)))
            .toList();
    return new AwsAgentCoreMemoryResponse(records, sdkResponse.nextToken());
  }

  private String extractContent(MemoryRecordSummary summary) {
    return summary.content() != null ? summary.content().text() : null;
  }

  private Map<String, String> flattenMetadata(MemoryRecordSummary summary) {
    if (!summary.hasMetadata()) {
      return Map.of();
    }
    return summary.metadata().entrySet().stream()
        .filter(e -> e.getValue() != null && e.getValue().stringValue() != null)
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stringValue()));
  }

  private List<MemoryMetadataFilterExpression> mapMetadataFilters(
      List<MetadataFilter> metadataFilters) {
    return metadataFilters.stream()
        .map(
            f -> {
              var builder =
                  MemoryMetadataFilterExpression.builder()
                      .left(LeftExpression.fromMetadataKey(f.key()))
                      .operator(OperatorType.fromValue(f.operator().name()));

              if (f.value() != null) {
                builder.right(
                    RightExpression.fromMetadataValue(MetadataValue.fromStringValue(f.value())));
              }
              return builder.build();
            })
        .toList();
  }
}
