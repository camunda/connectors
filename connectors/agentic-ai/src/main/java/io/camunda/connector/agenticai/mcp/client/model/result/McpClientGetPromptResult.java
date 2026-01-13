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
    implements McpClientResult, McpClientResultWithStorableData {

  @Override
  public McpClientGetPromptResult transformStorableMcpResultData(
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
    if (!(message instanceof StorableMcpDataContainer storableMcpDataContainer)) {
      return message;
    }

    return (PromptMessage)
        storableMcpDataContainer.replaceWithDocumentReference(documentFactory, documentSettings);
  }

  private static Document createDocument(
      DocumentFactory factory, McpDocumentSettings documentSettings, byte[] data, String mimeType) {
    return factory.create(
        DocumentCreationRequest.from(data)
            .timeToLive(documentSettings.timeToLive())
            .contentType(mimeType)
            .build());
  }

  /**
   * General interface for messages that are part of a retrieved prompt.
   *
   * <p>Those can contain text or other content, like binaries, or references to Camunda documents.
   */
  public sealed interface PromptMessage {

    String role();
  }

  /** Text messages hold plain textual content. */
  public record TextMessage(String role, String text) implements PromptMessage {}

  /**
   * Blob messages hold arbitrary binary data which is supposed to be stored as a Camunda document.
   */
  public record BlobMessage(String role, byte[] data, String mimeType)
      implements PromptMessage, StorableMcpDataContainer<PromptMessage> {

    @Override
    public PromptMessage replaceWithDocumentReference(
        DocumentFactory documentFactory, McpDocumentSettings documentSettings) {
      var document = createDocument(documentFactory, documentSettings, data, mimeType);

      return new CamundaDocumentReferenceMessage(role, document);
    }
  }

  /**
   * Embedded resource messages can either contain textual or binary content. Only embedded binary
   * resources are stored in the Camunda document store.
   */
  public record EmbeddedResourceMessage(String role, EmbeddedResource resource)
      implements PromptMessage, StorableMcpDataContainer<PromptMessage> {

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
          role, new EmbeddedResource.CamundaDocumentReference(blobResource.uri, document));
    }

    public sealed interface EmbeddedResource {

      record BlobResource(String uri, byte[] blob, String mimeType) implements EmbeddedResource {}

      record TextResource(String uri, String text, String mimeType) implements EmbeddedResource {}

      record CamundaDocumentReference(String uri, Document data) implements EmbeddedResource {}
    }
  }

  /**
   * Message holding a reference to a Camunda document. This record is a result of transforming
   * prompt messages of type {@link BlobMessage}>
   */
  public record CamundaDocumentReferenceMessage(String role, Document data)
      implements PromptMessage {}
}
