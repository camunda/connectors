/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.common.json.ObjectMapperConstants;
import io.camunda.connector.agenticai.mcp.client.McpClientErrorCodes;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.model.content.McpBlobContent;
import io.camunda.connector.agenticai.mcp.client.model.content.McpContent;
import io.camunda.connector.agenticai.mcp.client.model.content.McpEmbeddedResourceContent;
import io.camunda.connector.agenticai.mcp.client.model.content.McpObjectContent;
import io.camunda.connector.agenticai.mcp.client.model.content.McpResourceLinkContent;
import io.camunda.connector.agenticai.mcp.client.model.content.McpTextContent;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.api.error.ConnectorException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.collections4.MapUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

final class ToolCallRequest {
  private static final Logger LOGGER = LoggerFactory.getLogger(ToolCallRequest.class);

  private static final String CONTENT_NO_RESULT =
      "Tool execution succeeded, but returned no result.";
  private static final String CONTENT_ERROR_NO_RESULT =
      "Tool execution failed, but returned no result.";

  private final String clientId;
  private final ObjectMapper objectMapper;

  ToolCallRequest(String clientId, ObjectMapper objectMapper) {
    this.clientId = clientId;
    this.objectMapper = objectMapper;
  }

  public McpClientCallToolResult execute(
      McpSyncClient client,
      AllowDenyList toolNameFilter,
      Map<String, Object> params,
      Map<String, Object> meta) {

    final var parameters = parseParams(params);
    final var toolExecutionRequest = createToolExecutionRequest(parameters, meta);
    if (!toolNameFilter.isPassing(toolExecutionRequest.name())) {
      LOGGER.error(
          "MCP({}): Tool '{}' is not included in the filter {}.",
          clientId,
          toolExecutionRequest.name(),
          toolNameFilter);
      return new McpClientCallToolResult(
          toolExecutionRequest.name(),
          List.of(
              McpTextContent.textContent(
                  "Executing tool '%s' is not allowed by filter configuration: %s"
                      .formatted(toolExecutionRequest.name(), toolNameFilter))),
          true);
    }

    LOGGER.debug(
        "MCP({}): Executing tool '{}' with arguments: {}",
        clientId,
        parameters.name(),
        parameters.arguments());

    try {
      final var result = client.callTool(toolExecutionRequest);

      // the server reports a failed tool execution in the result, not as a protocol error
      final boolean isError = result != null && Boolean.TRUE.equals(result.isError());
      if (isError) {
        LOGGER.debug(
            "MCP({}): Tool '{}' reported an error in its result",
            clientId,
            toolExecutionRequest.name());
      } else {
        LOGGER.debug(
            "MCP({}): Successfully executed tool '{}'", clientId, toolExecutionRequest.name());
      }

      final var mappedContent = mapResultContent(result);
      if (mappedContent.isEmpty()) {
        LOGGER.debug(
            "MCP({}): Tool '{}' returned no content", clientId, toolExecutionRequest.name());
        return new McpClientCallToolResult(
            toolExecutionRequest.name(),
            List.of(
                McpTextContent.textContent(isError ? CONTENT_ERROR_NO_RESULT : CONTENT_NO_RESULT)),
            isError);
      }

      return new McpClientCallToolResult(toolExecutionRequest.name(), mappedContent, isError);
    } catch (Exception e) {
      LOGGER.error(
          "MCP({}): Failed to execute tool '{}'", clientId, toolExecutionRequest.name(), e);
      return new McpClientCallToolResult(
          toolExecutionRequest.name(),
          List.of(
              McpTextContent.textContent(
                  "Error executing tool '%s': %s"
                      .formatted(toolExecutionRequest.name(), e.getMessage()))),
          true);
    }
  }

  /**
   * The content of a tool call result: the structured content if the server sent one, otherwise the
   * content blocks. A blank text block is left out, as {@link McpTextContent} holds no blank text.
   * Empty when the result holds nothing else.
   */
  private List<McpContent> mapResultContent(McpSchema.@Nullable CallToolResult result) {
    if (result == null) {
      return List.of();
    }
    if (result.structuredContent() != null) {
      return List.of(McpObjectContent.objectContent(fromObjectContent(result.structuredContent())));
    }
    if (CollectionUtils.isEmpty(result.content())) {
      return List.of();
    }
    return result.content().stream()
        .filter(content -> !isBlankText(content))
        .map(this::mapContent)
        .toList();
  }

  private static boolean isBlankText(McpSchema.Content content) {
    return content instanceof McpSchema.TextContent textContent
        && (textContent.text() == null || textContent.text().isBlank());
  }

  private McpContent mapContent(McpSchema.Content responseContent) {
    return switch (responseContent) {
      case McpSchema.AudioContent audioContent ->
          fromBlob(audioContent.data(), audioContent.mimeType(), audioContent.meta());
      case McpSchema.EmbeddedResource embeddedResource -> mapEmbeddedResource(embeddedResource);
      case McpSchema.ImageContent imageContent ->
          fromBlob(imageContent.data(), imageContent.mimeType(), imageContent.meta());
      case McpSchema.ResourceLink resourceLink -> mapResourceLink(resourceLink);
      case McpSchema.TextContent textContent -> McpTextContent.textContent(textContent.text());
      default ->
          throw new UnsupportedOperationException(
              "Unsupported content type: " + responseContent.getClass().getSimpleName());
    };
  }

  private McpEmbeddedResourceContent mapEmbeddedResource(
      McpSchema.EmbeddedResource embeddedResource) {
    var resource =
        switch (embeddedResource.resource()) {
          case McpSchema.TextResourceContents textResource ->
              new McpEmbeddedResourceContent.TextResource(
                  textResource.uri(), textResource.mimeType(), textResource.text());
          case McpSchema.BlobResourceContents blobResource ->
              new McpEmbeddedResourceContent.BlobResource(
                  blobResource.uri(),
                  blobResource.mimeType(),
                  Base64.getDecoder().decode(blobResource.blob()));
          default ->
              throw new UnsupportedOperationException(
                  "Unsupported resource type: "
                      + embeddedResource.resource().getClass().getSimpleName());
        };
    return new McpEmbeddedResourceContent(resource, embeddedResource.meta());
  }

  private McpResourceLinkContent mapResourceLink(McpSchema.ResourceLink resourceLink) {
    return new McpResourceLinkContent(
        resourceLink.uri(),
        resourceLink.name(),
        resourceLink.description(),
        resourceLink.mimeType(),
        resourceLink.meta());
  }

  private McpBlobContent fromBlob(String blob, String mimeType, Map<String, Object> metadata) {
    return new McpBlobContent(Base64.getDecoder().decode(blob), mimeType, metadata);
  }

  private Map<String, Object> fromObjectContent(Object responseContent) {
    return objectMapper.convertValue(
        responseContent, ObjectMapperConstants.STRING_OBJECT_MAP_TYPE_REFERENCE);
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

  private McpSchema.CallToolRequest createToolExecutionRequest(
      ToolExecutionParameters params, Map<String, Object> meta) {
    if (params == null || params.name() == null) {
      throw new IllegalArgumentException("Tool name must not be null");
    }

    final var arguments = Optional.ofNullable(params.arguments()).orElseGet(Collections::emptyMap);

    return McpSchema.CallToolRequest.builder(params.name())
        .arguments(arguments)
        .meta(MapUtils.isEmpty(meta) ? null : meta)
        .build();
  }

  record ToolExecutionParameters(String name, Map<String, Object> arguments) {}
}
