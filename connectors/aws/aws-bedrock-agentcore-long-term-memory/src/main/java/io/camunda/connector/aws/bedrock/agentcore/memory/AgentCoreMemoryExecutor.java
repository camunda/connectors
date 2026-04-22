/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.agentcore.memory;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.bedrock.agentcore.memory.model.request.AgentCoreMemoryRequest;
import io.camunda.connector.aws.bedrock.agentcore.memory.model.request.ListOperation;
import io.camunda.connector.aws.bedrock.agentcore.memory.model.request.RetrieveOperation;
import io.camunda.connector.aws.bedrock.agentcore.memory.model.response.AgentCoreMemoryResult;
import io.camunda.connector.aws.bedrock.agentcore.memory.model.response.MemoryRecordEntry;
import java.util.Map;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.BedrockAgentCoreException;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryRecordSummary;

public class AgentCoreMemoryExecutor {

  private static final String ERROR_MEMORY_FAILED = "AGENTCORE_MEMORY_FAILED";

  private final BedrockAgentCoreClient client;

  public AgentCoreMemoryExecutor(BedrockAgentCoreClient client) {
    this.client = client;
  }

  public AgentCoreMemoryResult execute(AgentCoreMemoryRequest request) {
    return switch (request.getOperation()) {
      case RetrieveOperation op -> retrieve(op, request);
      case ListOperation op -> list(op, request);
    };
  }

  private AgentCoreMemoryResult retrieve(RetrieveOperation op, AgentCoreMemoryRequest request) {
    try {
      var builder =
          software.amazon.awssdk.services.bedrockagentcore.model.RetrieveMemoryRecordsRequest
              .builder()
              .memoryId(request.getMemoryId())
              .namespace(request.getNamespace())
              .searchCriteria(
                  sc -> {
                    sc.searchQuery(op.query());
                    if (op.memoryStrategyId() != null && !op.memoryStrategyId().isBlank()) {
                      sc.memoryStrategyId(op.memoryStrategyId());
                    }
                    if (op.maxResults() != null) {
                      sc.topK(op.maxResults());
                    }
                  });

      if (op.nextToken() != null && !op.nextToken().isBlank()) {
        builder.nextToken(op.nextToken());
      }

      var response = client.retrieveMemoryRecords(builder.build());

      var records =
          response.memoryRecordSummaries().stream()
              .filter(r -> r.content() != null && r.content().text() != null)
              .map(this::toEntry)
              .toList();

      return new AgentCoreMemoryResult(records, records.size(), response.nextToken());

    } catch (BedrockAgentCoreException e) {
      throw wrapException(e);
    }
  }

  private AgentCoreMemoryResult list(ListOperation op, AgentCoreMemoryRequest request) {
    try {
      var builder =
          software.amazon.awssdk.services.bedrockagentcore.model.ListMemoryRecordsRequest.builder()
              .memoryId(request.getMemoryId())
              .namespace(request.getNamespace());

      if (op.memoryStrategyId() != null && !op.memoryStrategyId().isBlank()) {
        builder.memoryStrategyId(op.memoryStrategyId());
      }
      if (op.maxResults() != null) {
        builder.maxResults(op.maxResults());
      }
      if (op.nextToken() != null && !op.nextToken().isBlank()) {
        builder.nextToken(op.nextToken());
      }

      var response = client.listMemoryRecords(builder.build());

      var records =
          response.memoryRecordSummaries().stream()
              .filter(r -> r.content() != null && r.content().text() != null)
              .map(this::toEntry)
              .toList();

      return new AgentCoreMemoryResult(records, records.size(), response.nextToken());

    } catch (BedrockAgentCoreException e) {
      throw wrapException(e);
    }
  }

  private MemoryRecordEntry toEntry(MemoryRecordSummary r) {
    return new MemoryRecordEntry(
        r.memoryRecordId(),
        r.content().text(),
        r.memoryStrategyId(),
        r.hasNamespaces() ? r.namespaces() : java.util.List.of(),
        r.createdAt(),
        r.score(),
        mapMetadata(r));
  }

  private Map<String, Object> mapMetadata(MemoryRecordSummary r) {
    if (!r.hasMetadata()) {
      return Map.of();
    }
    return r.metadata().entrySet().stream()
        .filter(e -> e.getValue() != null && e.getValue().stringValue() != null)
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stringValue()));
  }

  private ConnectorException wrapException(BedrockAgentCoreException e) {
    var msg = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
    return new ConnectorException(ERROR_MEMORY_FAILED, "AgentCore Memory error: " + msg, e);
  }
}
