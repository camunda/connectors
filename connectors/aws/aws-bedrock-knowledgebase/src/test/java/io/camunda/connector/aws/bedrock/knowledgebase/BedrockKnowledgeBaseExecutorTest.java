/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.knowledgebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.aws.bedrock.knowledgebase.model.request.BedrockKnowledgeBaseRequest;
import io.camunda.connector.aws.bedrock.knowledgebase.model.request.RetrieveOperation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultContent;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultLocation;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultS3Location;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;

@ExtendWith(MockitoExtension.class)
class BedrockKnowledgeBaseExecutorTest extends BaseTest {

  @Mock private BedrockAgentRuntimeClient client;
  @Mock private DocumentFactory documentFactory;
  @Mock private Document mockDocument;
  @Mock private DocumentReference mockReference;

  private BedrockKnowledgeBaseExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new BedrockKnowledgeBaseExecutor(client);
  }

  @Test
  void shouldPassKnowledgeBaseIdAndNumberOfResults() {
    when(mockDocument.reference()).thenReturn(mockReference);
    when(documentFactory.create(any(DocumentCreationRequest.class))).thenReturn(mockDocument);
    when(client.retrieve(any(RetrieveRequest.class)))
        .thenReturn(
            RetrieveResponse.builder()
                .retrievalResults(
                    KnowledgeBaseRetrievalResult.builder()
                        .content(RetrievalResultContent.builder().text("test").build())
                        .build())
                .build());

    var request = buildRequest(ActualValue.KNOWLEDGE_BASE_ID, ActualValue.QUERY, 10);
    executor.execute(request, documentFactory);

    var captor = ArgumentCaptor.forClass(RetrieveRequest.class);
    verify(client).retrieve(captor.capture());
    assertThat(captor.getValue().knowledgeBaseId()).isEqualTo(ActualValue.KNOWLEDGE_BASE_ID);
    assertThat(
            captor
                .getValue()
                .retrievalConfiguration()
                .vectorSearchConfiguration()
                .numberOfResults())
        .isEqualTo(10);
  }

  @Test
  void shouldReturnContentScoreAndPaginationToken() {
    when(mockDocument.reference()).thenReturn(mockReference);
    when(documentFactory.create(any(DocumentCreationRequest.class))).thenReturn(mockDocument);
    when(client.retrieve(any(RetrieveRequest.class)))
        .thenReturn(
            RetrieveResponse.builder()
                .retrievalResults(
                    KnowledgeBaseRetrievalResult.builder()
                        .content(
                            RetrievalResultContent.builder()
                                .text("Policy covers fire damage")
                                .build())
                        .score(0.95)
                        .build(),
                    KnowledgeBaseRetrievalResult.builder()
                        .content(
                            RetrievalResultContent.builder().text("Flood damage excluded").build())
                        .score(0.82)
                        .build())
                .nextToken("next-page-token")
                .build());

    var result = executor.execute(buildRequest("KB1", "query", 5), documentFactory);

    assertThat(result.resultCount()).isEqualTo(2);
    assertThat(result.results()).hasSize(2);
    assertThat(result.results().get(0).content()).isEqualTo("Policy covers fire damage");
    assertThat(result.results().get(0).score()).isEqualTo(0.95);
    assertThat(result.results().get(0).documentReference()).isEqualTo(mockReference);
    assertThat(result.results().get(1).content()).isEqualTo("Flood damage excluded");
    assertThat(result.paginationToken()).isEqualTo("next-page-token");
  }

  @Test
  void shouldMapS3LocationWhenPresent() {
    when(mockDocument.reference()).thenReturn(mockReference);
    when(documentFactory.create(any(DocumentCreationRequest.class))).thenReturn(mockDocument);
    when(client.retrieve(any(RetrieveRequest.class)))
        .thenReturn(
            RetrieveResponse.builder()
                .retrievalResults(
                    KnowledgeBaseRetrievalResult.builder()
                        .content(RetrievalResultContent.builder().text("content").build())
                        .location(
                            RetrievalResultLocation.builder()
                                .s3Location(
                                    RetrievalResultS3Location.builder()
                                        .uri("s3://bucket/file.pdf")
                                        .build())
                                .build())
                        .build())
                .build());

    var result = executor.execute(buildRequest("KB1", "query", null), documentFactory);

    assertThat(result.results().get(0).sourceUri()).isEqualTo("s3://bucket/file.pdf");
  }

  @Test
  void shouldReturnNullSourceUriWhenNoLocation() {
    when(mockDocument.reference()).thenReturn(mockReference);
    when(documentFactory.create(any(DocumentCreationRequest.class))).thenReturn(mockDocument);
    when(client.retrieve(any(RetrieveRequest.class)))
        .thenReturn(
            RetrieveResponse.builder()
                .retrievalResults(
                    KnowledgeBaseRetrievalResult.builder()
                        .content(RetrievalResultContent.builder().text("content").build())
                        .build())
                .build());

    var result = executor.execute(buildRequest("KB1", "query", null), documentFactory);

    assertThat(result.results().get(0).sourceUri()).isNull();
  }

  @Test
  void shouldMapMetadataWhenPresent() {
    when(mockDocument.reference()).thenReturn(mockReference);
    when(documentFactory.create(any(DocumentCreationRequest.class))).thenReturn(mockDocument);
    when(client.retrieve(any(RetrieveRequest.class)))
        .thenReturn(
            RetrieveResponse.builder()
                .retrievalResults(
                    KnowledgeBaseRetrievalResult.builder()
                        .content(RetrievalResultContent.builder().text("content").build())
                        .metadata(
                            Map.of(
                                "category",
                                software.amazon.awssdk.core.document.Document.fromString("auto")))
                        .build())
                .build());

    var result = executor.execute(buildRequest("KB1", "query", null), documentFactory);

    assertThat(result.results().get(0).metadata()).containsEntry("category", "auto");
  }

  @Test
  void shouldFilterOutChunksWithoutContent() {
    when(mockDocument.reference()).thenReturn(mockReference);
    when(documentFactory.create(any(DocumentCreationRequest.class))).thenReturn(mockDocument);
    when(client.retrieve(any(RetrieveRequest.class)))
        .thenReturn(
            RetrieveResponse.builder()
                .retrievalResults(
                    KnowledgeBaseRetrievalResult.builder()
                        .content(RetrievalResultContent.builder().text("valid content").build())
                        .build(),
                    KnowledgeBaseRetrievalResult.builder().build())
                .build());

    var result = executor.execute(buildRequest("KB1", "query", null), documentFactory);

    assertThat(result.resultCount()).isEqualTo(1);
    assertThat(result.results().get(0).content()).isEqualTo("valid content");
  }

  @Test
  void shouldHandleEmptyResults() {
    when(client.retrieve(any(RetrieveRequest.class)))
        .thenReturn(RetrieveResponse.builder().retrievalResults(List.of()).build());

    var result = executor.execute(buildRequest("KB1", "query", null), documentFactory);

    assertThat(result.resultCount()).isZero();
    assertThat(result.results()).isEmpty();
    assertThat(result.paginationToken()).isNull();
  }

  @Test
  void shouldCreateDocumentForEveryChunk() throws Exception {
    when(mockDocument.reference()).thenReturn(mockReference);
    when(documentFactory.create(any(DocumentCreationRequest.class))).thenReturn(mockDocument);
    when(client.retrieve(any(RetrieveRequest.class)))
        .thenReturn(
            RetrieveResponse.builder()
                .retrievalResults(
                    KnowledgeBaseRetrievalResult.builder()
                        .content(RetrievalResultContent.builder().text("chunk 1").build())
                        .build(),
                    KnowledgeBaseRetrievalResult.builder()
                        .content(RetrievalResultContent.builder().text("chunk 2").build())
                        .build(),
                    KnowledgeBaseRetrievalResult.builder()
                        .content(RetrievalResultContent.builder().text("chunk 3").build())
                        .build())
                .build());

    var result = executor.execute(buildRequest("KB1", "query", null), documentFactory);

    assertThat(result.resultCount()).isEqualTo(3);
    assertThat(result.results()).hasSize(3);
    var captor = ArgumentCaptor.forClass(DocumentCreationRequest.class);
    verify(documentFactory, times(3)).create(captor.capture());
    var requests = captor.getAllValues();
    assertThat(new String(requests.get(0).content().readAllBytes())).isEqualTo("chunk 1");
    assertThat(new String(requests.get(1).content().readAllBytes())).isEqualTo("chunk 2");
    assertThat(new String(requests.get(2).content().readAllBytes())).isEqualTo("chunk 3");
  }

  private BedrockKnowledgeBaseRequest buildRequest(
      String kbId, String query, Integer numberOfResults) {
    var request = new BedrockKnowledgeBaseRequest();
    request.setKnowledgeBaseId(kbId);
    request.setOperation(new RetrieveOperation(query, numberOfResults));
    return request;
  }
}
