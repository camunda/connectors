/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.fixture;

import io.camunda.connector.model.EmbeddingsVectorDBRequest;
import io.camunda.connector.model.operation.EmbedDocumentOperation;
import io.camunda.connector.model.operation.RetrieveDocumentOperation;
import java.util.List;

public class EmbeddingsVectorDBRequestFixture {

  public static EmbeddingsVectorDBRequest createDefaultRetrieve() {
    final var operation = new RetrieveDocumentOperation("What is RAG?", 5, 0.6);
    final var embeddingModeProvider =
        EmbeddingModelProviderFixture.createDefaultOllamaEmbeddingModel();
    final var vectorStore = EmbeddingsVectorStoreFixture.createElasticSearchVectorStore();
    final var request =
        new EmbeddingsVectorDBRequest(operation, embeddingModeProvider, vectorStore);
    return request;
  }

  public static EmbeddingsVectorDBRequest createDefaultEmbedOperation() {
    final var operation =
        new EmbedDocumentOperation(
            List.of(CamundaDocumentFixture.inMemoryDocument()),
            DocumentSplitterFixture.noopDocumentSplitter());
    final var embeddingModeProvider =
        EmbeddingModelProviderFixture.createDefaultBedrockEmbeddingModel();
    final var vectorStore = EmbeddingsVectorStoreFixture.createAmazonManagedOpenVectorStore();
    final var request =
        new EmbeddingsVectorDBRequest(operation, embeddingModeProvider, vectorStore);
    return request;
  }
}
