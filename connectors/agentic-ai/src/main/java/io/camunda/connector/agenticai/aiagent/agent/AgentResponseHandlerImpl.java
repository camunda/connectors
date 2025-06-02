/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_TO_PARSE_RESPONSE_CONTENT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentResponseBuilder;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.util.JacksonExceptionMessageExtractor;
import io.camunda.connector.api.error.ConnectorException;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public class AgentResponseHandlerImpl implements AgentResponseHandler {

  private final ObjectMapper objectMapper;

  public AgentResponseHandlerImpl(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public AgentResponse createResponse(
      AgentRequest request,
      AgentContext agentContext,
      AssistantMessage assistantMessage,
      List<ToolCallProcessVariable> toolCalls) {

    final var responseConfiguration =
        Optional.ofNullable(request.data().response())
            // default to text content only if not configured
            .orElseGet(
                () -> new ResponseConfiguration(new TextResponseFormatConfiguration(true), false));

    final var builder = AgentResponse.builder().context(agentContext).toolCalls(toolCalls);
    if (responseConfiguration.includeAssistantMessage()) {
      builder.responseMessage(assistantMessage);
    }

    findFirstResponseText(assistantMessage)
        .ifPresent(
            responseText -> addFirstResponseText(responseConfiguration, builder, responseText));

    return builder.build();
  }

  private Optional<String> findFirstResponseText(AssistantMessage assistantMessage) {
    return assistantMessage.content().stream()
        .filter(c -> c instanceof TextContent)
        .map(c -> ((TextContent) c).text())
        .filter(StringUtils::isNotBlank)
        .findFirst();
  }

  private void addFirstResponseText(
      ResponseConfiguration responseConfiguration,
      AgentResponseBuilder responseBuilder,
      String responseText) {

    if (StringUtils.isBlank(responseText)) {
      return;
    }

    // keep null handling for backward compatibility
    if (responseConfiguration.format() == null
        || (responseConfiguration.format()
                instanceof TextResponseFormatConfiguration(boolean includeText)
            && includeText)) {
      responseBuilder.responseText(responseText);
    }

    if (responseConfiguration.format() instanceof JsonResponseFormatConfiguration) {
      try {
        Object responseJson = objectMapper.readValue(responseText, Object.class);
        responseBuilder.responseJson(responseJson);
      } catch (JsonProcessingException e) {
        throw new ConnectorException(
            ERROR_CODE_FAILED_TO_PARSE_RESPONSE_CONTENT,
            "Failed to parse response content as JSON: %s"
                .formatted(
                    JacksonExceptionMessageExtractor.humanReadableJsonProcessingExceptionMessage(
                        e)));
      }
    }
  }
}
