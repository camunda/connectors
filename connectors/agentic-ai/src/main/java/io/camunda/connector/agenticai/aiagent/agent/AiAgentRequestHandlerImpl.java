/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED;

import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkChatResponse;
import io.camunda.connector.agenticai.aiagent.memory.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.runtime.MessageWindowRuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.util.Optional;

public class AiAgentRequestHandlerImpl implements AiAgentRequestHandler {

  private static final int DEFAULT_MAX_MODEL_CALLS = 10;
  private static final int DEFAULT_MAX_MEMORY_MESSAGES = 20;

  private final AgentInitializer agentInitializer;
  private final AgentMessagesHandler agentMessagesHandler;
  private final GatewayToolHandlerRegistry gatewayToolHandlers;
  private final AiFrameworkAdapter<?> framework;
  private final AgentResponseHandler responseHandler;

  public AiAgentRequestHandlerImpl(
      AgentInitializer agentInitializer,
      AgentMessagesHandler agentMessagesHandler,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      AiFrameworkAdapter<?> framework,
      AgentResponseHandler responseHandler) {
    this.agentInitializer = agentInitializer;
    this.agentMessagesHandler = agentMessagesHandler;
    this.gatewayToolHandlers = gatewayToolHandlers;
    this.framework = framework;
    this.responseHandler = responseHandler;
  }

  @Override
  public AgentResponse handleRequest(OutboundConnectorContext context, AgentRequest request) {
    final var agentInitializationResult = agentInitializer.initializeAgent(context, request);
    if (agentInitializationResult.agentResponse() != null) {
      // return agent response if needed (e.g. tool discovery tool calls before calling the LLM)
      return agentInitializationResult.agentResponse();
    }

    AgentContext agentContext = agentInitializationResult.agentContext();

    // set up memory and load from context if available
    final var runtimeMemory =
        new MessageWindowRuntimeMemory(
            Optional.ofNullable(request.data().memory())
                .map(MemoryConfiguration::maxMessages)
                .orElse(DEFAULT_MAX_MEMORY_MESSAGES));

    final var conversationStore = new InProcessConversationStore();
    conversationStore.loadIntoRuntimeMemory(context, agentContext, runtimeMemory);

    // check configured limits
    checkLimits(request.data(), agentContext);

    // update memory with system message + new user messages/tool call responses
    agentMessagesHandler.addSystemMessage(
        agentContext, runtimeMemory, request.data().systemPrompt());
    agentMessagesHandler.addMessagesFromRequest(
        agentContext,
        runtimeMemory,
        request.data().userPrompt(),
        agentInitializationResult.toolCallResults());

    // call framework with memory
    AiFrameworkChatResponse<?> frameworkChatResponse =
        framework.executeChatRequest(request, agentContext, runtimeMemory);
    agentContext = frameworkChatResponse.agentContext();

    final var assistantMessage = frameworkChatResponse.assistantMessage();
    runtimeMemory.addMessage(assistantMessage);

    // apply potential gateway tool call transformations & map tool call to process variable format
    var toolCalls =
        gatewayToolHandlers.transformToolCalls(agentContext, assistantMessage.toolCalls());
    final var processVariableToolCalls =
        toolCalls.stream().map(ToolCallProcessVariable::from).toList();

    final var nextAgentState =
        toolCalls.isEmpty() ? AgentState.READY : AgentState.WAITING_FOR_TOOL_INPUT;

    // store memory to context and update the next agent state based on tool calls
    agentContext =
        conversationStore
            .storeFromRuntimeMemory(context, agentContext, runtimeMemory)
            .withState(nextAgentState);

    return responseHandler.createResponse(
        request, agentContext, assistantMessage, processVariableToolCalls);
  }

  private void checkLimits(AgentRequest.AgentRequestData requestData, AgentContext agentContext) {
    final int maxModelCalls =
        Optional.ofNullable(requestData.limits())
            .map(LimitsConfiguration::maxModelCalls)
            .orElse(DEFAULT_MAX_MODEL_CALLS);
    if (agentContext.metrics().modelCalls() >= maxModelCalls) {
      throw new ConnectorException(
          ERROR_CODE_MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED,
          "Maximum number of model calls reached (modelCalls: %d, limit: %d)"
              .formatted(agentContext.metrics().modelCalls(), maxModelCalls));
    }
  }
}
