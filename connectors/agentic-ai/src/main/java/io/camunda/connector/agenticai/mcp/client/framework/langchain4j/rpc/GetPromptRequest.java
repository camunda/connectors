/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import dev.langchain4j.mcp.client.*;
import io.camunda.connector.agenticai.mcp.McpClientErrorCodes;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientGetPromptResult;
import io.camunda.connector.api.error.ConnectorException;
import java.util.*;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

final class GetPromptRequest {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetPromptRequest.class);

  public McpClientGetPromptResult execute(McpClient client, Map<String, Object> params) {
    final var parameters = parseParams(params);

    LOGGER.debug(
        "MCP({}): Executing get prompt '{}' with arguments: {}",
        client.key(),
        parameters.name(),
        parameters.arguments());

    try {
      final var promptArguments =
          Optional.ofNullable(parameters.arguments()).orElseGet(Collections::emptyMap);

      final var result = client.getPrompt(parameters.name(), promptArguments);

      LOGGER.debug(
          "MCP({}): Successfully retrieved prompt '{}' with {} messages",
          client.key(),
          parameters.name(),
          result.messages().size());

      return new McpClientGetPromptResult(
          result.description(), result.messages().stream().map(this::map).toList());
    } catch (Exception e) {
      LOGGER.error("MCP({}): Failed to get prompt '{}'", client.key(), parameters.name(), e);
      throw new ConnectorException(
          McpClientErrorCodes.ERROR_CODE_GET_PROMPT_ERROR,
          "Error getting prompt '%s': %s".formatted(parameters.name(), e.getMessage()),
          e);
    }
  }

  private McpClientGetPromptResult.PromptMessage map(McpPromptMessage promptMessage) {
    final var content = promptMessage.content();
    final var role = promptMessage.role().name().toLowerCase();

    var resultingContent =
        switch (content) {
          case McpImageContent mcpImageContent ->
              new McpClientGetPromptResult.BlobMessage(
                  Base64.getDecoder().decode(mcpImageContent.data()), mcpImageContent.mimeType());
          case McpTextContent mcpTextContent ->
              new McpClientGetPromptResult.TextMessage(mcpTextContent.text());
          case McpEmbeddedResource mcpEmbeddedResource ->
              mapEmbeddedResourceContent(mcpEmbeddedResource);
        };

    return new McpClientGetPromptResult.PromptMessage(role, resultingContent);
  }

  private McpClientGetPromptResult.PromptMessageContent mapEmbeddedResourceContent(
      McpEmbeddedResource embeddedResource) {
    return new McpClientGetPromptResult.EmbeddedResourceContent(
        switch (embeddedResource.resource()) {
          case McpBlobResourceContents mcpBlobResourceContents ->
              new McpClientGetPromptResult.EmbeddedResourceContent.EmbeddedResource.BlobResource(
                  mcpBlobResourceContents.uri(),
                  Base64.getDecoder().decode(mcpBlobResourceContents.blob()),
                  mcpBlobResourceContents.mimeType());
          case McpTextResourceContents mcpTextResourceContents ->
              new McpClientGetPromptResult.EmbeddedResourceContent.EmbeddedResource.TextResource(
                  mcpTextResourceContents.uri(),
                  mcpTextResourceContents.text(),
                  mcpTextResourceContents.mimeType());
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
