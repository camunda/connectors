/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import io.camunda.connector.agenticai.mcp.McpClientErrorCodes;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientReadResourceResult;
import io.camunda.connector.agenticai.mcp.client.model.result.ResourceData;
import io.camunda.connector.api.error.ConnectorException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class ReadResourceRequest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReadResourceRequest.class);

  private static final String RESOURCE_URI_KEY = "uri";

  public McpClientReadResourceResult execute(
      McpSyncClient client, AllowDenyList resourceUriFilter, Map<String, Object> params) {
    var resourceUri = getResourceUri(params);

    if (!resourceUriFilter.isPassing(resourceUri)) {
      LOGGER.error(
          "MCP({}): Resource '{}' is not allowed by the filter {}.",
          client.getClientInfo().name(),
          resourceUri,
          resourceUriFilter);
      throw new ConnectorException(
          McpClientErrorCodes.ERROR_CODE_READ_RESOURCE_ERROR,
          "Reading resource '%s' is not allowed by filter configuration: %s"
              .formatted(resourceUri, resourceUriFilter));
    }

    try {
      LOGGER.debug(
          "MCP({}): Executing read resource request with params: {}",
          client.getClientInfo().name(),
          params);
      var readResourceResult = client.readResource(new McpSchema.ReadResourceRequest(resourceUri));

      var contentResult = readResourceResult.contents().stream().map(this::map).toList();

      return new McpClientReadResourceResult(contentResult);
    } catch (Exception e) {
      LOGGER.error(
          "MCP({}): Failed to read resource from MCP server.", client.getClientInfo().name(), e);
      throw new ConnectorException(
          McpClientErrorCodes.ERROR_CODE_READ_RESOURCE_ERROR,
          "Failed to read resource from MCP server: %s".formatted(e.getMessage()),
          e);
    }
  }

  private String getResourceUri(Map<String, Object> params) {
    if (!params.containsKey(RESOURCE_URI_KEY)) {
      throw new ConnectorException(
          McpClientErrorCodes.ERROR_CODE_INVALID_PARAMS, "Resource URI must be provided");
    }

    if (!(params.get(RESOURCE_URI_KEY) instanceof String resourceUri)) {
      throw new ConnectorException(
          McpClientErrorCodes.ERROR_CODE_INVALID_PARAMS, "Resource URI must be a string");
    }

    if (!StringUtils.hasText(resourceUri)) {
      throw new ConnectorException(
          McpClientErrorCodes.ERROR_CODE_INVALID_PARAMS, "Resource URI must not be blank or empty");
    }

    return resourceUri;
  }

  private ResourceData map(McpSchema.ResourceContents content) {
    return switch (content) {
      case McpSchema.BlobResourceContents blobResourceContents ->
          new ResourceData.BlobResourceData(
              blobResourceContents.uri(),
              blobResourceContents.mimeType(),
              Base64.getDecoder().decode(blobResourceContents.blob()));
      case McpSchema.TextResourceContents textResourceContents ->
          new ResourceData.TextResourceData(
              textResourceContents.uri(),
              textResourceContents.mimeType(),
              textResourceContents.text());
    };
  }
}
