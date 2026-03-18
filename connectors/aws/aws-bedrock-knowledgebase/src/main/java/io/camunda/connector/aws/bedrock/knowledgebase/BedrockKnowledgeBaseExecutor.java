/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.knowledgebase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.aws.bedrock.knowledgebase.model.request.BedrockKnowledgeBaseRequest;
import io.camunda.connector.aws.bedrock.knowledgebase.model.request.RetrieveOperation;
import io.camunda.connector.aws.bedrock.knowledgebase.model.response.BedrockKnowledgeBaseResponse;
import io.camunda.connector.aws.bedrock.knowledgebase.model.response.KnowledgeBaseRetrievalResult;
import io.camunda.connector.aws.bedrock.knowledgebase.model.response.RetrievalResultEntry;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.BedrockAgentRuntimeException;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.ThrottlingException;

public class BedrockKnowledgeBaseExecutor {

  private final BedrockAgentRuntimeClient client;
  private final ObjectMapper objectMapper;

  public BedrockKnowledgeBaseExecutor(BedrockAgentRuntimeClient client, ObjectMapper objectMapper) {
    this.client = client;
    this.objectMapper = objectMapper;
  }

  public KnowledgeBaseRetrievalResult execute(
      BedrockKnowledgeBaseRequest request,
      Function<DocumentCreationRequest, Document> documentFactory) {
    return switch (request.getOperation()) {
      case RetrieveOperation op -> retrieve(op, request, documentFactory);
    };
  }

  private KnowledgeBaseRetrievalResult retrieve(
      RetrieveOperation op,
      BedrockKnowledgeBaseRequest request,
      Function<DocumentCreationRequest, Document> documentFactory) {
    try {
      var sdkRequestBuilder =
          RetrieveRequest.builder()
              .knowledgeBaseId(request.getKnowledgeBaseId())
              .retrievalQuery(q -> q.text(op.query()));

      if (op.numberOfResults() != null) {
        sdkRequestBuilder.retrievalConfiguration(
            KnowledgeBaseRetrievalConfiguration.builder()
                .vectorSearchConfiguration(
                    KnowledgeBaseVectorSearchConfiguration.builder()
                        .numberOfResults(op.numberOfResults())
                        .build())
                .build());
      }

      RetrieveResponse sdkResponse = client.retrieve(sdkRequestBuilder.build());

      var response = mapSdkResponse(sdkResponse);
      byte[] json = objectMapper.writeValueAsBytes(response);

      Document doc =
          documentFactory.apply(
              DocumentCreationRequest.from(json)
                  .contentType("application/json")
                  .fileName("kb-retrieval-" + Instant.now().toEpochMilli() + ".json")
                  .build());

      return new KnowledgeBaseRetrievalResult(doc, response.results().size(), response.nextToken());

    } catch (ThrottlingException e) {
      throw ConnectorRetryException.builder()
          .errorCode("THROTTLED")
          .message("Bedrock Knowledge Base request was throttled: " + e.getMessage())
          .cause(e)
          .build();
    } catch (BedrockAgentRuntimeException e) {
      var errorMsg =
          e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
      throw new ConnectorException(
          "KB_RETRIEVAL_FAILED", "Bedrock Knowledge Base error: " + errorMsg, e);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          "SERIALIZATION_ERROR", "Failed to serialize retrieval results to JSON", e);
    }
  }

  private BedrockKnowledgeBaseResponse mapSdkResponse(RetrieveResponse sdkResponse) {
    var results =
        sdkResponse.retrievalResults().stream()
            .map(
                r ->
                    new RetrievalResultEntry(
                        r.content() != null ? r.content().text() : null,
                        r.score(),
                        r.location() != null && r.location().s3Location() != null
                            ? r.location().s3Location().uri()
                            : null,
                        r.hasMetadata()
                            ? r.metadata().entrySet().stream()
                                .collect(
                                    Collectors.toMap(
                                        Map.Entry::getKey,
                                        e -> e.getValue() != null ? e.getValue().toString() : ""))
                            : Map.of()))
            .toList();
    return new BedrockKnowledgeBaseResponse(results, sdkResponse.nextToken());
  }
}
