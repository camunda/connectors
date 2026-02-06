/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_TO_PARSE_RESPONSE_CONTENT;
import static io.camunda.connector.agenticai.util.JacksonExceptionMessageExtractor.humanReadableJsonProcessingExceptionMessage;
import static io.camunda.connector.agenticai.util.ResponseTextUtil.stripMarkdownCodeBlocks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentResponseBuilder;
import io.camunda.connector.agenticai.aiagent.model.request.OutboundConnectorResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentResponseHandlerImpl implements AgentResponseHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentResponseHandlerImpl.class);
  private static final ResponseConfiguration DEFAULT_RESPONSE_CONFIGURATION =
      new OutboundConnectorResponseConfiguration(new TextResponseFormatConfiguration(false), false);

  private final ObjectMapper objectMapper;

  public AgentResponseHandlerImpl(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public AgentResponse createResponse(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      AssistantMessage assistantMessage,
      List<ToolCallProcessVariable> toolCalls) {

    // default to text content only if not configured
    final var responseConfiguration =
        Optional.ofNullable(executionContext.response()).orElse(DEFAULT_RESPONSE_CONFIGURATION);

    final var builder = AgentResponse.builder().context(agentContext).toolCalls(toolCalls);
    if (Boolean.TRUE.equals(responseConfiguration.includeAssistantMessage())) {
      builder.responseMessage(assistantMessage);
    }

    findFirstResponseText(assistantMessage)
        .ifPresent(
            responseText -> handleFirstResponseText(responseConfiguration, builder, responseText));

    return builder.build();
  }

  private Optional<String> findFirstResponseText(AssistantMessage assistantMessage) {
    return assistantMessage.content().stream()
        .filter(c -> c instanceof TextContent)
        .map(c -> ((TextContent) c).text())
        .filter(StringUtils::isNotBlank)
        .findFirst();
  }

  private void handleFirstResponseText(
      ResponseConfiguration responseConfiguration,
      AgentResponseBuilder responseBuilder,
      String responseText) {

    if (StringUtils.isBlank(responseText)) {
      return;
    }

    // keep null handling for backward compatibility
    final var format =
        Optional.ofNullable(responseConfiguration.format())
            .orElseGet(DEFAULT_RESPONSE_CONFIGURATION::format);

    switch (format) {
      case TextResponseFormatConfiguration textFormat -> handleTextResponseFormat(
          responseBuilder, responseText, textFormat);
      case JsonResponseFormatConfiguration ignored -> handleJsonResponseFormat(
          responseBuilder, responseText);
    }
  }

  private void handleTextResponseFormat(
      AgentResponseBuilder responseBuilder,
      String responseText,
      TextResponseFormatConfiguration textFormat) {
    responseBuilder.responseText(responseText);

    if (Boolean.TRUE.equals(textFormat.parseJson())) {
      try {
        parseTextToResponseJson(responseBuilder, responseText);
      } catch (Exception e) {
        var message = e.getMessage();
        if (e instanceof JsonProcessingException jpe) {
          message = humanReadableJsonProcessingExceptionMessage(jpe);
        }

        LOGGER.warn("Failed to parse response content as JSON: {}", message);
      }
    }
  }

  private void handleJsonResponseFormat(AgentResponseBuilder responseBuilder, String responseText) {
    try {
      parseTextToResponseJson(responseBuilder, responseText);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          ERROR_CODE_FAILED_TO_PARSE_RESPONSE_CONTENT,
          "Failed to parse response content as JSON: %s"
              .formatted(humanReadableJsonProcessingExceptionMessage(e)));
    }
  }

  private void parseTextToResponseJson(AgentResponseBuilder responseBuilder, String responseText)
      throws JsonProcessingException {
    // Strip markdown code blocks before parsing to handle AI models (like Anthropic Claude)
    // that wrap JSON responses in ```json ... ``` code fences
    String cleanedText = stripMarkdownCodeBlocks(responseText);
    Object responseJson = objectMapper.readValue(cleanedText, Object.class);
    responseBuilder.responseJson(responseJson);
  }
}
