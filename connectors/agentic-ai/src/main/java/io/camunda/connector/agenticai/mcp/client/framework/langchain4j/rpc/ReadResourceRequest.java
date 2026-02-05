/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import dev.langchain4j.mcp.client.McpBlobResourceContents;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpResourceContents;
import dev.langchain4j.mcp.client.McpTextResourceContents;
import io.camunda.connector.agenticai.mcp.McpClientErrorCodes;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientReadResourceResult;
import io.camunda.connector.agenticai.mcp.client.model.result.ResourceData;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class ReadResourceRequest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReadResourceRequest.class);

  private static final String RESOURCE_URI_KEY = "uri";

  public McpClientReadResourceResult execute(
      McpClient client, AllowDenyList resourceUriFilter, Map<String, Object> params) {
    var resourceUri = getResourceUri(params);

    if (!resourceUriFilter.isPassing(resourceUri)) {
      LOGGER.error(
          "MCP({}): Resource '{}' is not allowed by the filter {}.",
          client.key(),
          resourceUri,
          resourceUriFilter);
      throw new ConnectorException(
          McpClientErrorCodes.ERROR_CODE_READ_RESOURCE_ERROR,
          "Reading resource '%s' is not allowed by filter configuration: %s"
              .formatted(resourceUri, resourceUriFilter));
    }

    try {
      LOGGER.debug(
          "MCP({}): Executing read resource request with params: {}", client.key(), params);
      var readResourceResult = client.readResource(resourceUri);

      var contentResult = readResourceResult.contents().stream().map(this::map).toList();

      return new McpClientReadResourceResult(contentResult);
    } catch (Exception e) {
      LOGGER.error("MCP({}): Failed to read resource from MCP server.", client.key(), e);
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

  private ResourceData map(McpResourceContents content) {
    return switch (content) {
      case McpBlobResourceContents mcpBlobResourceContents ->
          new ResourceData.BlobResourceData(
              mcpBlobResourceContents.uri(),
              mcpBlobResourceContents.mimeType(),
              Base64.getDecoder().decode(mcpBlobResourceContents.blob()),
              null);
      case McpTextResourceContents mcpTextResourceContents ->
          new ResourceData.TextResourceData(
              mcpTextResourceContents.uri(),
              mcpTextResourceContents.mimeType(),
              mcpTextResourceContents.text(),
              null);
    };
  }
}
