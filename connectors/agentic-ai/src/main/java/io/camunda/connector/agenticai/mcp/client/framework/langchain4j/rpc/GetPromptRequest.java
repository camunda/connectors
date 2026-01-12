/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpTextContent;
import io.camunda.connector.agenticai.mcp.client.model.result.McpClientGetPromptResult;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GetPromptRequest {

  private static final Logger LOGGER = LoggerFactory.getLogger(GetPromptRequest.class);
  private final ObjectMapper objectMapper;

  GetPromptRequest(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

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
          result.description(),
          result.messages().stream()
              .map(
                  message -> {
                    final var content = message.content();
                    if (content instanceof McpTextContent textContent) {
                      return new McpClientGetPromptResult.PromptMessage(
                          message.role().toString(), textContent.text());
                    } else {
                      LOGGER.warn(
                          "MCP({}): Prompt '{}' contains unsupported content type '{}' - only text content is supported. Skipping this content.",
                          client.key(),
                          parameters.name(),
                          content.getClass().getSimpleName());
                      return null;
                    }
                  })
              .filter(Objects::nonNull)
              .toList());
    } catch (Exception e) {
      LOGGER.error("MCP({}): Failed to get prompt '{}'", client.key(), parameters.name(), e);
      throw new ConnectorException(
          "MCP_CLIENT_GET_PROMPT_ERROR",
          "Error getting prompt '%s': %s".formatted(parameters.name(), e.getMessage()),
          e);
    }
  }

  private GetPromptParameters parseParameters(Map<String, Object> params) {
    if (MapUtils.isEmpty(params)) {
      throw new ConnectorException(
          "MCP_CLIENT_INVALID_PARAMS", "Parameters for get prompt request cannot be empty.");
    }

    if (!params.containsKey("name")) {
      throw new ConnectorException(
          "MCP_CLIENT_INVALID_PARAMS", "Prompt name is required in params.");
    }

    if (!(params.get("name") instanceof String promptName)) {
      throw new ConnectorException("MCP_CLIENT_INVALID_PARAMS", "Prompt name must be a string.");
    }

    var paramsArguments = params.getOrDefault("arguments", Collections.emptyMap());
    if (!(paramsArguments instanceof Map promptArguments)) {
      throw new ConnectorException(
          "MCP_CLIENT_INVALID_PARAMS",
          "Incorrect format for prompt arguments. Expecting object."
              .formatted(params.get("arguments").getClass().getSimpleName()));
    }

    return new GetPromptParameters(promptName, promptArguments);
  }

  record GetPromptParameters(String name, Map<String, Object> arguments) {}
}
