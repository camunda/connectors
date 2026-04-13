/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.agentcore.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.bedrock.agentcore.memory.model.request.AgentCoreMemoryRequest;
import io.camunda.connector.aws.bedrock.agentcore.memory.model.request.ListOperation;
import io.camunda.connector.aws.bedrock.agentcore.memory.model.request.MemoryOperation;
import io.camunda.connector.aws.bedrock.agentcore.memory.model.request.RetrieveOperation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.BedrockAgentCoreException;
import software.amazon.awssdk.services.bedrockagentcore.model.ListMemoryRecordsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.ListMemoryRecordsResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryContent;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryRecordSummary;
import software.amazon.awssdk.services.bedrockagentcore.model.MetadataValue;
import software.amazon.awssdk.services.bedrockagentcore.model.RetrieveMemoryRecordsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.RetrieveMemoryRecordsResponse;

class AgentCoreMemoryExecutorTest {

  private BedrockAgentCoreClient client;
  private AgentCoreMemoryExecutor executor;

  @BeforeEach
  void setUp() {
    client = mock(BedrockAgentCoreClient.class);
    executor = new AgentCoreMemoryExecutor(client);
  }

  @Test
  void retrieve_shouldPassMemoryIdNamespaceAndQuery() {
    var summary = buildSummary("mem-abc123", "User prefers email", 0.95);
    when(client.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenReturn(RetrieveMemoryRecordsResponse.builder().memoryRecordSummaries(summary).build());

    var request = buildRequest(new RetrieveOperation("customer preferences", null, null));
    var result = executor.execute(request);

    var captor = ArgumentCaptor.forClass(RetrieveMemoryRecordsRequest.class);
    verify(client).retrieveMemoryRecords(captor.capture());
    assertThat(captor.getValue().memoryId()).isEqualTo("test-memory-id-abc123");
    assertThat(captor.getValue().namespace()).isEqualTo("customer/12345");
    assertThat(captor.getValue().searchCriteria().searchQuery()).isEqualTo("customer preferences");
    assertThat(result.resultCount()).isEqualTo(1);
    assertThat(result.records().getFirst().content()).isEqualTo("User prefers email");
    assertThat(result.records().getFirst().score()).isEqualTo(0.95);
  }

  @Test
  void retrieve_shouldPassStrategyIdAndMaxResults() {
    when(client.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenReturn(
            RetrieveMemoryRecordsResponse.builder().memoryRecordSummaries(List.of()).build());

    var request = buildRequest(new RetrieveOperation("query", "semantic_memory", 25));
    executor.execute(request);

    var captor = ArgumentCaptor.forClass(RetrieveMemoryRecordsRequest.class);
    verify(client).retrieveMemoryRecords(captor.capture());
    assertThat(captor.getValue().searchCriteria().memoryStrategyId()).isEqualTo("semantic_memory");
    assertThat(captor.getValue().searchCriteria().topK()).isEqualTo(25);
  }

  @Test
  void retrieve_shouldFilterOutEmptyContent() {
    var withContent = buildSummary("mem-1", "Has content", 0.9);
    var withoutContent =
        MemoryRecordSummary.builder()
            .memoryRecordId("mem-2")
            .memoryStrategyId("default")
            .namespaces("ns")
            .createdAt(Instant.now())
            .build();

    when(client.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenReturn(
            RetrieveMemoryRecordsResponse.builder()
                .memoryRecordSummaries(withContent, withoutContent)
                .build());

    var result = executor.execute(buildRequest(new RetrieveOperation("test", null, null)));
    assertThat(result.resultCount()).isEqualTo(1);
    assertThat(result.records().getFirst().memoryRecordId()).isEqualTo("mem-1");
  }

  @Test
  void retrieve_shouldMapMetadataWithUnwrap() {
    var summary =
        MemoryRecordSummary.builder()
            .memoryRecordId("mem-meta")
            .content(MemoryContent.fromText("fact"))
            .memoryStrategyId("default")
            .namespaces("ns")
            .createdAt(Instant.now())
            .score(0.8)
            .metadata(Map.of("source", MetadataValue.fromStringValue("conversation")))
            .build();

    when(client.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenReturn(RetrieveMemoryRecordsResponse.builder().memoryRecordSummaries(summary).build());

    var result = executor.execute(buildRequest(new RetrieveOperation("test", null, null)));
    assertThat(result.records().getFirst().metadata()).containsEntry("source", "conversation");
  }

  @Test
  void list_shouldPassMemoryIdAndNamespace() {
    var summary = buildSummary("mem-list1", "A stored fact", null);
    when(client.listMemoryRecords(any(ListMemoryRecordsRequest.class)))
        .thenReturn(ListMemoryRecordsResponse.builder().memoryRecordSummaries(summary).build());

    var request = buildRequest(new ListOperation(null, null));
    var result = executor.execute(request);

    var captor = ArgumentCaptor.forClass(ListMemoryRecordsRequest.class);
    verify(client).listMemoryRecords(captor.capture());
    assertThat(captor.getValue().memoryId()).isEqualTo("test-memory-id-abc123");
    assertThat(captor.getValue().namespace()).isEqualTo("customer/12345");
    assertThat(result.resultCount()).isEqualTo(1);
    assertThat(result.records().getFirst().content()).isEqualTo("A stored fact");
  }

  @Test
  void list_shouldPassStrategyIdAndMaxResults() {
    when(client.listMemoryRecords(any(ListMemoryRecordsRequest.class)))
        .thenReturn(ListMemoryRecordsResponse.builder().memoryRecordSummaries(List.of()).build());

    var request = buildRequest(new ListOperation("user_preferences", 50));
    executor.execute(request);

    var captor = ArgumentCaptor.forClass(ListMemoryRecordsRequest.class);
    verify(client).listMemoryRecords(captor.capture());
    assertThat(captor.getValue().memoryStrategyId()).isEqualTo("user_preferences");
    assertThat(captor.getValue().maxResults()).isEqualTo(50);
  }

  @Test
  void list_shouldFilterOutEmptyContent() {
    var withContent = buildSummary("mem-ok", "Valid content", null);
    var nullContent =
        MemoryRecordSummary.builder()
            .memoryRecordId("mem-empty")
            .memoryStrategyId("default")
            .namespaces("ns")
            .createdAt(Instant.now())
            .content(MemoryContent.fromText(null))
            .build();

    when(client.listMemoryRecords(any(ListMemoryRecordsRequest.class)))
        .thenReturn(
            ListMemoryRecordsResponse.builder()
                .memoryRecordSummaries(withContent, nullContent)
                .build());

    var result = executor.execute(buildRequest(new ListOperation(null, null)));
    assertThat(result.resultCount()).isEqualTo(1);
  }

  @Test
  void retrieve_shouldIncludeNextToken() {
    when(client.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenReturn(
            RetrieveMemoryRecordsResponse.builder()
                .memoryRecordSummaries(buildSummary("mem-1", "content", 0.9))
                .nextToken("page2token")
                .build());

    var result = executor.execute(buildRequest(new RetrieveOperation("q", null, null)));
    assertThat(result.nextToken()).isEqualTo("page2token");
  }

  @Test
  void retrieve_shouldWrapBedrockException() {
    when(client.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenThrow(BedrockAgentCoreException.builder().message("Access denied").build());

    assertThatThrownBy(() -> executor.execute(buildRequest(new RetrieveOperation("q", null, null))))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("AgentCore Memory error");
  }

  @Test
  void list_shouldWrapBedrockException() {
    when(client.listMemoryRecords(any(ListMemoryRecordsRequest.class)))
        .thenThrow(BedrockAgentCoreException.builder().message("Not found").build());

    assertThatThrownBy(() -> executor.execute(buildRequest(new ListOperation(null, null))))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("AgentCore Memory error");
  }

  // --- helpers ---

  private AgentCoreMemoryRequest buildRequest(MemoryOperation operation) {
    var request = new AgentCoreMemoryRequest();
    request.setMemoryId("test-memory-id-abc123");
    request.setNamespace("customer/12345");
    request.setOperation(operation);
    return request;
  }

  private MemoryRecordSummary buildSummary(String id, String text, Double score) {
    var builder =
        MemoryRecordSummary.builder()
            .memoryRecordId(id)
            .content(MemoryContent.fromText(text))
            .memoryStrategyId("default")
            .namespaces("customer/12345")
            .createdAt(Instant.parse("2026-04-01T10:00:00Z"));
    if (score != null) {
      builder.score(score);
    }
    return builder.build();
  }
}
