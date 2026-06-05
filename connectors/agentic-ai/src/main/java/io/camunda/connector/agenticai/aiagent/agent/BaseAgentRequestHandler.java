/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.DeferConversation;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.DiscoverTools;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializationResult.ReadyToConverse;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceUpdateRequest;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRequest;
import io.camunda.connector.agenticai.aiagent.memory.runtime.SlidingMessagesWindow;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.outbound.ConnectorResponse;
import io.camunda.connector.api.outbound.JobCompletionFailure;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseAgentRequestHandler<
        C extends AgentExecutionContext, R extends ConnectorResponse>
    implements AgentRequestHandler<C, R> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BaseAgentRequestHandler.class);

  private final AgentInitializer agentInitializer;
  private final ConversationStoreRegistry conversationStoreRegistry;
  private final AgentLimitsValidator limitsValidator;
  private final ConversationMessageComposer messageComposer;
  private final GatewayToolHandlerRegistry gatewayToolHandlers;
  private final AiFrameworkAdapter<?> framework;
  private final AgentResponseHandler responseHandler;
  private final AgentInstanceClient agentInstanceClient;

  public BaseAgentRequestHandler(
      AgentInitializer agentInitializer,
      ConversationStoreRegistry conversationStoreRegistry,
      AgentLimitsValidator limitsValidator,
      ConversationMessageComposer messageComposer,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      AiFrameworkAdapter<?> framework,
      AgentResponseHandler responseHandler,
      AgentInstanceClient agentInstanceClient) {
    this.agentInitializer = agentInitializer;
    this.conversationStoreRegistry = conversationStoreRegistry;
    this.limitsValidator = limitsValidator;
    this.messageComposer = messageComposer;
    this.gatewayToolHandlers = gatewayToolHandlers;
    this.framework = framework;
    this.responseHandler = responseHandler;
    this.agentInstanceClient = agentInstanceClient;
  }

  @Override
  public R handleRequest(final C executionContext) {
    return switch (agentInitializer.initializeAgent(executionContext)) {
      case ReadyToConverse(var agentContext, var engineToolCallResults) -> {
        LOGGER.debug(
            "Handling agent request with {} tool call results",
            engineToolCallResults != null ? engineToolCallResults.size() : 0);
        yield converse(executionContext, agentContext, engineToolCallResults);
      }
      case DiscoverTools(var agentContext, var toolDiscoveryToolCalls) -> {
        LOGGER.debug(
            "AI Agent initialization dispatching {} gateway tool discovery calls. Completing job without further processing.",
            toolDiscoveryToolCalls.size());
        yield dispatchToolDiscovery(executionContext, agentContext, toolDiscoveryToolCalls);
      }
      case DeferConversation() -> {
        LOGGER.debug(
            "AI Agent initialization tool discovery is still in progress. Completing job without further processing.");
        yield buildConnectorResponse(executionContext, null, null);
      }
    };
  }

  private R converse(
      final C executionContext,
      AgentContext agentContext,
      final List<ToolCallResult> engineToolCallResults) {
    final var store =
        conversationStoreRegistry.getConversationStore(executionContext, agentContext);
    final var initialMetrics = agentContext.metrics();

    try (var session = store.createSession(executionContext, agentContext)) {
      LOGGER.trace("Loading previous conversation (if any) into conversation");
      var conversation =
          AgentConversation.start(
              agentContext, session.loadMessages(agentContext).messages(), engineToolCallResults);

      var agentResponse = processConversation(executionContext, conversation, session);

      LOGGER.debug(
          "Request processing completed {} agent response, completing job",
          agentResponse == null ? "without" : "with");

      AgentJobCompletionListener metricsListener = null;
      if (agentResponse != null) {
        final var metricsDelta = agentResponse.context().metrics().minus(initialMetrics);
        final var nextStatus = nextAgentInstanceState(metricsDelta.toolCalls());
        if (shouldUpdateAgentInstanceBeforeJobCompletion(agentResponse)) {
          notifyMetrics(executionContext, agentResponse.context(), metricsDelta, nextStatus, true);
        } else {
          metricsListener =
              createMetricsCompletionListener(
                  executionContext, agentResponse.context(), metricsDelta, nextStatus);
        }
      }

      return buildConnectorResponse(
          executionContext,
          agentResponse,
          AgentJobCompletionListener.compose(
              metricsListener,
              createStoreCompletionListener(executionContext, store, agentResponse)));
    }
  }

  private AgentResponse processConversation(
      final C executionContext, AgentConversation rehydratedConversation, final ConversationSession session) {
    LOGGER.trace("Validating configured limits for agent execution");
    limitsValidator.validateConfiguredLimits(executionContext, rehydratedConversation.context());

    final var conversation = messageComposer.compose(executionContext, rehydratedConversation);

    if (!modelCallPrerequisitesFulfilled(
        executionContext, conversation.context(), conversation.addedMessages())) {
      LOGGER.debug("Model call prerequisites not fulfilled, returning without agent response");
      return null;
    }
    reactToInterruptedToolCalls(executionContext, conversation);

    notifyThinking(executionContext, conversation);
    LOGGER.debug("Executing chat request with AI framework");
    final var snapshot = conversation.window(SlidingMessagesWindow.of(executionContext.memory()));
    final var chatResponse =
        framework.executeChatRequest(executionContext, conversation.context(), snapshot);

    final var updatedConversation = conversation.ingest(chatResponse.assistantMessage(), chatResponse.tokenUsage());

    return buildResponse(executionContext, updatedConversation, session);
  }

  private void notifyThinking(C executionContext, AgentConversation conversation) {
    LOGGER.debug("Notifying agent instance: status=THINKING before LLM call");
    agentInstanceClient.update(
        executionContext,
        conversation.context(),
        AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING));
  }

  private AgentInstanceUpdateStatus nextAgentInstanceState(int toolCallsDelta) {
    return toolCallsDelta == 0
        ? AgentInstanceUpdateStatus.IDLE
        : AgentInstanceUpdateStatus.TOOL_CALLING;
  }

  private AgentResponse buildResponse(
      C executionContext, AgentConversation conversation, ConversationSession session) {
    final var assistantMessage = (AssistantMessage) conversation.messages().getLast();
    LOGGER.debug(
        "Received assistant message containing {} tool call requests",
        assistantMessage.toolCalls() != null ? assistantMessage.toolCalls().size() : 0);

    final var toolCalls =
        gatewayToolHandlers.transformToolCalls(
            conversation.context(), assistantMessage.toolCalls());
    final var processVariableToolCalls =
        toolCalls.stream().map(ToolCallProcessVariable::from).toList();

    LOGGER.debug("Storing conversation messages to session");
    final var storedConversation =
        session.storeMessages(
            conversation.context(), ConversationStoreRequest.of(conversation.messages()));
    conversation =
        conversation.withContext(conversation.context().withConversation(storedConversation));

    return responseHandler.createResponse(
        executionContext, conversation.context(), assistantMessage, processVariableToolCalls);
  }

  private R dispatchToolDiscovery(
      C executionContext, AgentContext agentContext, List<ToolCall> discoveryToolCalls) {
    var response =
        AgentResponse.builder()
            .context(agentContext)
            .toolCalls(discoveryToolCalls.stream().map(ToolCallProcessVariable::from).toList())
            .build();
    var listener = createToolDiscoveryCompletionListener(executionContext, agentContext);
    return buildConnectorResponse(executionContext, response, listener);
  }

  private AgentJobCompletionListener createToolDiscoveryCompletionListener(
      C executionContext, AgentContext agentContext) {
    return new AgentJobCompletionListener() {
      @Override
      public void onJobCompleted() {
        try {
          agentInstanceClient.update(
              executionContext,
              agentContext,
              AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.TOOL_DISCOVERY));
        } catch (Exception e) {
          LOGGER.error(
              "Failed to update agent instance status to TOOL_DISCOVERY after job completion", e);
        }
      }

      @Override
      public void onJobCompletionFailed(JobCompletionFailure failure) {
        LOGGER.debug(
            "Job completion failed ({}), skipping TOOL_DISCOVERY status update",
            failure.getClass().getSimpleName());
      }
    };
  }

  protected abstract boolean modelCallPrerequisitesFulfilled(
      C executionContext, AgentContext agentContext, List<Message> addedMessages);

  /**
   * Returns {@code true} when the agent-instance PATCH must be sent synchronously before the job
   * completion command is issued. Returning {@code false} defers the PATCH to {@link
   * AgentJobCompletionListener#onJobCompleted()}, which is safe as long as the element instance
   * survives job completion (e.g. an AHSP intermediate turn where tool elements are activated and
   * the subprocess stays open).
   */
  protected abstract boolean shouldUpdateAgentInstanceBeforeJobCompletion(
      AgentResponse agentResponse);

  protected void reactToInterruptedToolCalls(C executionContext, AgentConversation conversation) {
    // no-op by default
  }

  /**
   * Builds the connector response from the agent response. Agent response and listener may be null.
   */
  protected abstract R buildConnectorResponse(
      final C executionContext,
      @Nullable final AgentResponse agentResponse,
      @Nullable final AgentJobCompletionListener completionListener);

  private void notifyMetrics(
      C executionContext,
      AgentContext agentContext,
      AgentMetrics metricsDelta,
      @Nullable AgentInstanceUpdateStatus nextStatus,
      boolean rethrowOnFailure) {
    try {
      LOGGER.debug(
          "Updating agent instance metrics: status={}, modelCalls=+{}, inputTokens=+{}, outputTokens=+{}, toolCalls=+{}",
          nextStatus,
          metricsDelta.modelCalls(),
          metricsDelta.tokenUsage().inputTokenCount(),
          metricsDelta.tokenUsage().outputTokenCount(),
          metricsDelta.toolCalls());
      agentInstanceClient.update(
          executionContext,
          agentContext,
          AgentInstanceUpdateRequest.builder().status(nextStatus).delta(metricsDelta).build());
    } catch (Exception e) {
      LOGGER.error("Failed to update agent instance metrics; metrics may be inaccurate", e);
      if (rethrowOnFailure) {
        throw e;
      }
    }
  }

  private AgentJobCompletionListener createMetricsCompletionListener(
      C executionContext,
      AgentContext agentContext,
      AgentMetrics metricsDelta,
      AgentInstanceUpdateStatus nextStatus) {
    return new AgentJobCompletionListener() {
      @Override
      public void onJobCompleted() {
        notifyMetrics(executionContext, agentContext, metricsDelta, nextStatus, false);
      }

      @Override
      public void onJobCompletionFailed(JobCompletionFailure failure) {
        final var strippedDelta = metricsDelta.withToolCalls(0);
        if (failure instanceof JobCompletionFailure.CommandFailure.CommandIgnored) {
          notifyMetrics(executionContext, agentContext, strippedDelta, null, false);
        } else {
          notifyMetrics(
              executionContext, agentContext, strippedDelta, AgentInstanceUpdateStatus.IDLE, false);
        }
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
