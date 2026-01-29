/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.*;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.camunda.connector.agenticai.mcp.McpClientErrorCodes;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientCallToolResult;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ToolCallRequest {
  private static final Logger LOGGER = LoggerFactory.getLogger(ToolCallRequest.class);
  private final ObjectMapper objectMapper;
  private final OutboundConnectorContext context;

  ToolCallRequest(ObjectMapper objectMapper, OutboundConnectorContext context) {
    this.objectMapper = objectMapper;
    this.context = context;
  }

  public McpClientCallToolResult execute(
      McpClient client, AllowDenyList toolNameFilter, Map<String, Object> params) {

    final var parameters = parseParams(params);
    final var toolExecutionRequest = createToolExecutionRequest(parameters);
    if (!toolNameFilter.isPassing(toolExecutionRequest.name())) {
      LOGGER.error(
          "MCP({}): Tool '{}' is not included in the filter {}.",
          client.key(),
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
        client.key(),
        parameters.name(),
        parameters.arguments());

    try {
      final var result = client.executeTool(toolExecutionRequest);

      LOGGER.debug(
          "MCP({}): Successfully executed tool '{}'", client.key(), toolExecutionRequest.name());

      // Try to extract structured content if available
      final var content = extractContentFromResult(result, toolExecutionRequest.name());

      return new McpClientCallToolResult(toolExecutionRequest.name(), content, false);
    } catch (Exception e) {
      LOGGER.error(
          "MCP({}): Failed to execute tool '{}'", client.key(), toolExecutionRequest.name(), e);
      return new McpClientCallToolResult(
          toolExecutionRequest.name(),
          List.of(
              TextContent.textContent(
                  "Error executing tool '%s': %s"
                      .formatted(toolExecutionRequest.name(), e.getMessage()))),
          true);
    }
  }

  /**
   * Extracts content from ToolExecutionResult. Attempts to handle structured content with multiple
   * content types (text, images, embedded resources) when available, falling back to simple text
   * extraction.
   *
   * @param result The ToolExecutionResult from MCP client
   * @param toolName The name of the tool (for logging)
   * @return List of Content objects
   */
  private List<Content> extractContentFromResult(ToolExecutionResult result, String toolName) {
    if (result == null) {
      return List.of(TextContent.textContent(ToolCallResult.CONTENT_NO_RESULT));
    }

    // Check if the result contains structured content with multiple types
    try {
      final var resultObject = result.result();
      if (resultObject instanceof Map<?, ?> resultMap) {
        // Check if it has a content array (MCP protocol structure)
        if (resultMap.containsKey("content") && resultMap.get("content") instanceof List<?> contentList) {
          return processContentList(contentList, toolName);
        }
      }
    } catch (Exception e) {
      LOGGER.debug(
          "Could not extract structured content from tool '{}' result, falling back to text: {}",
          toolName,
          e.getMessage());
    }

    // Fallback to text-based extraction
    final var normalizedResult =
        Optional.ofNullable(result.resultText())
            .filter(StringUtils::isNotBlank)
            .orElse(ToolCallResult.CONTENT_NO_RESULT);

    return List.of(TextContent.textContent(normalizedResult));
  }

  /**
   * Processes a list of content objects from MCP response, converting them to Camunda Content
   * types.
   *
   * @param contentList List of content objects from MCP
   * @param toolName The name of the tool (for logging)
   * @return List of processed Content objects
   */
  private List<Content> processContentList(List<?> contentList, String toolName) {
    if (contentList == null || contentList.isEmpty()) {
      return List.of(TextContent.textContent(ToolCallResult.CONTENT_NO_RESULT));
    }

    final var processedContent = new ArrayList<Content>();
    for (Object contentItem : contentList) {
      try {
        final var content = processContentItem(contentItem);
        if (content != null) {
          processedContent.add(content);
        }
      } catch (Exception e) {
        LOGGER.warn(
            "Failed to process content item from tool '{}': {}",
            toolName,
            e.getMessage());
        // Continue processing other items
      }
    }

    return processedContent.isEmpty()
        ? List.of(TextContent.textContent(ToolCallResult.CONTENT_NO_RESULT))
        : processedContent;
  }

  /**
   * Processes a single content item, handling different MCP content types.
   *
   * @param contentItem The content item to process
   * @return Processed Content object, or null if unsupported
   */
  private Content processContentItem(Object contentItem) {
    return switch (contentItem) {
      case McpTextContent mcpTextContent -> TextContent.textContent(mcpTextContent.text());
      case McpImageContent mcpImageContent -> createDocumentFromImage(mcpImageContent);
      case McpEmbeddedResource mcpEmbeddedResource ->
          createDocumentFromEmbeddedResource(mcpEmbeddedResource);
      case Map<?, ?> contentMap -> processContentMap(contentMap);
      default -> {
        LOGGER.debug(
            "Unsupported content type: {}. Converting to text.", contentItem.getClass().getName());
        yield TextContent.textContent(contentItem.toString());
      }
    };
  }

  /**
   * Processes a content item represented as a Map (for cases where MCP SDK returns raw maps).
   *
   * @param contentMap The content map to process
   * @return Processed Content object
   */
  private Content processContentMap(Map<?, ?> contentMap) {
    final var type = contentMap.get("type");
    if (type == null) {
      return TextContent.textContent(contentMap.toString());
    }

    return switch (type.toString()) {
      case "text" -> {
        final var text = contentMap.get("text");
        yield text != null
            ? TextContent.textContent(text.toString())
            : TextContent.textContent("");
      }
      case "image" -> {
        final var data = contentMap.get("data");
        final var mimeType = contentMap.getOrDefault("mimeType", "image/png");
        if (data != null) {
          yield createDocumentFromBase64Image(data.toString(), mimeType.toString());
        }
        yield null;
      }
      case "resource" -> {
        final var resource = contentMap.get("resource");
        if (resource instanceof Map<?, ?> resourceMap) {
          yield processEmbeddedResourceMap(resourceMap);
        }
        yield null;
      }
      default -> TextContent.textContent(contentMap.toString());
    };
  }

  /**
   * Creates a DocumentContent from an McpImageContent.
   *
   * @param imageContent The MCP image content
   * @return DocumentContent containing the image
   * @throws IllegalArgumentException if the image data is invalid
   */
  private DocumentContent createDocumentFromImage(McpImageContent imageContent) {
    try {
      final var imageBytes = Base64.getDecoder().decode(imageContent.data());
      final var document =
          context.create(
              DocumentCreationRequest.from(imageBytes)
                  .contentType(imageContent.mimeType())
                  .fileName("tool-result-image.png")
                  .build());

      if (document == null) {
        throw new IllegalStateException("Document creation returned null");
      }

      return DocumentContent.documentContent(document);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Failed to decode base64 image data: " + e.getMessage(), e);
    }
  }

  /**
   * Creates a DocumentContent from base64-encoded image data.
   *
   * @param base64Data The base64-encoded image data
   * @param mimeType The MIME type of the image
   * @return DocumentContent containing the image
   * @throws IllegalArgumentException if the image data is invalid
   */
  private DocumentContent createDocumentFromBase64Image(String base64Data, String mimeType) {
    try {
      final var imageBytes = Base64.getDecoder().decode(base64Data);
      final var document =
          context.create(
              DocumentCreationRequest.from(imageBytes)
                  .contentType(mimeType)
                  .fileName("tool-result-image.png")
                  .build());

      if (document == null) {
        throw new IllegalStateException("Document creation returned null");
      }

      return DocumentContent.documentContent(document);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Failed to decode base64 image data: " + e.getMessage(), e);
    }
  }

  /**
   * Creates a Content from an McpEmbeddedResource.
   *
   * @param embeddedResource The MCP embedded resource
   * @return Content (DocumentContent for blobs, TextContent for text resources)
   * @throws IllegalArgumentException if the resource data is invalid
   */
  private Content createDocumentFromEmbeddedResource(McpEmbeddedResource embeddedResource) {
    return switch (embeddedResource.resource()) {
      case McpBlobResourceContents blobContents -> {
        try {
          final var blobBytes = Base64.getDecoder().decode(blobContents.blob());
          final var document =
              context.create(
                  DocumentCreationRequest.from(blobBytes)
                      .contentType(blobContents.mimeType())
                      .fileName("tool-result-resource")
                      .build());

          if (document == null) {
            throw new IllegalStateException("Document creation returned null");
          }

          yield DocumentContent.documentContent(document);
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException(
              "Failed to decode base64 blob data: " + e.getMessage(), e);
        }
      }
      case McpTextResourceContents textContents -> TextContent.textContent(textContents.text());
    };
  }

  /**
   * Processes an embedded resource represented as a Map.
   *
   * @param resourceMap The resource map to process
   * @return Content object
   */
  private Content processEmbeddedResourceMap(Map<?, ?> resourceMap) {
    final var type = resourceMap.get("type");
    if (type == null) {
      return null;
    }

    return switch (type.toString()) {
      case "text" -> {
        final var text = resourceMap.get("text");
        yield text != null ? TextContent.textContent(text.toString()) : null;
      }
      case "blob" -> {
        final var blob = resourceMap.get("blob");
        final var mimeType = resourceMap.getOrDefault("mimeType", "application/octet-stream");
        if (blob != null) {
          try {
            final var blobBytes = Base64.getDecoder().decode(blob.toString());
            final var document =
                context.create(
                    DocumentCreationRequest.from(blobBytes)
                        .contentType(mimeType.toString())
                        .fileName("tool-result-resource")
                        .build());

            if (document == null) {
              LOGGER.warn("Document creation returned null for blob resource");
              yield null;
            }

            yield DocumentContent.documentContent(document);
          } catch (IllegalArgumentException e) {
            LOGGER.warn("Failed to decode base64 blob data from resource map: {}", e.getMessage());
            yield null;
          }
        }
        yield null;
      }
      default -> null;
    };
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

  private ToolExecutionRequest createToolExecutionRequest(ToolExecutionParameters params) {
    if (params == null || params.name() == null) {
      throw new IllegalArgumentException("Tool name must not be null");
    }

    final var arguments = Optional.ofNullable(params.arguments()).orElseGet(Collections::emptyMap);

    try {
      return ToolExecutionRequest.builder()
          .name(params.name())
          .arguments(objectMapper.writeValueAsString(arguments))
          .build();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(
          "Failed to create tool execution request for tool '%s'".formatted(params.name()), e);
    }
  }

  record ToolExecutionParameters(String name, Map<String, Object> arguments) {}
}
