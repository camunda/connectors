/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc;

import dev.langchain4j.mcp.client.*;
import io.camunda.connector.agenticai.mcp.McpClientErrorCodes;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientGetPromptResult;
import io.camunda.connector.api.error.ConnectorException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.*;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

final class GetPromptRequest {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetPromptRequest.class);

  private final String clientId;

  GetPromptRequest(String clientId) {
    this.clientId = clientId;
  }

  public McpClientGetPromptResult execute(
      McpSyncClient client, AllowDenyList promptNameFilter, Map<String, Object> params) {
    final var getPromptParams = parseParams(params);

    if (!promptNameFilter.isPassing(getPromptParams.name())) {
      LOGGER.error(
          "MCP({}): Prompt '{}' is not allowed by the filter {}.",
          clientId,
          getPromptParams.name(),
          promptNameFilter);
      throw new ConnectorException(
          McpClientErrorCodes.ERROR_CODE_GET_PROMPT_ERROR,
          "Getting prompt '%s' is not allowed by filter configuration: %s"
              .formatted(getPromptParams.name(), promptNameFilter));
    }

    LOGGER.debug(
        "MCP({}): Executing get prompt '{}' with arguments: {}",
        clientId,
        getPromptParams.name(),
        getPromptParams.arguments());

    try {
      final var promptArguments =
          Optional.ofNullable(getPromptParams.arguments()).orElseGet(Collections::emptyMap);

      final var result =
          client.getPrompt(new McpSchema.GetPromptRequest(getPromptParams.name(), promptArguments));

      LOGGER.debug(
          "MCP({}): Successfully retrieved prompt '{}' with {} messages",
          clientId,
          getPromptParams.name(),
          result.messages().size());

      return new McpClientGetPromptResult(
          result.description(), result.messages().stream().map(this::map).toList());
    } catch (Exception e) {
      LOGGER.error("MCP({}): Failed to get prompt '{}'", clientId, getPromptParams.name(), e);
      throw new ConnectorException(
          McpClientErrorCodes.ERROR_CODE_GET_PROMPT_ERROR,
          "Error getting prompt '%s': %s".formatted(getPromptParams.name(), e.getMessage()),
          e);
    }
  }

  private McpClientGetPromptResult.PromptMessage map(McpSchema.PromptMessage promptMessage) {
    final var content = promptMessage.content();
    final var role = promptMessage.role().name().toLowerCase();

    var resultingContent =
        switch (content) {
          case McpSchema.AudioContent audioContent ->
              new McpClientGetPromptResult.BlobMessage(
                  audioContent.mimeType(), Base64.getDecoder().decode(audioContent.data()));
          case McpSchema.EmbeddedResource embeddedResource ->
              mapEmbeddedResourceContent(embeddedResource);
          case McpSchema.ImageContent imageContent ->
              new McpClientGetPromptResult.BlobMessage(
                  imageContent.mimeType(), Base64.getDecoder().decode(imageContent.data()));
          case McpSchema.ResourceLink ignored ->
              throw new UnsupportedOperationException("Not yet implemented!");
          case McpSchema.TextContent textContent ->
              new McpClientGetPromptResult.TextMessage(textContent.text());
        };

    return new McpClientGetPromptResult.PromptMessage(role, resultingContent);
  }

  private McpClientGetPromptResult.PromptMessageContent mapEmbeddedResourceContent(
      McpSchema.EmbeddedResource embeddedResource) {
    return new McpClientGetPromptResult.EmbeddedResourceContent(
        switch (embeddedResource.resource()) {
          case McpSchema.BlobResourceContents blobResourceContents ->
              new McpClientGetPromptResult.EmbeddedResourceContent.EmbeddedResource.BlobResource(
                  blobResourceContents.uri(),
                  blobResourceContents.mimeType(),
                  Base64.getDecoder().decode(blobResourceContents.blob()));
          case McpSchema.TextResourceContents textResourceContents ->
              new McpClientGetPromptResult.EmbeddedResourceContent.EmbeddedResource.TextResource(
                  textResourceContents.uri(),
                  textResourceContents.mimeType(),
                  textResourceContents.text());
        });
  }

  private GetPromptParameters parseParams(Map<String, Object> params) {
    if (MapUtils.isEmpty(params)) {
      throw new ConnectorException(
          McpClientErrorCodes.ERROR_CODE_INVALID_PARAMS,
          "Parameters for get prompt request cannot be empty.");
    }

    if (!params.containsKey("name")) {
      throw new ConnectorException(
          McpClientErrorCodes.ERROR_CODE_INVALID_PARAMS, "Prompt name is required in params.");
    }

    if (!(params.get("name") instanceof String promptName)) {
      throw new ConnectorException(
          McpClientErrorCodes.ERROR_CODE_INVALID_PARAMS, "Prompt name must be a string.");
    }

    if (!StringUtils.hasText(promptName)) {
      throw new ConnectorException(
          McpClientErrorCodes.ERROR_CODE_INVALID_PARAMS, "Prompt name cannot be empty or blank.");
    }

    var paramsArguments = params.getOrDefault("arguments", Collections.emptyMap());
    if (!(paramsArguments instanceof Map promptArguments)) {
      throw new ConnectorException(
          McpClientErrorCodes.ERROR_CODE_INVALID_PARAMS,
          "Incorrect format for prompt arguments. Expecting an object.");
    }

    return new GetPromptParameters(promptName, promptArguments);
  }

  record GetPromptParameters(String name, Map<String, Object> arguments) {}
}
