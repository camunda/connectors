/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.result;

import io.camunda.connector.agenticai.mcp.client.model.content.McpBlobContent;
import io.camunda.connector.agenticai.mcp.client.model.content.McpContent;
import io.camunda.connector.agenticai.mcp.client.model.content.McpDocumentContent;
import io.camunda.connector.agenticai.mcp.client.model.content.McpEmbeddedResourceContent;
import io.camunda.connector.agenticai.mcp.client.model.content.McpObjectContent;
import io.camunda.connector.agenticai.mcp.client.model.content.McpResourceLinkContent;
import io.camunda.connector.agenticai.mcp.client.model.content.McpTextContent;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;

@AgenticAiRecord
public record McpClientCallToolResult(String name, List<McpContent> content, Boolean isError)
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

  private McpContent createDocumentIfEligible(McpContent content, DocumentFactory documentFactory) {
    return switch (content) {
      case McpBlobContent blobContent -> createFromBinary(blobContent, documentFactory);
      case McpEmbeddedResourceContent embeddedResourceContent ->
          createFromEmbeddedResource(embeddedResourceContent, documentFactory);
      case McpDocumentContent documentContent -> documentContent;
      case McpObjectContent objectContent -> objectContent;
      case McpResourceLinkContent resourceLinkContent -> resourceLinkContent;
      case McpTextContent textContent -> textContent;
    };
  }

  private McpEmbeddedResourceContent createFromEmbeddedResource(
      McpEmbeddedResourceContent embeddedResourceContent, DocumentFactory documentFactory) {
    var resource = embeddedResourceContent.resource();

    // Only BlobResource needs to be converted to a document
    if (!(resource
        instanceof
        McpEmbeddedResourceContent.BlobResource(String uri, String mimeType, byte[] blob))) {
      return embeddedResourceContent;
    }

    var createdDocument =
        documentFactory.create(DocumentCreationRequest.from(blob).contentType(mimeType).build());

    return new McpEmbeddedResourceContent(
        new McpEmbeddedResourceContent.BlobDocumentResource(uri, mimeType, createdDocument),
        embeddedResourceContent.metadata());
  }

  private McpDocumentContent createFromBinary(
      McpBlobContent blobContent, DocumentFactory documentFactory) {
    var createdDocument =
        documentFactory.create(
            DocumentCreationRequest.from(blobContent.blob())
                .contentType(blobContent.mimeType())
                .customProperties(blobContent.metadata())
                .build());

    return new McpDocumentContent(createdDocument, blobContent.metadata());
  }
}
