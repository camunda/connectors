/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.result;

import io.camunda.connector.agenticai.mcp.client.model.McpDocumentSettings;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import java.util.List;

public record McpClientGetPromptResult(String description, List<PromptMessage> messages)
    implements McpClientResult, McpClientResultWithBinaries {

  @Override
  public McpClientGetPromptResult transformBinaryContentToDocuments(
      DocumentFactory documentFactory, McpDocumentSettings documentSettings) {
    var messagesWithDocumentReferences =
        messages.stream()
            .map(
                promptMessage ->
                    replaceWithDocumentReferenceIfNeeded(
                        documentFactory, documentSettings, promptMessage))
            .toList();

    return new McpClientGetPromptResult(this.description, messagesWithDocumentReferences);
  }

  private PromptMessage replaceWithDocumentReferenceIfNeeded(
      DocumentFactory documentFactory,
      McpDocumentSettings documentSettings,
      PromptMessage message) {
    if (!(message instanceof DocumentHoldingMessage documentHoldingMessage)) {
      return message;
    }

    return documentHoldingMessage.replaceWithDocumentReference(documentFactory, documentSettings);
  }

  private static Document createDocument(
      DocumentFactory factory, McpDocumentSettings documentSettings, byte[] data, String mimeType) {
    return factory.create(
        DocumentCreationRequest.from(data)
            .timeToLive(documentSettings.timeToLive())
            .contentType(mimeType)
            .build());
  }

  public sealed interface PromptMessage {

    String role();
  }

  public sealed interface DocumentHoldingMessage extends PromptMessage {

    PromptMessage replaceWithDocumentReference(
        DocumentFactory documentFactory, McpDocumentSettings documentSettings);
  }

  public record TextMessage(String role, String text) implements PromptMessage {}

  public record BinaryMessage(String role, byte[] data, String mimeType)
      implements DocumentHoldingMessage {

    @Override
    public PromptMessage replaceWithDocumentReference(
        DocumentFactory documentFactory, McpDocumentSettings documentSettings) {
      var document = createDocument(documentFactory, documentSettings, data, mimeType);

      return new CamundaDocumentReferenceMessage(role, document);
    }
  }

  public record EmbeddedResourceMessage(String role, EmbeddedResource resource)
      implements DocumentHoldingMessage {

    @Override
    public PromptMessage replaceWithDocumentReference(
        DocumentFactory documentFactory, McpDocumentSettings documentSettings) {
      if (!(resource instanceof EmbeddedResource.BlobResource blobResource)) {
        return this;
      }

      var document =
          createDocument(
              documentFactory, documentSettings, blobResource.blob(), blobResource.mimeType());

      return new EmbeddedResourceMessage(
          role, new EmbeddedResource.CamundaDocumentReference(document));
    }

    public sealed interface EmbeddedResource {

      record BlobResource(String uri, byte[] blob, String mimeType) implements EmbeddedResource {}

      record TextResource(String uri, String text, String mimeType) implements EmbeddedResource {}

      record CamundaDocumentReference(Document data) implements EmbeddedResource {}
    }
  }

  public record CamundaDocumentReferenceMessage(String role, Document data)
      implements PromptMessage {}
}
