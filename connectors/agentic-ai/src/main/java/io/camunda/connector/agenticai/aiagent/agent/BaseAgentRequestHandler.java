/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.agent.AgentJobCompletionListener.compose;

import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentContextInitializationResult;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentDiscoveryInProgressInitializationResult;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.AgentResponseInitializationResult;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceUpdateRequest;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkChatResponse;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRequest;
import io.camunda.connector.agenticai.aiagent.memory.runtime.MessageWindowRuntimeMemory;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.outbound.ConnectorResponse;
import io.camunda.connector.api.outbound.JobCompletionFailure;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private final AgentInstanceClient agentInstanceClient;

  public BaseAgentRequestHandler(
      AgentInitializer agentInitializer,
      ConversationStoreRegistry conversationStoreRegistry,
      AgentLimitsValidator limitsValidator,
      AgentMessagesHandler messagesHandler,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      AiFrameworkAdapter<?> framework,
      AgentResponseHandler responseHandler,
      AgentInstanceClient agentInstanceClient) {
    this.agentInitializer = agentInitializer;
    this.conversationStoreRegistry = conversationStoreRegistry;
    this.limitsValidator = limitsValidator;
    this.messagesHandler = messagesHandler;
    this.gatewayToolHandlers = gatewayToolHandlers;
    this.framework = framework;
    this.responseHandler = responseHandler;
    this.agentInstanceClient = agentInstanceClient;
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
    final var initialMetrics = agentContext.metrics();

    try (var session = store.createSession(executionContext, agentContext)) {
      var agentResponse =
          processConversation(executionContext, agentContext, toolCallResults, session);

      LOGGER.debug(
          "Request processing completed {} agent response, completing job",
          agentResponse == null ? "without" : "with");

      final int toolCallsDelta =
          agentResponse != null
              ? agentResponse.context().metrics().toolCalls() - initialMetrics.toolCalls()
              : 0;
      return buildConnectorResponse(
          executionContext,
          agentResponse,
          compose(
              createStoreCompletionListener(executionContext, store, agentResponse),
              createToolCallsCompletionListener(executionContext, agentResponse, toolCallsDelta)));
    }
  }

  private AgentResponse processConversation(
      final C executionContext,
      AgentContext agentContext,
      final List<ToolCallResult> toolCallResults,
      final ConversationSession session) {
    final var runtimeMemory = initializeRuntimeMemory(executionContext, agentContext, session);

    LOGGER.trace("Validating configured limits for agent execution");
    limitsValidator.validateConfiguredLimits(executionContext, agentContext);

    final var addedUserMessages =
        prepareMessages(executionContext, agentContext, runtimeMemory, toolCallResults);

    if (!modelCallPrerequisitesFulfilled(executionContext, agentContext, addedUserMessages)) {
      LOGGER.debug("Model call prerequisites not fulfilled, returning without agent response");
      return null;
    }
    handleAddedUserMessages(executionContext, agentContext, addedUserMessages);

    final var preChatMetrics = notifyThinking(executionContext, agentContext);
    LOGGER.debug("Executing chat request with AI framework");
    final var chatResponse =
        framework.executeChatRequest(executionContext, agentContext, runtimeMemory);

    agentContext =
        updateAgentInstanceMetricsAndStatus(
            executionContext,
            updateContextMetrics(chatResponse.agentContext(), chatResponse),
            preChatMetrics);

    return buildResponse(executionContext, agentContext, chatResponse, session, runtimeMemory);
  }

  private RuntimeMemory initializeRuntimeMemory(
      C executionContext, AgentContext agentContext, ConversationSession session) {
    LOGGER.trace("Loading previous conversation (if any) into runtime memory");
    final var runtimeMemory =
        new MessageWindowRuntimeMemory(
            Optional.ofNullable(executionContext.memory())
                .map(MemoryConfiguration::contextWindowSize)
                .orElse(DEFAULT_CONTEXT_WINDOW_SIZE));
    runtimeMemory.addMessages(session.loadMessages(agentContext).messages());
    return runtimeMemory;
  }

  private List<Message> prepareMessages(
      C executionContext,
      AgentContext agentContext,
      RuntimeMemory runtimeMemory,
      List<ToolCallResult> toolCallResults) {
    LOGGER.trace("Adding system message (if necessary)");
    messagesHandler.addSystemMessage(
        executionContext, agentContext, runtimeMemory, executionContext.systemPrompt());

    logToolCallResults(toolCallResults);
    return messagesHandler.addUserMessages(
        executionContext,
        agentContext,
        runtimeMemory,
        executionContext.userPrompt(),
        toolCallResults);
  }

  private AgentContext updateContextMetrics(
      AgentContext agentContext, AiFrameworkChatResponse<?> chatResponse) {
    final var assistantToolCalls = chatResponse.assistantMessage().toolCalls();
    final int toolCallsDelta = Optional.ofNullable(assistantToolCalls).map(List::size).orElse(0);

    if (toolCallsDelta == 0) {
      return agentContext;
    }

    return agentContext.withMetrics(agentContext.metrics().incrementToolCalls(toolCallsDelta));
  }

  private AgentMetrics notifyThinking(C executionContext, AgentContext agentContext) {
    LOGGER.debug("Notifying agent instance: status=THINKING before LLM call");
    final var snapshot = agentContext.metrics();
    agentInstanceClient.update(
        executionContext,
        agentContext,
        AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING));
    return snapshot;
  }

  private AgentContext updateAgentInstanceMetricsAndStatus(
      C executionContext, AgentContext agentContext, AgentMetrics preChatMetrics) {
    final var metricsDelta = agentContext.metrics().minus(preChatMetrics);
    final var nextStatus = nextAgentInstanceState(metricsDelta.toolCalls());

    logAgentInstancePostLlmUpdate(nextStatus, metricsDelta);

    // Exclude toolCalls: tool calls are reported on job completion
    // so superseded/failed jobs don't inflate the count.
    agentInstanceClient.update(
        executionContext,
        agentContext,
        AgentInstanceUpdateRequest.builder()
            .status(nextStatus)
            .delta(metricsDelta.withToolCalls(0))
            .build());

    return agentContext;
  }

  private AgentInstanceUpdateStatus nextAgentInstanceState(int toolCallsDelta) {
    return toolCallsDelta == 0
        ? AgentInstanceUpdateStatus.IDLE
        : AgentInstanceUpdateStatus.TOOL_CALLING;
  }

  private AgentResponse buildResponse(
      C executionContext,
      AgentContext agentContext,
      AiFrameworkChatResponse<?> chatResponse,
      ConversationSession session,
      RuntimeMemory runtimeMemory) {
    final var assistantMessage = chatResponse.assistantMessage();
    LOGGER.debug(
        "Received assistant message containing {} tool call requests",
        assistantMessage.toolCalls() != null ? assistantMessage.toolCalls().size() : 0);
    runtimeMemory.addMessage(assistantMessage);

    final var toolCalls =
        gatewayToolHandlers.transformToolCalls(agentContext, assistantMessage.toolCalls());
    final var processVariableToolCalls =
        toolCalls.stream().map(ToolCallProcessVariable::from).toList();

    LOGGER.debug("Storing runtime memory to conversation session");
    final var updatedConversation =
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
      final AgentResponse agentResponse,
      final AgentJobCompletionListener completionListener);

  private void logToolCallResults(List<ToolCallResult> toolCallResults) {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Adding user messages including the following {} tool call results: {}",
          toolCallResults.size(),
          toolCallResults.stream().map(tcr -> Pair.of(tcr.id(), tcr.name())).toList());
    }
  }

  private void logAgentInstancePostLlmUpdate(
      AgentInstanceUpdateStatus nextStatus, AgentMetrics metricsDelta) {
    if (!LOGGER.isDebugEnabled()) {
      return;
    }
    LOGGER.debug(
        "Updating agent instance after LLM response: status={}, modelCalls=+{}, inputTokens=+{}, outputTokens=+{}",
        nextStatus,
        metricsDelta.modelCalls(),
        metricsDelta.tokenUsage().inputTokenCount(),
        metricsDelta.tokenUsage().outputTokenCount());
    if (metricsDelta.toolCalls() > 0) {
      LOGGER.debug(
          "{} tool call(s) will be reported to agent instance on job completion",
          metricsDelta.toolCalls());
    }
  }

  private AgentJobCompletionListener createToolCallsCompletionListener(
      C executionContext, AgentResponse agentResponse, int toolCallsDelta) {
    if (agentResponse == null || toolCallsDelta <= 0) {
      return null;
    }
    final var agentContext = agentResponse.context();
    return new AgentJobCompletionListener() {
      @Override
      public void onJobCompleted() {
        try {
          LOGGER.debug(
              "Reporting {} deferred tool call(s) to agent instance on job completion",
              toolCallsDelta);
          agentInstanceClient.update(
              executionContext,
              agentContext,
              AgentInstanceUpdateRequest.builder()
                  .delta(AgentMetrics.empty().incrementToolCalls(toolCallsDelta))
                  .build());
        } catch (Exception e) {
          LOGGER.error(
              "Failed to update tool call metrics after job completion; metrics may be inaccurate",
              e);
        }
      }

      @Override
      public void onJobCompletionFailed(JobCompletionFailure failure) {
        LOGGER.warn(
            "Job completion failed ({}), skipping tool call metrics update for {} tool call(s)",
            failure.getClass().getSimpleName(),
            toolCallsDelta);
      }
    };
  }

  private static <C extends AgentExecutionContext>
      AgentJobCompletionListener createStoreCompletionListener(
          C executionContext, ConversationStore store, @Nullable AgentResponse agentResponse) {
    if (agentResponse == null) {
      return null;
    }
    var context = agentResponse.context();
    return new AgentJobCompletionListener() {
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
