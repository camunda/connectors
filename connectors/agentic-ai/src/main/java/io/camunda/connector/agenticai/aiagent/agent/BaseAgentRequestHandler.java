/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentContextInitializationResult;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentDiscoveryInProgressInitializationResult;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentResponseInitializationResult;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRequest;
import io.camunda.connector.agenticai.aiagent.memory.runtime.MessageWindowRuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.outbound.ConnectorResponse;
import io.camunda.connector.api.outbound.JobCompletionFailure;
import io.camunda.connector.api.outbound.JobCompletionListener;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

public abstract class BaseAgentRequestHandler<
        C extends AgentExecutionContext, R extends ConnectorResponse>
    implements AgentRequestHandler<C, R> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BaseAgentRequestHandler.class);

  private static final int DEFAULT_CONTEXT_WINDOW_SIZE = 20;

  private final AgentInitializer agentInitializer;
  private final ConversationStoreRegistry conversationStoreRegistry;
  private final AgentLimitsValidator limitsValidator;
  private final AgentMessagesHandler messagesHandler;
  private final GatewayToolHandlerRegistry gatewayToolHandlers;
  private final AiFrameworkAdapter<?> framework;
  private final AgentResponseHandler responseHandler;

  public BaseAgentRequestHandler(
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
  public R handleRequest(final C executionContext) {
    final var agentInitializationResult = agentInitializer.initializeAgent(executionContext);
    return switch (agentInitializationResult) {
      // directly return agent response if needed (e.g. tool discovery tool calls before calling the
      // LLM)
      case AgentResponseInitializationResult(AgentResponse agentResponse) -> {
        LOGGER.debug(
            "AI Agent initialization returned direct response including {} tool calls. Completing job without further processing.",
            agentResponse.toolCalls().size());
        yield buildConnectorResponse(executionContext, agentResponse, null);
      }

      // discovery still in progress (not all tool call results present)
      case AgentDiscoveryInProgressInitializationResult ignored -> {
        LOGGER.debug(
            "AI Agent initialization tool discovery is still in progress. Completing job without further processing.");
        yield buildConnectorResponse(executionContext, null, null);
      }

      case AgentContextInitializationResult(
              AgentContext agentContext,
              List<ToolCallResult> toolCallResults) -> {
        LOGGER.debug(
            "Handling agent request with {} tool call results",
            toolCallResults != null ? toolCallResults.size() : 0);
        yield handleRequest(executionContext, agentContext, toolCallResults);
      }
    };
  }

  private R handleRequest(
      final C executionContext,
      AgentContext agentContext,
      final List<ToolCallResult> toolCallResults) {
    final var store =
        conversationStoreRegistry.getConversationStore(executionContext, agentContext);

    try (var session = store.createSession(executionContext, agentContext)) {
      var agentResponse =
          processConversation(executionContext, agentContext, toolCallResults, session);

      LOGGER.debug(
          "Request processing completed {} agent response, completing job",
          agentResponse == null ? "without" : "with");

      var completionListener =
          createStoreCompletionListener(executionContext, store, agentResponse);
      return buildConnectorResponse(executionContext, agentResponse, completionListener);
    }
  }

  private AgentResponse processConversation(
      final C executionContext,
      AgentContext agentContext,
      final List<ToolCallResult> toolCallResults,
      final ConversationSession session) {
    // set up memory and load from context if available
    final var runtimeMemory =
        new MessageWindowRuntimeMemory(
            Optional.ofNullable(executionContext.memory())
                .map(MemoryConfiguration::contextWindowSize)
                .orElse(DEFAULT_CONTEXT_WINDOW_SIZE));

    LOGGER.trace("Loading previous conversation (if any) into runtime memory");
    var loadResult = session.loadMessages(agentContext);
    runtimeMemory.addMessages(loadResult.messages());

    // validate configured limits
    LOGGER.trace("Validating configured limits for agent execution");
    limitsValidator.validateConfiguredLimits(executionContext, agentContext);

    // update memory with system message
    LOGGER.trace("Adding system message (if necessary)");
    messagesHandler.addSystemMessage(
        executionContext, agentContext, runtimeMemory, executionContext.systemPrompt());

    // update memory with user messages/tool call responses
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Adding user messages including the following {} tool call results: {}",
          toolCallResults.size(),
          toolCallResults.stream().map(tcr -> Pair.of(tcr.id(), tcr.name())).toList());
    }

    final var userMessages =
        messagesHandler.addUserMessages(
            executionContext,
            agentContext,
            runtimeMemory,
            executionContext.userPrompt(),
            toolCallResults);

    // check if we're actually able to call the model, abort early otherwise
    if (!modelCallPrerequisitesFulfilled(executionContext, agentContext, userMessages)) {
      LOGGER.debug("Model call prerequisites not fulfilled, returning without agent response");
      return null;
    }

    handleAddedUserMessages(executionContext, agentContext, userMessages);

    // call framework with memory
    LOGGER.debug("Executing chat request with AI framework");
    final var frameworkChatResponse =
        framework.executeChatRequest(executionContext, agentContext, runtimeMemory);
    agentContext = frameworkChatResponse.agentContext();

    final var assistantMessage = frameworkChatResponse.assistantMessage();
    LOGGER.debug(
        "Received assistant message containing {} tool call requests",
        assistantMessage.toolCalls() != null ? assistantMessage.toolCalls().size() : 0);
    runtimeMemory.addMessage(assistantMessage);

    // apply potential gateway tool call transformations & map tool call to process
    // variable format
    final var toolCalls =
        gatewayToolHandlers.transformToolCalls(agentContext, assistantMessage.toolCalls());
    final var processVariableToolCalls =
        toolCalls.stream().map(ToolCallProcessVariable::from).toList();

    // store memory reference to context
    LOGGER.debug("Storing runtime memory to conversation session");
    var updatedConversation =
        session.storeMessages(
            agentContext, ConversationStoreRequest.of(runtimeMemory.allMessages()));
    agentContext = agentContext.withConversation(updatedConversation);

    return responseHandler.createResponse(
        executionContext, agentContext, assistantMessage, processVariableToolCalls);
  }

  protected abstract boolean modelCallPrerequisitesFulfilled(
      C executionContext, AgentContext agentContext, List<Message> addedUserMessages);

  protected void handleAddedUserMessages(
      C executionContext, AgentContext agentContext, List<Message> addedUserMessages) {
    // no-op by default
  }

  /**
   * Builds the connector response from the agent response. Agent response and listener may be null.
   */
  protected abstract R buildConnectorResponse(
      final C executionContext,
      @Nullable final AgentResponse agentResponse,
      @Nullable final JobCompletionListener completionListener);

  private static <C extends AgentExecutionContext>
      JobCompletionListener createStoreCompletionListener(
          C executionContext, ConversationStore store, @Nullable AgentResponse agentResponse) {
    if (agentResponse == null) {
      return null;
    }
    var context = agentResponse.context();
    return new JobCompletionListener() {
      @Override
      public void onJobCompleted() {
        store.onJobCompleted(executionContext, context);
      }

      @Override
      public void onJobCompletionFailed(JobCompletionFailure failure) {
        store.onJobCompletionFailed(executionContext, context, failure);
      }
    };
  }
}
