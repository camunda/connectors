/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.result;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import java.util.List;

public record McpClientGetPromptResult(String description, List<PromptMessage> messages)
    implements McpClientResult, McpClientResultWithStorableData {

  @Override
  public McpClientGetPromptResult convertStorableMcpResultData(DocumentFactory documentFactory) {
    var messagesWithDocumentReferences =
        messages.stream()
            .map(
                promptMessage ->
                    replaceContentWithDocumentReferenceIfNeeded(documentFactory, promptMessage))
            .toList();

    return new McpClientGetPromptResult(this.description, messagesWithDocumentReferences);
  }

  private PromptMessage replaceContentWithDocumentReferenceIfNeeded(
      DocumentFactory documentFactory, PromptMessage message) {
    var content = message.content();

    if (!(content instanceof StorableMcpDataContainer storableMcpDataContainer)) {
      return message;
    }

    PromptMessageContent conversedContent =
        (PromptMessageContent)
            storableMcpDataContainer.replaceWithDocumentReference(documentFactory);
    return new PromptMessage(message.role(), conversedContent);
  }

  private static Document createDocument(DocumentFactory factory, byte[] data, String mimeType) {
    return factory.create(DocumentCreationRequest.from(data).contentType(mimeType).build());
  }

  /** A message that is part of a retrieved prompt. */
  public record PromptMessage(String role, PromptMessageContent content) {}

  /**
   * General interface for message contents that are part of a retrieved prompt.
   *
   * <p>Those can contain text or other content, like binaries, or references to Camunda documents.
   */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = McpClientGetPromptResult.TextMessage.class, name = "text"),
    @JsonSubTypes.Type(value = McpClientGetPromptResult.BlobMessage.class, name = "blob"),
    @JsonSubTypes.Type(
        value = McpClientGetPromptResult.EmbeddedResourceContent.class,
        name = "resource"),
    @JsonSubTypes.Type(
        value = McpClientGetPromptResult.CamundaDocumentReference.class,
        name = "document")
  })
  public sealed interface PromptMessageContent {}

  /** Text messages hold plain textual content. */
  public record TextMessage(String text) implements PromptMessageContent {}

  /**
   * Blob messages hold arbitrary binary data which is supposed to be stored as a Camunda document.
   */
  public record BlobMessage(byte[] data, String mimeType)
      implements PromptMessageContent, StorableMcpDataContainer<PromptMessageContent> {

    @Override
    public PromptMessageContent replaceWithDocumentReference(DocumentFactory documentFactory) {
      var document = createDocument(documentFactory, data, mimeType);

      return new CamundaDocumentReference(document);
    }
  }

  /**
   * Embedded resource messages can either contain textual or binary content. Only embedded binary
   * resources are stored in the Camunda document store.
   */
  public record EmbeddedResourceContent(EmbeddedResource resource)
      implements PromptMessageContent, StorableMcpDataContainer<PromptMessageContent> {

    @Override
    public PromptMessageContent replaceWithDocumentReference(DocumentFactory documentFactory) {
      if (!(resource instanceof EmbeddedResource.BlobResource blobResource)) {
        return this;
      }

      var document = createDocument(documentFactory, blobResource.blob(), blobResource.mimeType());

      return new EmbeddedResourceContent(
          new EmbeddedResource.CamundaDocumentReference(blobResource.uri, document));
    }

    public sealed interface EmbeddedResource {

      record BlobResource(String uri, byte[] blob, String mimeType) implements EmbeddedResource {}

      record TextResource(String uri, String text, String mimeType) implements EmbeddedResource {}

      record CamundaDocumentReference(String uri, Document data) implements EmbeddedResource {}
    }
  }

  /**
   * Message holding a reference to a Camunda document. This record is a result of transforming
   * prompt messages of type {@link BlobMessage}
   */
  public record CamundaDocumentReference(Document document) implements PromptMessageContent {}
}
