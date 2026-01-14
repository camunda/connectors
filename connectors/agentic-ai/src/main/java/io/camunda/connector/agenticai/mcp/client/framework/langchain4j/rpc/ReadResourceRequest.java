package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import dev.langchain4j.mcp.client.McpBlobResourceContents;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpResourceContents;
import dev.langchain4j.mcp.client.McpTextResourceContents;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientReadResourceResult;
import io.camunda.connector.agenticai.mcp.client.model.result.ResourceData;
import io.camunda.connector.api.error.ConnectorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.Base64;
import java.util.Map;

public class ReadResourceRequest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReadResourceRequest.class);

  private static final String MCP_CLIENT_INVALID_PARAMS_KEY = "MCP_CLIENT_INVALID_PARAMS";

  public McpClientReadResourceResult execute(McpClient client, Map<String, Object> params) {
    try (var ignored = MDC.putCloseable("mcpClient", client.key())) {
      var resourceUri = getResourceUri(params);

      try {
        LOGGER.debug("Executing read resource request with params: {}", params);
        var readResourceResult = client.readResource(resourceUri);

        var contentResult = readResourceResult.contents().stream().map(this::map).toList();

        return new McpClientReadResourceResult(contentResult);
      } catch (Exception e) {
        LOGGER.error("Failed to read resource from MCP server.", e);
        throw new ConnectorException(
            "MCP_CLIENT_READ_RESOURCE_FAILED",
            "Failed to read resource from MCP server: %s".formatted(e.getMessage()),
            e);
      }
    }
  }

  private String getResourceUri(Map<String, Object> params) {
    if (!params.containsKey("resourceUri")) {
      throw new ConnectorException(MCP_CLIENT_INVALID_PARAMS_KEY, "Resource URI must be provided");
    }

    if (!(params.get("resourceUri") instanceof String resourceUri)) {
      throw new ConnectorException(MCP_CLIENT_INVALID_PARAMS_KEY, "Resource URI must be a string");
    }

    if (!StringUtils.hasText(resourceUri)) {
      throw new ConnectorException(
          MCP_CLIENT_INVALID_PARAMS_KEY, "Resource URI must not be blank or empty");
    }

    return resourceUri;
  }

  private ResourceData map(McpResourceContents content) {
    return switch (content) {
      case McpBlobResourceContents mcpBlobResourceContents ->
          new ResourceData.BlobResourceData(
              mcpBlobResourceContents.uri(),
              mcpBlobResourceContents.mimeType(),
              Base64.getDecoder().decode(mcpBlobResourceContents.blob()));
      case McpTextResourceContents mcpTextResourceContents ->
          new ResourceData.TextResourceData(
              mcpTextResourceContents.uri(),
              mcpTextResourceContents.mimeType(),
              mcpTextResourceContents.text());
    };
  }
}
