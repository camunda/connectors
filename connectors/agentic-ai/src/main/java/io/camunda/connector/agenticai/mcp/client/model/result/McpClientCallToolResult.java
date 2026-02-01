/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.result;

import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.agenticai.model.message.content.BinaryContent;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.message.content.EmbeddedResourceBlobDocumentContent;
import io.camunda.connector.agenticai.model.message.content.EmbeddedResourceContent;
import io.camunda.connector.agenticai.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.model.message.content.ResourceLinkContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

@AgenticAiRecord
public record McpClientCallToolResult(String name, List<Content> content, Boolean isError)
    implements McpClientResultWithStorableData, McpClientCallToolResultBuilder.With {
  @Override
  public McpClientResult convertStorableMcpResultData(DocumentFactory documentFactory) {
    if (CollectionUtils.isEmpty(content)) {
      return this;
    }

    var mappedContent =
        content.stream().map(c -> createDocumentIfEligible(c, documentFactory)).toList();

    return new McpClientCallToolResult(name, mappedContent, isError);
  }

  private Content createDocumentIfEligible(Content content, DocumentFactory documentFactory) {
    return switch (content) {
      case BinaryContent binaryContent -> createFromBinary(binaryContent, documentFactory);
      case EmbeddedResourceContent embeddedResourceContent ->
          createFromEmbeddedResource(embeddedResourceContent, documentFactory);
      case DocumentContent documentContent -> documentContent;
      case ObjectContent objectContent -> objectContent;
      case ResourceLinkContent resourceLinkContent -> resourceLinkContent;
      case TextContent textContent -> textContent;
    };
  }

  private EmbeddedResourceContent createFromEmbeddedResource(
      EmbeddedResourceContent embeddedResourceContent, DocumentFactory documentFactory) {
    var resource = embeddedResourceContent.resource();
    
    // Only BlobResource needs to be converted to a document
    if (resource instanceof EmbeddedResourceContent.BlobResource blobResource) {
      var createdDocument =
          documentFactory.create(
              DocumentCreationRequest.from(blobResource.blob())
                  .contentType(blobResource.mimeType())
                  .build());
      
      return new EmbeddedResourceContent(
          new EmbeddedResourceBlobDocumentContent(
              blobResource.uri(), blobResource.mimeType(), createdDocument),
          embeddedResourceContent.metadata());
    }
    
    return embeddedResourceContent;
  }

  private DocumentContent createFromBinary(
      BinaryContent binaryContent, DocumentFactory documentFactory) {
    var createdDocument =
        documentFactory.create(
            DocumentCreationRequest.from(binaryContent.blob())
                .contentType(binaryContent.mimeType())
                .customProperties(binaryContent.metadata())
                .build());

    return new DocumentContent(createdDocument, binaryContent.metadata());
  }
}
