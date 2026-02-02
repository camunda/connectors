/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.McpClientErrorCodes;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.model.message.content.BinaryContent;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.EmbeddedResourceContent;
import io.camunda.connector.agenticai.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.model.message.content.ResourceLinkContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.util.ObjectMapperConstants;
import io.camunda.connector.api.error.ConnectorException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

final class ToolCallRequest {
  private static final Logger LOGGER = LoggerFactory.getLogger(ToolCallRequest.class);
  private final ObjectMapper objectMapper;

  ToolCallRequest(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public McpClientCallToolResult execute(
      McpSyncClient client, AllowDenyList toolNameFilter, Map<String, Object> params) {

    final var parameters = parseParams(params);
    final var toolExecutionRequest = createToolExecutionRequest(parameters);
    if (!toolNameFilter.isPassing(toolExecutionRequest.name())) {
      LOGGER.error(
          "MCP({}): Tool '{}' is not included in the filter {}.",
          client.getClientInfo().name(),
          toolExecutionRequest.name(),
          toolNameFilter);
      return new McpClientCallToolResult(
          toolExecutionRequest.name(),
          List.of(
              TextContent.textContent(
                  "Executing tool '%s' is not allowed by filter configuration: %s"
                      .formatted(toolExecutionRequest.name(), toolNameFilter))),
          true);
    }

    LOGGER.debug(
        "MCP({}): Executing tool '{}' with arguments: {}",
        client.getClientInfo().name(),
        parameters.name(),
        parameters.arguments());

    try {
      final var result = client.callTool(toolExecutionRequest);

      LOGGER.debug(
          "MCP({}): Successfully executed tool '{}'",
          client.getClientInfo().name(),
          toolExecutionRequest.name());

      if (!hasContent(result)) {
        LOGGER.debug(
            "MCP({}): Tool '{}' returned no content",
            client.getClientInfo().name(),
            toolExecutionRequest.name());
        return new McpClientCallToolResult(
            toolExecutionRequest.name(),
            List.of(TextContent.textContent(ToolCallResult.CONTENT_NO_RESULT)),
            false);
      }

      if (result.structuredContent() != null) {
        return new McpClientCallToolResult(
            toolExecutionRequest.name(),
            List.of(ObjectContent.objectContent(fromObjectContent(result.structuredContent()))),
            false);
      }

      var mappedContent = result.content().stream().map(this::mapContent).toList();

      return new McpClientCallToolResult(toolExecutionRequest.name(), mappedContent, false);
    } catch (Exception e) {
      LOGGER.error(
          "MCP({}): Failed to execute tool '{}'",
          client.getClientInfo().name(),
          toolExecutionRequest.name(),
          e);
      return new McpClientCallToolResult(
          toolExecutionRequest.name(),
          List.of(
              TextContent.textContent(
                  "Error executing tool '%s': %s"
                      .formatted(toolExecutionRequest.name(), e.getMessage()))),
          true);
    }
  }

  private Content mapContent(McpSchema.Content responseContent) {
    return switch (responseContent) {
      case McpSchema.AudioContent audioContent ->
          fromBlob(audioContent.data(), audioContent.mimeType(), audioContent.meta());
      case McpSchema.EmbeddedResource embeddedResource -> mapEmbeddedResource(embeddedResource);
      case McpSchema.ImageContent imageContent ->
          fromBlob(imageContent.data(), imageContent.mimeType(), imageContent.meta());
      case McpSchema.ResourceLink resourceLink -> mapResourceLink(resourceLink);
      case McpSchema.TextContent textContent -> TextContent.textContent(textContent.text());
    };
  }

  private EmbeddedResourceContent mapEmbeddedResource(McpSchema.EmbeddedResource embeddedResource) {
    var resource =
        switch (embeddedResource.resource()) {
          case McpSchema.TextResourceContents textResource ->
              new EmbeddedResourceContent.TextResource(
                  textResource.uri(), textResource.mimeType(), textResource.text());
          case McpSchema.BlobResourceContents blobResource ->
              new EmbeddedResourceContent.BlobResource(
                  blobResource.uri(),
                  blobResource.mimeType(),
                  Base64.getDecoder().decode(blobResource.blob()));
        };
    return EmbeddedResourceContent.embeddedResource(resource);
  }

  private ResourceLinkContent mapResourceLink(McpSchema.ResourceLink resourceLink) {
    return new ResourceLinkContent(
        resourceLink.uri(),
        resourceLink.name(),
        resourceLink.description(),
        resourceLink.mimeType(),
        resourceLink.meta());
  }

  private BinaryContent fromBlob(String blob, String mimeType, Map<String, Object> metadata) {
    return new BinaryContent(Base64.getDecoder().decode(blob), mimeType, metadata);
  }

  private Map<String, Object> fromObjectContent(Object responseContent) {
    return objectMapper.convertValue(
        responseContent, ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE);
  }

  private boolean hasContent(McpSchema.CallToolResult result) {
    return result != null
        && (!CollectionUtils.isEmpty(result.content()) || result.structuredContent() != null);
  }

  private ToolExecutionParameters parseParams(Map<String, Object> params) {
    try {
      // TODO: Replace with manual parsing to skip serialization and immediate deserialization
      return objectMapper.convertValue(params, ToolExecutionParameters.class);
    } catch (IllegalArgumentException ex) {
      throw new ConnectorException(
          McpClientErrorCodes.ERROR_CODE_INVALID_PARAMS,
          "Unable to convert parameters passed to MCP client: %s".formatted(ex.getMessage()));
    }
  }

  private McpSchema.CallToolRequest createToolExecutionRequest(ToolExecutionParameters params) {
    if (params == null || params.name() == null) {
      throw new IllegalArgumentException("Tool name must not be null");
    }

    final var arguments = Optional.ofNullable(params.arguments()).orElseGet(Collections::emptyMap);

    return McpSchema.CallToolRequest.builder().name(params.name()).arguments(arguments).build();
  }

  record ToolExecutionParameters(String name, Map<String, Object> arguments) {}
}
