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
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceKey;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModelRejectedException;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ContentFilteredException;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ContextWindowExceededException;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.GuardrailInterventionException;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationStoreRequest;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentFlavor;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.AgentJobKind;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.JobDeferred;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.JobFailed;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ModelRoundCompleted;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ModelRoundFailed;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ModelRoundStarted;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ToolDiscoveryDispatched;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.ToolExecution;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.TurnCompleted;
import io.camunda.connector.agenticai.aiagent.api.lifecycle.TurnStarted;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRegistry;
import io.camunda.connector.agenticai.aiagent.conversation.AgentConversation;
import io.camunda.connector.agenticai.aiagent.conversation.AgentInput;
import io.camunda.connector.agenticai.aiagent.conversation.PreviousConversation;
import io.camunda.connector.agenticai.aiagent.conversation.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.lifecycle.AgentJobLifecycle;
import io.camunda.connector.agenticai.aiagent.lifecycle.AgentLifecycleDispatcher;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.agenticai.aiagent.tool.ToolCallProcessVariable;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.ConnectorResponse;
import io.camunda.connector.api.outbound.JobCompletionFailure;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseAgentRequestHandler<
        C extends AgentExecutionContext, R extends ConnectorResponse>
    implements AgentRequestHandler<C, R> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BaseAgentRequestHandler.class);

  private final AgentInitializer agentInitializer;
  private final ConversationStoreRegistry conversationStoreRegistry;
  private final AgentConversationTurnInputComposer agentInputComposer;
  private final ChatModelRegistry chatModelRegistry;
  private final SystemPromptComposer systemPromptComposer;
  private final AgentResponseHandler responseHandler;
  private final AgentInstanceClient agentInstanceClient;
  private final AgentLifecycleDispatcher lifecycleDispatcher;

  public BaseAgentRequestHandler(
      AgentInitializer agentInitializer,
      ConversationStoreRegistry conversationStoreRegistry,
      AgentConversationTurnInputComposer agentInputComposer,
      ChatModelRegistry chatModelRegistry,
      SystemPromptComposer systemPromptComposer,
      AgentResponseHandler responseHandler,
      AgentInstanceClient agentInstanceClient,
      AgentLifecycleDispatcher lifecycleDispatcher) {
    this.agentInitializer = agentInitializer;
    this.conversationStoreRegistry = conversationStoreRegistry;
    this.agentInputComposer = agentInputComposer;
    this.chatModelRegistry = chatModelRegistry;
    this.systemPromptComposer = systemPromptComposer;
    this.responseHandler = responseHandler;
    this.agentInstanceClient = agentInstanceClient;
    this.lifecycleDispatcher = lifecycleDispatcher;
  }

  @Override
  public R handleRequest(final C executionContext) {
    final var startedAt = Instant.now();
    return switch (agentInitializer.initializeAgent(executionContext)) {
      case DiscoverTools(var agentContext, var toolDiscoveryToolCalls) -> {
        LOGGER.debug(
            "AI Agent initialization dispatching {} gateway tool discovery calls. Completing job without further processing.",
            toolDiscoveryToolCalls.size());
        final var lifecycle =
            startLifecycle(executionContext, AgentJobKind.TOOL_DISCOVERY, agentContext, startedAt);
        yield failFast(
            lifecycle,
            () ->
                dispatchToolDiscovery(
                    executionContext, agentContext, toolDiscoveryToolCalls, lifecycle));
      }
      case DeferConversation() -> {
        LOGGER.debug(
            "AI Agent initialization tool discovery is still in progress. Completing job without further processing.");
        yield handleNoOp(executionContext);
      }
      case ReadyToConverse(var agentContext, var toolCallResults) -> {
        LOGGER.debug("Handling agent request with {} tool call results", toolCallResults.size());
        final var lifecycle =
            startLifecycle(executionContext, AgentJobKind.CONVERSATION, agentContext, startedAt);
        yield failFast(
            lifecycle, () -> converse(executionContext, agentContext, toolCallResults, lifecycle));
      }
    };
  }

  /** The flavor of the connector that this handler runs, reported to lifecycle listeners. */
  protected abstract AgentFlavor flavor();

  private AgentJobLifecycle startLifecycle(
      C executionContext, AgentJobKind kind, AgentContext agentContext, Instant startedAt) {
    return lifecycleDispatcher.startJob(
        flavor(), kind, executionContext.jobContext(), agentContext, startedAt);
  }

  /** Runs the agent work and reports a failure to the lifecycle listeners before rethrowing it. */
  private R failFast(AgentJobLifecycle lifecycle, Supplier<R> work) {
    try {
      return work.get();
    } catch (RuntimeException e) {
      lifecycle.jobFailed(new JobFailed(e, errorCodeOf(e)));
      throw e;
    }
  }

  private static @Nullable String errorCodeOf(Throwable e) {
    return e instanceof ConnectorException connectorException
        ? connectorException.getErrorCode()
        : null;
  }

  private R converse(
      final C executionContext,
      final AgentContext agentContext,
      final List<ToolCallResult> toolCallResults,
      final AgentJobLifecycle lifecycle) {
    final var store =
        conversationStoreRegistry.getConversationStore(executionContext, agentContext);

    try (var session = store.createSession(executionContext, agentContext)) {
      // AgentConfiguration#toolDefinitions() becomes the authoritative current tool list for the
      // rest of this invocation once populated here from the durable AgentContext.
      final var configuration =
          executionContext.configuration().withToolDefinitions(agentContext.toolDefinitions());
      final var agentInput = AgentInput.from(configuration.userPrompt(), toolCallResults);

      LOGGER.trace("Loading previous conversation (if any) for rehydration");
      final var loadedMessages = session.loadMessages(agentContext).messages();
      final var previousConversation =
          TurnReconstructor.reconstruct(loadedMessages, agentContext.metadata());

      LOGGER.trace("Composing turn input from history and invocation state");
      final var compositionResult =
          agentInputComposer.compose(configuration, agentContext, previousConversation, agentInput);
      return switch (compositionResult) {
        case CompositionResult.Deferred(var arrivedResults) -> {
          LOGGER.debug("No input ready to add, completing job without agent response");
          reportArrivedToolCallResults(
              executionContext, agentContext, arrivedResults, previousConversation);
          lifecycle.jobDeferred(new JobDeferred(arrivedResults));
          yield handleNoOp(executionContext);
        }
        case CompositionResult.NoInput ignored -> {
          LOGGER.debug("No input could be composed for this turn");
          yield handleNoInput(executionContext);
        }
        case CompositionResult.NextTurn(var newMessages) ->
            proceed(
                executionContext,
                agentContext,
                configuration,
                previousConversation,
                newMessages,
                session,
                store,
                lifecycle);
      };
    }
  }

  /**
   * Reports {@code arrivedResults} to agent instance history. No-ops when empty or when there is no
   * previous turn to correlate against.
   */
  private void reportArrivedToolCallResults(
      C executionContext,
      AgentContext agentContext,
      List<ToolCallResult> arrivedResults,
      PreviousConversation previousConversation) {
    if (arrivedResults.isEmpty() || previousConversation.turns().isEmpty()) {
      return;
    }
    agentInstanceClient.applyToolCallResults(
        executionContext,
        AgentInstanceKey.from(agentContext.metadata()),
        arrivedResults,
        previousConversation.turns().getLast());
  }

  private R proceed(
      final C executionContext,
      final AgentContext agentContext,
      final AgentConfiguration agentConfiguration,
      final PreviousConversation previousConversation,
      final List<Message> inputMessages,
      final ConversationSession session,
      final ConversationStore store,
      final AgentJobLifecycle lifecycle) {
    var systemMessage = createSystemMessage(executionContext, agentContext);
    final var conversation =
        AgentConversation.rehydrate(
            agentConfiguration, agentContext, previousConversation, systemMessage, inputMessages);

    lifecycle.turnStarted(turnStarted(conversation, agentConfiguration));
    throwIfLimitsReached(conversation, agentConfiguration);

    final var agentInstanceKey = conversation.agentInstanceKey();
    // called before ingest, so the current turn is still pending and lastTurn() is the turn
    // preceding it — i.e. the one whose tool calls originated the current turn's tool results.
    // Non-tool-result input items (e.g. the user message) are stamped with this turn-ingestion
    // timestamp; tool-result items use their own resolved completedAt instead (ADR 008).
    agentInstanceClient.applyTurnStart(
        executionContext,
        agentConfiguration,
        agentInstanceKey,
        conversation.currentTurn(),
        conversation.lastTurn(),
        OffsetDateTime.now());

    final AgentConversation updatedConversation;
    try (final var chatModel = chatModelRegistry.resolve(agentConfiguration.chatModel())) {
      updatedConversation =
          driveContinuationLoop(
              executionContext,
              agentConfiguration,
              conversation,
              chatModel,
              agentInstanceKey,
              lifecycle);
    }

    LOGGER.debug("Storing conversation messages to session");
    final var storedRef =
        session.storeMessages(
            updatedConversation.toAgentContext(),
            ConversationStoreRequest.of(updatedConversation.allMessages()));

    final var storedConversation = updatedConversation.withStoredConversation(storedRef);
    final var createdResponse = responseHandler.createResponse(storedConversation);
    final var completedTurn = storedConversation.currentTurn();
    final var agentResponse =
        createdResponse.withContext(
            lifecycle.turnCompleted(
                createdResponse.context(),
                new TurnCompleted(
                    completedTurn.iterationKey(),
                    completedTurn.toolCalls(),
                    completedTurn.assistantMessage(),
                    storedConversation.currentTurnMetrics())));

    LOGGER.debug("Request processing completed with agent response, completing job");

    final var messageStorageCompletionListener =
        createStoreCompletionListener(executionContext, store, agentResponse);
    return buildConnectorResponse(
        executionContext,
        storedConversation,
        agentResponse,
        withLifecycle(messageStorageCompletionListener, lifecycle));
  }

  /**
   * Drives the given chat model until it returns a {@code Completed} result, ingesting each round
   * (including intermediate {@code Continuation} rounds) as its own turn.
   */
  private AgentConversation driveContinuationLoop(
      C executionContext,
      AgentConfiguration agentConfiguration,
      AgentConversation conversation,
      ChatModel chatModel,
      @Nullable AgentInstanceKey agentInstanceKey,
      AgentJobLifecycle lifecycle) {
    var workingConversation = conversation;
    boolean continued;
    int round = 0;
    do {
      round++;
      LOGGER.debug(
          "Sending turn (iterationKey={}) to the model",
          workingConversation.currentTurn().iterationKey());

      final var windowedSnapshot =
          workingConversation.window(agentConfiguration.contextWindowSize());
      final var chatResult =
          executeChatModelRound(
              chatModel, executionContext, agentConfiguration, windowedSnapshot, lifecycle, round);

      workingConversation =
          workingConversation.ingest(chatResult.assistantMessage(), chatResult.metrics());

      continued = chatResult instanceof ChatResult.Continuation;
      // Exactly one status per round (engine constraint): a continuation round re-asserts THINKING
      // alongside its assistant item, the final round sends its terminal status instead.
      final var status =
          continued
              ? AgentInstanceUpdateStatus.THINKING
              : nextAgentInstanceState(workingConversation.currentTurnMetrics().toolCalls());
      agentInstanceClient.applyTurnCompletion(
          executionContext,
          agentInstanceKey,
          workingConversation.currentTurn(),
          OffsetDateTime.now(),
          status);

      if (continued) {
        LOGGER.debug(
            "Provider requested continuation (iterationKey={}); resuming with another round",
            workingConversation.currentTurn().iterationKey());

        throwIfLimitsReached(workingConversation, agentConfiguration);
        workingConversation = workingConversation.nextContinuationRound();
      }
    } while (continued);
    return workingConversation;
  }

  /**
   * Wraps {@link #executeChatModel} in a lifecycle round: every started round is closed, completed
   * or failed, and a failure is rethrown unchanged.
   */
  private ChatResult executeChatModelRound(
      ChatModel chatModel,
      AgentExecutionContext executionContext,
      AgentConfiguration agentConfiguration,
      ConversationSnapshot snapshot,
      AgentJobLifecycle lifecycle,
      int round) {
    lifecycle.modelRoundStarted(
        new ModelRoundStarted(
            round,
            agentConfiguration.chatModel(),
            snapshot.messages(),
            snapshot.toolDefinitions()));
    final ChatResult result;
    try {
      result = executeChatModel(chatModel, executionContext, snapshot);
    } catch (RuntimeException e) {
      final var errorCode = errorCodeOf(e);
      lifecycle.modelRoundFailed(
          new ModelRoundFailed(
              errorCode != null ? errorCode : e.getClass().getSimpleName(), errorCode, e));
      throw e;
    }
    lifecycle.modelRoundCompleted(
        new ModelRoundCompleted(
            result.assistantMessage(),
            result.metrics(),
            result instanceof ChatResult.Continuation));
    return result;
  }

  /**
   * Calls the chat model, translating a {@link ChatModelRejectedException} - thrown directly by the
   * provider when it recognizes a known, unrecoverable-for-now condition - into the equivalent
   * coded {@link ConnectorException}. Exhaustive over the sealed hierarchy, so a future subtype
   * fails to compile here until handled.
   */
  private ChatResult executeChatModel(
      ChatModel chatModel, AgentExecutionContext executionContext, ConversationSnapshot snapshot) {
    try {
      return chatModel.execute(new ChatRequest(executionContext, snapshot));
    } catch (ChatModelRejectedException e) {
      throw switch (e) {
        case ContentFilteredException cfe ->
            new ConnectorException(
                AgentErrorCodes.ERROR_CODE_MODEL_RESPONSE_CONTENT_FILTERED,
                cfe.getMessage(),
                cfe,
                rejectionErrorVariables(cfe));
        case ContextWindowExceededException cwe ->
            new ConnectorException(
                AgentErrorCodes.ERROR_CODE_MODEL_CONTEXT_WINDOW_EXCEEDED,
                cwe.getMessage(),
                cwe,
                rejectionErrorVariables(cwe));
        case GuardrailInterventionException gie ->
            new ConnectorException(
                AgentErrorCodes.ERROR_CODE_MODEL_RESPONSE_GUARDRAIL_INTERVENED,
                gie.getMessage(),
                gie,
                rejectionErrorVariables(gie));
      };
    }
  }

  /**
   * Surfaces the stop reason and any text the provider had already produced before the rejection as
   * a nested {@code rejection} error variable, so a BPMN error boundary event can inspect what the
   * model was doing when it was cut off. Empty when the provider rejected the request before
   * producing any partial result at all.
   */
  private static Map<String, Object> rejectionErrorVariables(ChatModelRejectedException e) {
    final var partialResult = e.partialResult();
    if (partialResult == null) {
      return Map.of();
    }

    final var assistantMessage = partialResult.assistantMessage();
    final Map<String, Object> rejection = new LinkedHashMap<>();
    if (assistantMessage.stopReason() != null) {
      rejection.put("stopReason", assistantMessage.stopReason().value());
    }
    final var text = MessageUtil.contentText(assistantMessage);
    if (!text.isBlank()) {
      rejection.put("text", text);
    }
    return rejection.isEmpty() ? Map.of() : Map.of("rejection", Map.copyOf(rejection));
  }

  private void throwIfLimitsReached(
      AgentConversation conversation, AgentConfiguration configuration) {
    var limit = configuration.maxModelCalls();
    if (isModelCallLimitExceeded(conversation, limit)) {
      throw new ConnectorException(
          AgentErrorCodes.ERROR_CODE_MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED,
          "Maximum number of model calls reached (modelCalls: %d, limit: %d)"
              .formatted(conversation.totalMetrics().modelCalls(), limit));
    }
  }

  private boolean isModelCallLimitExceeded(AgentConversation conversation, int maxModelCalls) {
    LOGGER.trace("Validating configured limits for agent execution");
    var currentModelCalls = conversation.totalMetrics().modelCalls();
    return currentModelCalls >= maxModelCalls;
  }

  private @Nullable SystemMessage createSystemMessage(
      AgentExecutionContext executionContext, AgentContext agentContext) {
    LOGGER.trace("Composing system message");
    var composedPrompt = systemPromptComposer.compose(executionContext, agentContext);
    if (StringUtils.isBlank(composedPrompt)) {
      return null;
    }
    return SystemMessage.builder().content(MessageUtil.singleTextContent(composedPrompt)).build();
  }

  private AgentInstanceUpdateStatus nextAgentInstanceState(int toolCallsDelta) {
    return toolCallsDelta == 0
        ? AgentInstanceUpdateStatus.IDLE
        : AgentInstanceUpdateStatus.TOOL_CALLING;
  }

  private R dispatchToolDiscovery(
      C executionContext,
      AgentContext agentContext,
      List<ToolCall> discoveryToolCalls,
      AgentJobLifecycle lifecycle) {
    var response =
        AgentResponse.builder()
            .context(
                lifecycle.toolDiscoveryDispatched(
                    agentContext, new ToolDiscoveryDispatched(discoveryToolCalls.size())))
            .toolCalls(discoveryToolCalls.stream().map(ToolCallProcessVariable::from).toList())
            .build();
    agentInstanceClient.applyToolDiscoveryStart(
        executionContext, AgentInstanceKey.from(agentContext.metadata()));
    return buildConnectorResponse(executionContext, null, response, withLifecycle(null, lifecycle));
  }

  /** Called when no agent response should be produced this turn. Default: no-op response. */
  protected R handleNoOp(C executionContext) {
    return buildConnectorResponse(executionContext, null, null, null);
  }

  /**
   * Called when {@link AgentConversationTurnInputComposer} returns {@link
   * CompositionResult.NoInput} — no input (user prompt, documents or events) could be composed for
   * this turn. Subclasses decide whether this is a hard error (throw) or a benign wait (no-op
   * response), and own the error code/message and logging.
   */
  protected abstract R handleNoInput(C executionContext);

  /**
   * Builds the connector response from the agent response. Conversation, agent response, and
   * listener may be null (e.g. on no-op, cancellation, or tool-discovery paths). The conversation
   * is provided on the proceed path so subclasses can derive response details (e.g. whether to
   * cancel remaining tool instances) from the turn input.
   */
  protected abstract R buildConnectorResponse(
      final C executionContext,
      @Nullable final AgentConversation conversation,
      @Nullable final AgentResponse agentResponse,
      @Nullable final AgentJobCompletionListener completionListener);

  private TurnStarted turnStarted(
      AgentConversation conversation, AgentConfiguration agentConfiguration) {
    final var currentTurn = conversation.currentTurn();
    final var previousTurn = conversation.lastTurn().orElse(null);
    final var toolCallsById = new java.util.HashMap<String, ToolCall>();
    if (previousTurn != null) {
      previousTurn.toolCalls().forEach(call -> toolCallsById.putIfAbsent(call.id(), call));
    }

    final var toolExecutions =
        currentTurn.inputMessages().stream()
            .filter(ToolCallResultMessage.class::isInstance)
            .map(ToolCallResultMessage.class::cast)
            .flatMap(message -> message.results().stream())
            .map(
                result ->
                    new ToolExecution(
                        result.id() != null ? toolCallsById.get(result.id()) : null,
                        result,
                        result.properties() != null
                            && Boolean.TRUE.equals(
                                result.properties().get(ToolCallResult.PROPERTY_INTERRUPTED))))
            .toList();

    final var issuedAt =
        toolExecutions.isEmpty() || previousTurn == null || previousTurn.assistantMessage() == null
            ? null
            : timestampOf(previousTurn.assistantMessage().id().value());

    return new TurnStarted(
        currentTurn.iterationKey(),
        toolExecutions,
        issuedAt,
        currentTurn.inputMessages(),
        conversation.systemMessage(),
        agentConfiguration.chatModel(),
        agentConfiguration.toolDefinitions());
  }

  /** The creation time encoded in a time-ordered (version 7) UUID, null for any other version. */
  private static @Nullable Instant timestampOf(UUID id) {
    return id.version() == 7 ? Instant.ofEpochMilli(id.getMostSignificantBits() >>> 16) : null;
  }

  /**
   * Composes the completion callbacks of the conversation store and of the lifecycle listeners. The
   * lifecycle callback runs even when the store callback throws.
   */
  private static @Nullable AgentJobCompletionListener withLifecycle(
      @Nullable AgentJobCompletionListener storeListener, AgentJobLifecycle lifecycle) {
    if (lifecycle == AgentJobLifecycle.NOOP) {
      return storeListener;
    }
    return new AgentJobCompletionListener() {
      @Override
      public void onJobCompleted() {
        try {
          if (storeListener != null) {
            storeListener.onJobCompleted();
          }
        } finally {
          lifecycle.jobCompleted();
        }
      }

      @Override
      public void onJobCompletionFailed(JobCompletionFailure failure) {
        try {
          if (storeListener != null) {
            storeListener.onJobCompletionFailed(failure);
          }
        } finally {
          lifecycle.jobCompletionFailed(failure);
        }
      }
    };
  }

  private static <C extends AgentExecutionContext>
      @Nullable AgentJobCompletionListener createStoreCompletionListener(
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
