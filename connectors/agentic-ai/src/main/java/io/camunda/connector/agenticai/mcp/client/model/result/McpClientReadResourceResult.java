/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.result;

import io.camunda.connector.agenticai.mcp.client.model.McpDocumentSettings;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;

public record McpClientReadResourceResult(ResourceData resource)
    implements McpClientResultWithStorableData {
  @Override
  public McpClientResult convertStorableMcpResultData(
      DocumentFactory documentFactory, McpDocumentSettings settings) {
    return switch (resource) {
      case ResourceData.CamundaDocumentResourceData ignored -> this;
      case ResourceData.TextResourceData ignored -> this;
      case ResourceData.BlobResourceData blobResourceData ->
          new McpClientReadResourceResult(mapBlob(documentFactory, settings, blobResourceData));
    };
  }

  private ResourceData.CamundaDocumentResourceData mapBlob(
      DocumentFactory documentFactory,
      McpDocumentSettings documentSettings,
      ResourceData.BlobResourceData blobResourceData) {
    var createdDocument =
        documentFactory.create(
            DocumentCreationRequest.from(blobResourceData.blob())
                .timeToLive(documentSettings.timeToLive())
                .contentType(blobResourceData.mimeType())
                .build());
    return new ResourceData.CamundaDocumentResourceData(
        blobResourceData.uri(), blobResourceData.mimeType(), createdDocument);
  }
}
