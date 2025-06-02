/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import java.util.List;
import java.util.Optional;

public class AgentResponseHandlerImpl implements AgentResponseHandler {

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

    // keep null handling for backward compatibility
    if (responseConfiguration.format() == null
        || (responseConfiguration.format()
                instanceof TextResponseFormatConfiguration(boolean includeText)
            && includeText)) {
      assistantMessage.content().stream()
          .filter(c -> c instanceof TextContent)
          .map(c -> ((TextContent) c).text())
          .findFirst()
          .ifPresent(builder::responseText);
    }

    if (responseConfiguration.includeAssistantMessage()) {
      builder.responseMessage(assistantMessage);
    }

    return builder.build();
  }
}
