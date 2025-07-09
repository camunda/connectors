/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_IN_INVALID_STATE;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_NO_USER_MESSAGE_CONTENT;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT;
import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_WAITING_FOR_TOOL_INPUT_EMPTY_RESULTS;
import static io.camunda.connector.agenticai.model.message.MessageUtil.singleTextContent;
import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;

import dev.langchain4j.model.input.PromptTemplate;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.util.ClockProvider;
import io.camunda.connector.api.error.ConnectorException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public class AgentMessagesHandlerImpl implements AgentMessagesHandler {

  private final GatewayToolHandlerRegistry gatewayToolHandlers;

  public AgentMessagesHandlerImpl(GatewayToolHandlerRegistry gatewayToolHandlers) {
    this.gatewayToolHandlers = gatewayToolHandlers;
  }

  @Override
  public void addSystemMessage(
      AgentContext agentContext, RuntimeMemory memory, SystemPromptConfiguration systemPrompt) {
    if (StringUtils.isNotBlank(systemPrompt.prompt())) {
      // memory will take care of replacing any existing system message if already present
      memory.addMessage(
          SystemMessage.builder()
              .content(singleTextContent(promptFromConfiguration(systemPrompt)))
              .build());
    }
  }

  @Override
  public void addMessagesFromRequest(
      AgentContext agentContext,
      RuntimeMemory memory,
      UserPromptConfiguration userPrompt,
      List<ToolCallResult> toolCallResults) {
    // throw an error when receiving tool call results on an empty context as
    // most likely this is a modeling error
    if (agentContext.conversation() == null && !toolCallResults.isEmpty()) {
      throw new ConnectorException(
          ERROR_CODE_TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT,
          "Agent received tool call results, but the agent context was empty (no previous conversation). Is the context configured correctly?");
    }

    switch (agentContext.state()) {
      case READY -> addUserMessage(agentContext, memory, userPrompt);

      case WAITING_FOR_TOOL_INPUT -> addToolCallResults(agentContext, memory, toolCallResults);

      default ->
          throw new ConnectorException(
              ERROR_CODE_AGENT_IN_INVALID_STATE,
              "Agent is in invalid state '%s', not ready to add user messages"
                  .formatted(agentContext.state()));
    }
  }

  private void addUserMessage(
      AgentContext agentContext, RuntimeMemory memory, UserPromptConfiguration userPrompt) {
    final var content = new ArrayList<Content>();

    // add user prompt text
    if (StringUtils.isNotBlank(userPrompt.prompt())) {
      content.add(textContent(promptFromConfiguration(userPrompt)));
    }

    // add documents
    Optional.ofNullable(userPrompt.documents()).orElseGet(Collections::emptyList).stream()
        .map(DocumentContent::new)
        .forEach(content::add);

    if (content.isEmpty()) {
      throw new ConnectorException(
          ERROR_CODE_NO_USER_MESSAGE_CONTENT,
          "Agent is in state %s but no user prompt (no text, no documents) to add."
              .formatted(agentContext.state()));
    }

    memory.addMessage(
        UserMessage.builder()
            .content(content)
            .metadata(Map.of("timestamp", ClockProvider.zonedDateTimeNow()))
            .build());
  }

  private void addToolCallResults(
      AgentContext agentContext, RuntimeMemory memory, List<ToolCallResult> toolCallResults) {
    if (toolCallResults.isEmpty()) {
      throw new ConnectorException(
          ERROR_CODE_WAITING_FOR_TOOL_INPUT_EMPTY_RESULTS,
          "Agent is waiting for tool input, but tool call results were empty. Is the tool feedback loop configured correctly?");
    }

    var transformedToolCallResults =
        gatewayToolHandlers.transformToolCallResults(agentContext, toolCallResults);

    memory.addMessage(
        ToolCallResultMessage.builder()
            .results(transformedToolCallResults)
            .metadata(Map.of("timestamp", ClockProvider.zonedDateTimeNow()))
            .build());
  }

  private String promptFromConfiguration(
      AgentRequest.AgentRequestData.PromptConfiguration promptConfiguration) {
    // TODO replace L4j prompt with something more powerful?
    final var parameters =
        Optional.ofNullable(promptConfiguration.parameters()).orElseGet(Collections::emptyMap);
    return PromptTemplate.from(promptConfiguration.prompt()).apply(parameters).text();
  }
}
