/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.fixture;

import io.camunda.connector.model.EmbeddingsVectorDBRequest;
import io.camunda.connector.model.operation.EmbedDocumentOperation;
import io.camunda.connector.model.operation.EmbedDocumentSource;
import io.camunda.connector.model.operation.RetrieveDocumentOperation;
import java.util.List;

public class EmbeddingsVectorDBRequestFixture {

  private static final String CONVERSATION =
"""
[{"user":"operator123","message":"Hey how are you?"},{"user":"customer9000","message":"Yes I am fine, just busy with RAG connector"}]
""";

  public static EmbeddingsVectorDBRequest createDefaultRetrieve() {
    final var operation = new RetrieveDocumentOperation("What is RAG?", 5, 0.6);
    final var embeddingModeProvider =
        EmbeddingModelProviderFixture.createDefaultBedrockEmbeddingModel();
    final var vectorStore = EmbeddingsVectorStoreFixture.createElasticsearchVectorStore();
    final var request =
        new EmbeddingsVectorDBRequest(operation, embeddingModeProvider, vectorStore);
    return request;
  }

  public static EmbeddingsVectorDBRequest createDefaultEmbedOperation() {
    final var operation =
        new EmbedDocumentOperation(
            EmbedDocumentSource.CamundaDocument,
            null,
            List.of(CamundaDocumentFixture.inMemoryTxtDocument()),
            DocumentSplitterFixture.noopDocumentSplitter());
    final var embeddingModeProvider =
        EmbeddingModelProviderFixture.createDefaultBedrockEmbeddingModel();
    final var vectorStore = EmbeddingsVectorStoreFixture.createAmazonManagedOpenVectorStore();
    final var request =
        new EmbeddingsVectorDBRequest(operation, embeddingModeProvider, vectorStore);
    return request;
  }

  public static EmbeddingsVectorDBRequest createEmbedOperationWithPdfFile() {
    final var operation =
        new EmbedDocumentOperation(
            EmbedDocumentSource.CamundaDocument,
            null,
            List.of(CamundaDocumentFixture.inMemoryPdfDocument()),
            DocumentSplitterFixture.noopDocumentSplitter());
    final var embeddingModeProvider =
        EmbeddingModelProviderFixture.createDefaultBedrockEmbeddingModel();
    final var vectorStore = EmbeddingsVectorStoreFixture.createAmazonManagedOpenVectorStore();
    final var request =
        new EmbeddingsVectorDBRequest(operation, embeddingModeProvider, vectorStore);
    return request;
  }

  /**
   * What element templates from version 4 on produce: no source discriminator, documents supplied
   * through the unified document source dropdown (Camunda / inline / URL all deserialize to {@code
   * Document}).
   */
  public static EmbeddingsVectorDBRequest createEmbedOperationWithUnifiedDocumentInput() {
    final var operation =
        new EmbedDocumentOperation(
            null,
            null,
            List.of(CamundaDocumentFixture.inMemoryTxtDocument()),
            DocumentSplitterFixture.noopDocumentSplitter());
    return new EmbeddingsVectorDBRequest(
        operation,
        EmbeddingModelProviderFixture.createDefaultBedrockEmbeddingModel(),
        EmbeddingsVectorStoreFixture.createAmazonManagedOpenVectorStore());
  }

  /** Neither source populated — the connector has nothing to embed. */
  public static EmbeddingsVectorDBRequest createEmbedOperationWithoutInput() {
    final var operation =
        new EmbedDocumentOperation(
            null, null, List.of(), DocumentSplitterFixture.noopDocumentSplitter());
    return new EmbeddingsVectorDBRequest(
        operation,
        EmbeddingModelProviderFixture.createDefaultBedrockEmbeddingModel(),
        EmbeddingsVectorStoreFixture.createAmazonManagedOpenVectorStore());
  }

  /** Plain text without the legacy discriminator, as the hidden runtime fallback receives it. */
  public static EmbeddingsVectorDBRequest createEmbedOperationWithPlainTextOnly() {
    final var operation =
        new EmbedDocumentOperation(
            null, CONVERSATION, List.of(), DocumentSplitterFixture.noopDocumentSplitter());
    return new EmbeddingsVectorDBRequest(
        operation,
        EmbeddingModelProviderFixture.createDefaultBedrockEmbeddingModel(),
        EmbeddingsVectorStoreFixture.createAmazonManagedOpenVectorStore());
  }

  public static EmbeddingsVectorDBRequest createEmbedOperationWithPlainText() {
    final var operation =
        new EmbedDocumentOperation(
            EmbedDocumentSource.PlainText,
            CONVERSATION,
            List.of(CamundaDocumentFixture.inMemoryPdfDocument()),
            DocumentSplitterFixture.noopDocumentSplitter());
    final var embeddingModeProvider =
        EmbeddingModelProviderFixture.createDefaultBedrockEmbeddingModel();
    final var vectorStore = EmbeddingsVectorStoreFixture.createAmazonManagedOpenVectorStore();
    final var request =
        new EmbeddingsVectorDBRequest(operation, embeddingModeProvider, vectorStore);
    return request;
  }
}
