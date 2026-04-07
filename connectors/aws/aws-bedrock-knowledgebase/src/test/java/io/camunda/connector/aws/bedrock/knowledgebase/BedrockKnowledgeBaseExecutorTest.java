/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.knowledgebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.aws.bedrock.knowledgebase.model.request.BedrockKnowledgeBaseRequest;
import io.camunda.connector.aws.bedrock.knowledgebase.model.request.RetrieveOperation;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultContent;
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
  void shouldRetrieveResultsWithContentScoreAndDocumentReference() {
    when(mockDocument.reference()).thenReturn(mockReference);
    when(documentFactory.create(any(DocumentCreationRequest.class))).thenReturn(mockDocument);

    var sdkResponse =
        RetrieveResponse.builder()
            .retrievalResults(
                KnowledgeBaseRetrievalResult.builder()
                    .content(
                        RetrievalResultContent.builder().text("Policy covers fire damage").build())
                    .score(0.95)
                    .build(),
                KnowledgeBaseRetrievalResult.builder()
                    .content(RetrievalResultContent.builder().text("Flood damage excluded").build())
                    .score(0.82)
                    .build())
            .nextToken("next-page-token")
            .build();
    when(client.retrieve(any(RetrieveRequest.class))).thenReturn(sdkResponse);

    var request = buildRequest(ActualValue.KNOWLEDGE_BASE_ID, ActualValue.QUERY, 5);
    var result = executor.execute(request, documentFactory);

    assertThat(result).isNotNull();
    assertThat(result.resultCount()).isEqualTo(2);
    assertThat(result.results()).hasSize(2);
    assertThat(result.results().get(0).content()).isEqualTo("Policy covers fire damage");
    assertThat(result.results().get(0).score()).isEqualTo(0.95);
    assertThat(result.results().get(0).documentReference()).isEqualTo(mockReference);
    assertThat(result.results().get(1).content()).isEqualTo("Flood damage excluded");
    assertThat(result.paginationToken()).isEqualTo("next-page-token");
  }

  @Test
  void shouldHandleEmptyResults() {
    var sdkResponse = RetrieveResponse.builder().retrievalResults(List.of()).build();
    when(client.retrieve(any(RetrieveRequest.class))).thenReturn(sdkResponse);

    var request = buildRequest(ActualValue.KNOWLEDGE_BASE_ID, ActualValue.QUERY, null);
    var result = executor.execute(request, documentFactory);

    assertThat(result).isNotNull();
    assertThat(result.resultCount()).isZero();
    assertThat(result.results()).isEmpty();
    assertThat(result.paginationToken()).isNull();
  }

  private BedrockKnowledgeBaseRequest buildRequest(
      String kbId, String query, Integer numberOfResults) {
    var request = new BedrockKnowledgeBaseRequest();
    request.setKnowledgeBaseId(kbId);
    request.setOperation(new RetrieveOperation(query, numberOfResults));
    return request;
  }
}
