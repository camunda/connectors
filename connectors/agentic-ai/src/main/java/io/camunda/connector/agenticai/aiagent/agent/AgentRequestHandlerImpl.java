/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentContextInitializationResult;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentResponseInitializationResult;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkChatResponse;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.runtime.MessageWindowRuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.List;
import java.util.Optional;

public class AgentRequestHandlerImpl implements AgentRequestHandler {

  private static final int DEFAULT_CONTEXT_WINDOW_SIZE = 20;

  private final AgentInitializer agentInitializer;
  private final ConversationStoreRegistry conversationStoreRegistry;
  private final AgentLimitsValidator limitsValidator;
  private final AgentMessagesHandler messagesHandler;
  private final GatewayToolHandlerRegistry gatewayToolHandlers;
  private final AiFrameworkAdapter<?> framework;
  private final AgentResponseHandler responseHandler;

  public AgentRequestHandlerImpl(
      AgentInitializer agentInitializer,
      ConversationStoreRegistry conversationStoreRegistry,
      AgentLimitsValidator limitsValidator,
      AgentMessagesHandler messagesHandler,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      AiFrameworkAdapter<?> framework,
      AgentResponseHandler responseHandler) {
    this.agentInitializer = agentInitializer;
    this.conversationStoreRegistry = conversationStoreRegistry;
    this.limitsValidator = limitsValidator;
    this.messagesHandler = messagesHandler;
    this.gatewayToolHandlers = gatewayToolHandlers;
    this.framework = framework;
    this.responseHandler = responseHandler;
  }

  @Override
  public AgentResponse handleRequest(AgentExecutionContext executionContext) {
    final var agentInitializationResult = agentInitializer.initializeAgent(executionContext);
    return switch (agentInitializationResult) {
      // directly return agent response if needed (e.g. tool discovery tool calls before calling the
      // LLM)
      case AgentResponseInitializationResult(AgentResponse agentResponse) -> agentResponse;

      case AgentContextInitializationResult(
              AgentContext agentContext,
              List<ToolCallResult> toolCallResults) ->
          handleRequest(executionContext, agentContext, toolCallResults);
    };
  }

  private AgentResponse handleRequest(
      final AgentExecutionContext executionContext,
      final AgentContext agentContext,
      final List<ToolCallResult> toolCallResults) {
    final var conversationStore =
        conversationStoreRegistry.getConversationStore(executionContext, agentContext);
    return conversationStore.executeInSession(
        executionContext,
        agentContext,
        session -> handleRequest(executionContext, agentContext, toolCallResults, session));
  }

  private AgentResponse handleRequest(
      AgentExecutionContext executionContext,
      AgentContext agentContext,
      List<ToolCallResult> toolCallResults,
      ConversationSession session) {
    final var requestData = executionContext.request().data();

    // set up memory and load from context if available
    final var runtimeMemory =
        new MessageWindowRuntimeMemory(
            Optional.ofNullable(requestData.memory())
                .map(MemoryConfiguration::contextWindowSize)
                .orElse(DEFAULT_CONTEXT_WINDOW_SIZE));

    session.loadIntoRuntimeMemory(agentContext, runtimeMemory);

    // validate configured limits
    limitsValidator.validateConfiguredLimits(executionContext, agentContext);

    // update memory with system message + new user messages/tool call responses
    messagesHandler.addSystemMessage(agentContext, runtimeMemory, requestData.systemPrompt());
    messagesHandler.addMessagesFromRequest(
        agentContext, runtimeMemory, requestData.userPrompt(), toolCallResults);

    // call framework with memory
    AiFrameworkChatResponse<?> frameworkChatResponse =
        framework.executeChatRequest(executionContext, agentContext, runtimeMemory);
    agentContext = frameworkChatResponse.agentContext();

    final var assistantMessage = frameworkChatResponse.assistantMessage();
    runtimeMemory.addMessage(assistantMessage);

    // apply potential gateway tool call transformations & map tool call to process
    // variable format
    var toolCalls =
        gatewayToolHandlers.transformToolCalls(agentContext, assistantMessage.toolCalls());
    final var processVariableToolCalls =
        toolCalls.stream().map(ToolCallProcessVariable::from).toList();

    final var nextAgentState =
        toolCalls.isEmpty() ? AgentState.READY : AgentState.WAITING_FOR_TOOL_INPUT;

    // store memory to context and update the next agent state based on tool calls
    agentContext =
        session.storeFromRuntimeMemory(agentContext, runtimeMemory).withState(nextAgentState);

    return responseHandler.createResponse(
        executionContext, agentContext, assistantMessage, processVariableToolCalls);
  }
}
