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
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceUpdateRequest;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRequest;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentInput;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.PreviousConversation;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.ConnectorResponse;
import io.camunda.connector.api.outbound.JobCompletionFailure;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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
  private final AiFrameworkAdapter<?> framework;
  private final SystemPromptComposer systemPromptComposer;
  private final AgentResponseHandler responseHandler;
  private final AgentInstanceClient agentInstanceClient;

  public BaseAgentRequestHandler(
      AgentInitializer agentInitializer,
      ConversationStoreRegistry conversationStoreRegistry,
      AgentConversationTurnInputComposer agentInputComposer,
      AiFrameworkAdapter<?> framework,
      SystemPromptComposer systemPromptComposer,
      AgentResponseHandler responseHandler,
      AgentInstanceClient agentInstanceClient) {
    this.agentInitializer = agentInitializer;
    this.conversationStoreRegistry = conversationStoreRegistry;
    this.agentInputComposer = agentInputComposer;
    this.framework = framework;
    this.systemPromptComposer = systemPromptComposer;
    this.responseHandler = responseHandler;
    this.agentInstanceClient = agentInstanceClient;
  }

  @Override
  public R handleRequest(final C executionContext) {
    return switch (agentInitializer.initializeAgent(executionContext)) {
      case DiscoverTools(var agentContext, var toolDiscoveryToolCalls) -> {
        LOGGER.debug(
            "AI Agent initialization dispatching {} gateway tool discovery calls. Completing job without further processing.",
            toolDiscoveryToolCalls.size());
        yield dispatchToolDiscovery(executionContext, agentContext, toolDiscoveryToolCalls);
      }
      case DeferConversation() -> {
        LOGGER.debug(
            "AI Agent initialization tool discovery is still in progress. Completing job without further processing.");
        yield handleNoOp(executionContext);
      }
      case ReadyToConverse(var agentContext, var toolCallResults) -> {
        LOGGER.debug("Handling agent request with {} tool call results", toolCallResults.size());
        yield converse(executionContext, agentContext, toolCallResults);
      }
    };
  }

  private R converse(
      final C executionContext,
      final AgentContext agentContext,
      final List<ToolCallResult> toolCallResults) {
    final var store =
        conversationStoreRegistry.getConversationStore(executionContext, agentContext);

    try (var session = store.createSession(executionContext, agentContext)) {
      final var configuration = executionContext.configuration();
      final var agentInput = AgentInput.from(configuration.userPrompt(), toolCallResults);

      LOGGER.trace("Loading previous conversation (if any) for rehydration");
      final var loadResult = session.loadMessages(agentContext);
      final var loadedMessages = loadResult.messages();
      final var previousConversation = TurnReconstructor.reconstruct(loadedMessages);

      LOGGER.trace("Composing turn input from history and invocation state");
      final var compositionResult =
          agentInputComposer.compose(configuration, agentContext, previousConversation, agentInput);
      return switch (compositionResult) {
        case CompositionResult.Deferred ignored -> {
          LOGGER.debug("No input ready to add, completing job without agent response");
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
                previousConversation,
                newMessages,
                loadResult.documentRegistry(),
                session,
                store);
      };
    }
  }

  private R proceed(
      final C executionContext,
      final AgentContext agentContext,
      final PreviousConversation previousConversation,
      final List<Message> inputMessages,
      final DocumentRegistry loadedRegistry,
      final ConversationSession session,
      final ConversationStore store) {
    var agentConfiguration = executionContext.configuration();
    // Freeze the system prompt: compose it once on the first turn, then reuse the copy persisted in
    // the conversation history. This avoids re-running every system-prompt contributor on each
    // execution — notably the sandbox skills contributor, which renders the <available_skills>
    // catalog. The composed message is stored as the first message via
    // AgentConversation#allMessages
    // and reconstructed by TurnReconstructor into previousConversation.systemMessage(). Because
    // sandbox tool discovery (CREATE) completes before the first conversation turn, the skill
    // catalog is already present when this first composition happens.
    var systemMessage =
        previousConversation
            .systemMessage()
            .orElseGet(() -> createSystemMessage(executionContext, agentContext));
    final var documentRegistry = buildRegistry(loadedRegistry, inputMessages);
    final var conversation =
        AgentConversation.rehydrate(
            agentConfiguration,
            agentContext,
            previousConversation,
            systemMessage,
            inputMessages,
            documentRegistry);

    throwIfLimitsReached(conversation, agentConfiguration);
    notifyThinking(executionContext, conversation);

    final var agentInstanceKey = conversation.agentInstanceKey();
    // called before ingest, so the current turn is still pending and lastTurn() is the turn
    // preceding it — i.e. the one whose tool calls originated the current turn's tool results.
    // Non-tool-result input items (e.g. the user message) are stamped with this turn-ingestion
    // timestamp; tool-result items use their own resolved completedAt instead (ADR 008).
    agentInstanceClient.createHistoryForInputMessages(
        executionContext,
        agentInstanceKey,
        conversation.currentTurn(),
        conversation.lastTurn(),
        OffsetDateTime.now());

    LOGGER.debug("Executing chat request with AI framework");
    final var chatResponse =
        framework.executeMeasuringTime(
            executionContext, conversation.window(agentConfiguration.contextWindowSize()));
    final var updatedConversation =
        conversation.ingest(chatResponse.assistantMessage(), chatResponse.metrics());

    agentInstanceClient.createHistoryForAssistantMessage(
        executionContext,
        agentInstanceKey,
        updatedConversation.currentTurn(),
        OffsetDateTime.now());

    LOGGER.debug("Storing conversation messages to session");
    final var storedRef =
        session.storeMessages(
            updatedConversation.toAgentContext(),
            ConversationStoreRequest.of(
                updatedConversation.allMessages(), updatedConversation.documentRegistry()));

    final var storedConversation = updatedConversation.withStoredConversation(storedRef);
    final var agentResponse = responseHandler.createResponse(storedConversation);

    LOGGER.debug("Request processing completed with agent response, completing job");

    final var messageStorageCompletionListener =
        createStoreCompletionListener(executionContext, store, agentResponse);
    if (shouldUpdateAgentInstanceBeforeJobCompletion(storedConversation)) {
      notifyMetrics(executionContext, storedConversation, agentResponse, true);
      return buildConnectorResponse(
          executionContext, storedConversation, agentResponse, messageStorageCompletionListener);
    }

    return buildConnectorResponse(
        executionContext,
        storedConversation,
        agentResponse,
        AgentJobCompletionListener.compose(
            messageStorageCompletionListener,
            createMetricsCompletionListener(executionContext, storedConversation, agentResponse)));
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

  private void notifyThinking(C executionContext, AgentConversation conversation) {
    LOGGER.debug("Notifying agent instance: status=THINKING before LLM call");
    agentInstanceClient.update(
        executionContext,
        conversation.agentInstanceKey(),
        AgentInstanceUpdateRequest.builder()
            .status(AgentInstanceUpdateStatus.THINKING)
            .tools(conversation.toAgentContext().toolDefinitions())
            .build());
  }

  private AgentInstanceUpdateStatus nextAgentInstanceState(int toolCallsDelta) {
    return toolCallsDelta == 0
        ? AgentInstanceUpdateStatus.IDLE
        : AgentInstanceUpdateStatus.TOOL_CALLING;
  }

  private R dispatchToolDiscovery(
      C executionContext, AgentContext agentContext, List<ToolCall> discoveryToolCalls) {
    var response =
        AgentResponse.builder()
            .context(agentContext)
            .toolCalls(discoveryToolCalls.stream().map(ToolCallProcessVariable::from).toList())
            .build();
    var listener = createToolDiscoveryCompletionListener(executionContext, agentContext);
    return buildConnectorResponse(executionContext, null, response, listener);
  }

  private AgentJobCompletionListener createToolDiscoveryCompletionListener(
      C executionContext, AgentContext agentContext) {
    return new AgentJobCompletionListener() {
      @Override
      public void onJobCompleted() {
        try {
          agentInstanceClient.update(
              executionContext,
              AgentInstanceKey.from(agentContext.metadata()),
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
   * Returns {@code true} when the agent-instance PATCH must be sent synchronously before the job
   * completion command is issued. Returning {@code false} defers the PATCH to {@link
   * AgentJobCompletionListener#onJobCompleted()}, which is safe as long as the element instance
   * survives job completion (e.g. an AHSP intermediate turn where tool elements are activated and
   * the subprocess stays open).
   */
  protected abstract boolean shouldUpdateAgentInstanceBeforeJobCompletion(
      AgentConversation conversation);

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

  private void notifyMetrics(
      C executionContext,
      AgentConversation conversation,
      AgentResponse response,
      boolean rethrowOnFailure) {
    final var metricsDelta = conversation.currentTurnMetrics();
    final var nextState = nextAgentInstanceState(metricsDelta.toolCalls());
    final var agentContext = response.context();

    notifyMetrics(executionContext, agentContext, metricsDelta, nextState, rethrowOnFailure);
  }

  private void notifyMetrics(
      C executionContext,
      AgentContext context,
      AgentMetrics metricsDelta,
      @Nullable AgentInstanceUpdateStatus nextState,
      boolean rethrowOnFailure) {
    try {

      LOGGER.debug(
          "Updating agent instance metrics: status={}, modelCalls=+{}, inputTokens=+{}, outputTokens=+{}, toolCalls=+{}",
          nextState,
          metricsDelta.modelCalls(),
          metricsDelta.tokenUsage().inputTokenCount(),
          metricsDelta.tokenUsage().outputTokenCount(),
          metricsDelta.toolCalls());
      // The agent-instance metrics update is counters-only (model/tool calls, tokens). The per-turn
      // execution duration is a conversation-history concern and is not transmitted here, so it is
      // stripped from the delta.
      var updateRequestBuilder =
          AgentInstanceUpdateRequest.builder().delta(metricsDelta.withExecutionTime(null));
      if (nextState != null) {
        updateRequestBuilder.status(nextState);
      }
      agentInstanceClient.update(
          executionContext,
          AgentInstanceKey.from(context.metadata()),
          updateRequestBuilder.build());
    } catch (Exception e) {
      LOGGER.error("Failed to update agent instance metrics; metrics may be inaccurate", e);
      if (rethrowOnFailure) {
        throw e;
      }
    }
  }

  private AgentJobCompletionListener createMetricsCompletionListener(
      C executionContext, AgentConversation conversation, AgentResponse response) {
    return new AgentJobCompletionListener() {
      @Override
      public void onJobCompleted() {
        notifyMetrics(executionContext, conversation, response, false);
      }

      @Override
      public void onJobCompletionFailed(JobCompletionFailure failure) {
        final var strippedDelta = conversation.currentTurnMetrics().withToolCalls(0);
        if (failure instanceof JobCompletionFailure.CommandFailure.CommandIgnored) {
          // Superseded job: report model/token cost but don't overwrite the current status
          notifyMetrics(executionContext, response.context(), strippedDelta, null, false);
        } else {
          notifyMetrics(
              executionContext,
              response.context(),
              strippedDelta,
              AgentInstanceUpdateStatus.IDLE,
              false);
        }
      }
    };
  }

  /**
   * Builds the document registry for this turn by combining the registry loaded from the previous
   * turn with the documents newly added in {@code inputMessages} (user-prompt documents from {@link
   * DocumentContent} content blocks, and tool-call-result documents from {@link
   * ToolCallResultMessage} result content trees). Population is engine-driven only — no
   * agent-facing tool adds entries (§11.6).
   */
  private static DocumentRegistry buildRegistry(
      DocumentRegistry loadedRegistry, List<Message> inputMessages) {
    final var newDocs = new ArrayList<Document>();
    for (var msg : inputMessages) {
      switch (msg) {
        case UserMessage userMsg ->
            userMsg.content().stream()
                .filter(c -> c instanceof DocumentContent)
                .map(c -> ((DocumentContent) c).document())
                .forEach(newDocs::add);
        case ToolCallResultMessage toolMsg ->
            toolMsg.results().stream()
                .flatMap(
                    r ->
                        ContentTreeDocumentWalker.extractDocumentsFromContent(r.content()).stream())
                .forEach(newDocs::add);
        default -> {
          // SystemMessage and AssistantMessage carry no inbound documents
        }
      }
    }
    return loadedRegistry.withAddedDocuments(newDocs);
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
