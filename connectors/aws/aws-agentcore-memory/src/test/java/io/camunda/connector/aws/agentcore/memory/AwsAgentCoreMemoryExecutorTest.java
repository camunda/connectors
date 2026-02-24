/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.agentcore.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.aws.agentcore.memory.model.request.AwsAgentCoreMemoryRequest;
import io.camunda.connector.aws.agentcore.memory.model.request.FilterOperator;
import io.camunda.connector.aws.agentcore.memory.model.request.MetadataFilter;
import io.camunda.connector.aws.agentcore.memory.model.request.RetrieveMemoryRecordsOperation;
import io.camunda.connector.aws.agentcore.memory.model.response.AwsAgentCoreMemoryResponse;
import io.camunda.connector.aws.agentcore.memory.model.response.MemoryRetrievalResult;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.*;

@ExtendWith(MockitoExtension.class)
class AwsAgentCoreMemoryExecutorTest {

  @Mock private BedrockAgentCoreClient client;
  @Mock private Document document;
  @Mock private DocumentReference documentReference;

  private ObjectMapper objectMapper;
  private Function<DocumentCreationRequest, Document> documentFactory;
  private AwsAgentCoreMemoryExecutor executor;

  @BeforeEach
  void setUp() {
    objectMapper = ConnectorsObjectMapperSupplier.getCopy();
    lenient().when(document.reference()).thenReturn(documentReference);
    documentFactory = req -> document;
    executor = new AwsAgentCoreMemoryExecutor(client, objectMapper);
  }

  @Test
  void shouldBuildCorrectSdkRequest() {
    // given
    var request = buildRequest("test-memory", "/ns/test", "find preferences", 10);
    var op =
        new RetrieveMemoryRecordsOperation(
            "/ns/test", "find preferences", "strategy-1", 5, null, null);
    request.setOperation(op);

    when(client.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenReturn(emptyResponse());

    // when
    executor.execute(request, documentFactory);

    // then
    var captor = ArgumentCaptor.forClass(RetrieveMemoryRecordsRequest.class);
    verify(client).retrieveMemoryRecords(captor.capture());
    var sdkRequest = captor.getValue();
    assertThat(sdkRequest.memoryId()).isEqualTo("test-memory");
    assertThat(sdkRequest.namespace()).isEqualTo("/ns/test");
    assertThat(sdkRequest.searchCriteria().searchQuery()).isEqualTo("find preferences");
    assertThat(sdkRequest.searchCriteria().memoryStrategyId()).isEqualTo("strategy-1");
    assertThat(sdkRequest.searchCriteria().topK()).isEqualTo(5);
    assertThat(sdkRequest.maxResults()).isEqualTo(10);
  }

  @Test
  void shouldBuildSdkRequestWithMetadataFilters() {
    // given
    var filters = List.of(new MetadataFilter("category", FilterOperator.EQUALS_TO, "prefs"));
    var op =
        new RetrieveMemoryRecordsOperation("/ns/test", "search query", null, null, filters, null);
    var request = buildRequest("mem-1", "/ns/test", "search query", null);
    request.setOperation(op);

    when(client.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenReturn(emptyResponse());

    // when
    executor.execute(request, documentFactory);

    // then
    var captor = ArgumentCaptor.forClass(RetrieveMemoryRecordsRequest.class);
    verify(client).retrieveMemoryRecords(captor.capture());
    var sdkRequest = captor.getValue();
    assertThat(sdkRequest.searchCriteria().metadataFilters()).hasSize(1);
  }

  @Test
  void shouldMapSdkResponseCorrectly() throws Exception {
    // given
    var now = Instant.now();
    var sdkResponse =
        RetrieveMemoryRecordsResponse.builder()
            .memoryRecordSummaries(
                MemoryRecordSummary.builder()
                    .memoryRecordId("rec-1")
                    .content(MemoryContent.builder().text("User likes dark mode").build())
                    .memoryStrategyId("preferences")
                    .namespaces("/actors/user-1")
                    .createdAt(now)
                    .score(0.95)
                    .metadata(Map.of("category", MetadataValue.fromStringValue("ui-preferences")))
                    .build())
            .nextToken("token-2")
            .build();

    when(client.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenReturn(sdkResponse);

    var request = buildSimpleRequest();

    // when
    MemoryRetrievalResult result = executor.execute(request, documentFactory);

    // then
    assertThat(result.recordCount()).isEqualTo(1);
    assertThat(result.nextToken()).isEqualTo("token-2");
    assertThat(result.memoryDocument()).isEqualTo(document);

    // verify the document content
    var docRequestCaptor = ArgumentCaptor.forClass(DocumentCreationRequest.class);
    var factoryMock = mock(Function.class);
    when(factoryMock.apply(any())).thenReturn(document);
    var executor2 = new AwsAgentCoreMemoryExecutor(client, objectMapper);
    executor2.execute(request, (Function<DocumentCreationRequest, Document>) factoryMock);
    verify(factoryMock).apply(docRequestCaptor.capture());
    var docRequest = docRequestCaptor.getValue();
    assertThat(docRequest.contentType()).isEqualTo("application/json");
    assertThat(docRequest.fileName()).startsWith("agentcore-memory-");
    assertThat(docRequest.fileName()).endsWith(".json");
  }

  @Test
  void shouldFlattenContentFromText() throws Exception {
    // given
    var sdkResponse =
        RetrieveMemoryRecordsResponse.builder()
            .memoryRecordSummaries(
                MemoryRecordSummary.builder()
                    .memoryRecordId("rec-1")
                    .content(MemoryContent.builder().text("Flattened text content").build())
                    .memoryStrategyId("summary")
                    .createdAt(Instant.now())
                    .score(0.8)
                    .build())
            .build();

    when(client.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenReturn(sdkResponse);

    ArgumentCaptor<DocumentCreationRequest> captor =
        ArgumentCaptor.forClass(DocumentCreationRequest.class);
    Function<DocumentCreationRequest, Document> spyFactory =
        req -> {
          // Verify JSON content
          try {
            var response = objectMapper.readValue(req.content(), AwsAgentCoreMemoryResponse.class);
            assertThat(response.memoryRecords()).hasSize(1);
            assertThat(response.memoryRecords().get(0).content())
                .isEqualTo("Flattened text content");
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          return document;
        };

    var request = buildSimpleRequest();

    // when
    executor.execute(request, spyFactory);
  }

  @Test
  void shouldFlattenMetadata() throws Exception {
    // given
    var sdkResponse =
        RetrieveMemoryRecordsResponse.builder()
            .memoryRecordSummaries(
                MemoryRecordSummary.builder()
                    .memoryRecordId("rec-1")
                    .content(MemoryContent.builder().text("test").build())
                    .memoryStrategyId("strategy")
                    .createdAt(Instant.now())
                    .score(0.9)
                    .metadata(
                        Map.of(
                            "key1", MetadataValue.fromStringValue("val1"),
                            "key2", MetadataValue.fromStringValue("val2")))
                    .build())
            .build();

    when(client.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenReturn(sdkResponse);

    Function<DocumentCreationRequest, Document> verifyingFactory =
        req -> {
          try {
            var response = objectMapper.readValue(req.content(), AwsAgentCoreMemoryResponse.class);
            var metadata = response.memoryRecords().get(0).metadata();
            assertThat(metadata).containsEntry("key1", "val1").containsEntry("key2", "val2");
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          return document;
        };

    var request = buildSimpleRequest();

    // when
    executor.execute(request, verifyingFactory);
  }

  @Test
  void shouldReturnEmptyDocumentWhenNoRecords() {
    // given
    when(client.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenReturn(emptyResponse());

    var request = buildSimpleRequest();

    // when
    MemoryRetrievalResult result = executor.execute(request, documentFactory);

    // then
    assertThat(result.recordCount()).isZero();
    assertThat(result.nextToken()).isNull();
  }

  @Test
  void shouldPropagateNextToken() {
    // given
    var op =
        new RetrieveMemoryRecordsOperation("/ns/test", "query", null, null, null, "previous-token");
    var request = buildSimpleRequest();
    request.setOperation(op);

    when(client.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenReturn(emptyResponse());

    // when
    executor.execute(request, documentFactory);

    // then
    var captor = ArgumentCaptor.forClass(RetrieveMemoryRecordsRequest.class);
    verify(client).retrieveMemoryRecords(captor.capture());
    assertThat(captor.getValue().nextToken()).isEqualTo("previous-token");
  }

  @Test
  void shouldWrapThrottledExceptionAsRetryException() {
    // given
    when(client.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenThrow(ThrottledException.builder().message("Rate exceeded").build());

    var request = buildSimpleRequest();

    // when/then
    assertThatThrownBy(() -> executor.execute(request, documentFactory))
        .isInstanceOf(ConnectorRetryException.class)
        .hasMessageContaining("throttled");
  }

  @Test
  void shouldWrapServiceQuotaExceeded() {
    // given
    when(client.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenThrow(ServiceQuotaExceededException.builder().message("Quota exceeded").build());

    var request = buildSimpleRequest();

    // when/then
    assertThatThrownBy(() -> executor.execute(request, documentFactory))
        .isInstanceOf(ConnectorException.class)
        .hasFieldOrPropertyWithValue("errorCode", "QUOTA_EXCEEDED");
  }

  @Test
  void shouldWrapGenericBedrockAgentCoreException() {
    // given
    when(client.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenThrow(BedrockAgentCoreException.builder().message("Something went wrong").build());

    var request = buildSimpleRequest();

    // when/then
    assertThatThrownBy(() -> executor.execute(request, documentFactory))
        .isInstanceOf(ConnectorException.class)
        .hasFieldOrPropertyWithValue("errorCode", "AWS_ERROR");
  }

  @Test
  void shouldCreateDocumentWithCorrectContentType() {
    // given
    var sdkResponse =
        RetrieveMemoryRecordsResponse.builder()
            .memoryRecordSummaries(
                MemoryRecordSummary.builder()
                    .memoryRecordId("rec-1")
                    .content(MemoryContent.builder().text("test content").build())
                    .memoryStrategyId("strategy")
                    .createdAt(Instant.now())
                    .score(0.9)
                    .build())
            .build();

    when(client.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenReturn(sdkResponse);

    ArgumentCaptor<DocumentCreationRequest> captor =
        ArgumentCaptor.forClass(DocumentCreationRequest.class);
    @SuppressWarnings("unchecked")
    Function<DocumentCreationRequest, Document> factoryMock = mock(Function.class);
    when(factoryMock.apply(any())).thenReturn(document);

    var request = buildSimpleRequest();

    // when
    executor.execute(request, factoryMock);

    // then
    verify(factoryMock).apply(captor.capture());
    assertThat(captor.getValue().contentType()).isEqualTo("application/json");
  }

  @Test
  void shouldHandleNullContentGracefully() throws Exception {
    // given
    var sdkResponse =
        RetrieveMemoryRecordsResponse.builder()
            .memoryRecordSummaries(
                MemoryRecordSummary.builder()
                    .memoryRecordId("rec-1")
                    .memoryStrategyId("strategy")
                    .createdAt(Instant.now())
                    .score(0.5)
                    .build())
            .build();

    when(client.retrieveMemoryRecords(any(RetrieveMemoryRecordsRequest.class)))
        .thenReturn(sdkResponse);

    Function<DocumentCreationRequest, Document> verifyingFactory =
        req -> {
          try {
            var response = objectMapper.readValue(req.content(), AwsAgentCoreMemoryResponse.class);
            assertThat(response.memoryRecords().get(0).content()).isNull();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          return document;
        };

    var request = buildSimpleRequest();

    // when / then — no exception
    executor.execute(request, verifyingFactory);
  }

  // --- Helpers ---

  private AwsAgentCoreMemoryRequest buildSimpleRequest() {
    return buildRequest("mem-1", "/ns/test", "test query", null);
  }

  private AwsAgentCoreMemoryRequest buildRequest(
      String memoryId, String namespace, String searchQuery, Integer maxResults) {
    var request = new AwsAgentCoreMemoryRequest();
    request.setMemoryId(memoryId);
    request.setMaxResults(maxResults);
    request.setOperation(
        new RetrieveMemoryRecordsOperation(namespace, searchQuery, null, null, null, null));
    return request;
  }

  private RetrieveMemoryRecordsResponse emptyResponse() {
    return RetrieveMemoryRecordsResponse.builder().memoryRecordSummaries(List.of()).build();
  }
}
