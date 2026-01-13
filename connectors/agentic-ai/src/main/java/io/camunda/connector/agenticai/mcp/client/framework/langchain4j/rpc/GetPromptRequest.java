/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import dev.langchain4j.mcp.client.*;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientGetPromptResult;
import io.camunda.connector.api.error.ConnectorException;
import java.util.*;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GetPromptRequest {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetPromptRequest.class);
  public static final String MCP_CLIENT_INVALID_PARAMS_KEY = "MCP_CLIENT_INVALID_PARAMS";

  public McpClientGetPromptResult execute(McpClient client, Map<String, Object> params) {
    final var parameters = parseParameters(params);

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
          "MCP_CLIENT_GET_PROMPT_ERROR",
          "Error getting prompt '%s': %s".formatted(parameters.name(), e.getMessage()),
          e);
    }
  }

  private McpClientGetPromptResult.PromptMessage map(McpPromptMessage promptMessage) {
    final var content = promptMessage.content();

    if (content instanceof McpImageContent mcpImageContent) {
      return new McpClientGetPromptResult.BlobMessage(
          promptMessage.role().name(),
          Base64.getDecoder().decode(mcpImageContent.data()),
          mcpImageContent.mimeType());
    }

    if (content instanceof McpTextContent mcpTextContent) {
      return new McpClientGetPromptResult.TextMessage(
          promptMessage.role().name(), mcpTextContent.text());
    }

    if (content instanceof McpEmbeddedResource mcpEmbeddedResource) {
      return new McpClientGetPromptResult.EmbeddedResourceMessage(
          promptMessage.role().name(),
          switch (mcpEmbeddedResource.resource()) {
            case McpBlobResourceContents mcpBlobResourceContents ->
                new McpClientGetPromptResult.EmbeddedResourceMessage.EmbeddedResource.BlobResource(
                    mcpBlobResourceContents.uri(),
                    Base64.getDecoder().decode(mcpBlobResourceContents.blob()),
                    mcpBlobResourceContents.mimeType());
            case McpTextResourceContents mcpTextResourceContents ->
                new McpClientGetPromptResult.EmbeddedResourceMessage.EmbeddedResource.TextResource(
                    mcpTextResourceContents.uri(),
                    mcpTextResourceContents.text(),
                    mcpTextResourceContents.mimeType());
          });
    }

    throw new UnsupportedOperationException(
        "Unsupported prompt message content type: %s".formatted(content.getType()));
  }

  private GetPromptParameters parseParameters(Map<String, Object> params) {
    if (MapUtils.isEmpty(params)) {
      throw new ConnectorException(
          MCP_CLIENT_INVALID_PARAMS_KEY, "Parameters for get prompt request cannot be empty.");
    }

    if (!params.containsKey("name")) {
      throw new ConnectorException(
              MCP_CLIENT_INVALID_PARAMS_KEY, "Prompt name is required in params.");
    }

    if (!(params.get("name") instanceof String promptName)) {
      throw new ConnectorException(MCP_CLIENT_INVALID_PARAMS_KEY, "Prompt name must be a string.");
    }

    var paramsArguments = params.getOrDefault("arguments", Collections.emptyMap());
    if (!(paramsArguments instanceof Map promptArguments)) {
      throw new ConnectorException(
          MCP_CLIENT_INVALID_PARAMS_KEY,
          "Incorrect format for prompt arguments. Expecting an object.");
    }

    return new GetPromptParameters(promptName, promptArguments);
  }

  record GetPromptParameters(String name, Map<String, Object> arguments) {}
}
