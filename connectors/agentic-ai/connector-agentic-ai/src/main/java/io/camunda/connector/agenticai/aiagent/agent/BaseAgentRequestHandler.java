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
import io.camunda.connector.agenticai.aiagent.agentinstance.HistoryDocuments;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRegistry;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRejectedException;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContentFilteredException;
import io.camunda.connector.agenticai.aiagent.chatmodel.ContextWindowExceededException;
import io.camunda.connector.agenticai.aiagent.chatmodel.GuardrailInterventionException;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRequest;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentInput;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.PreviousConversation;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentExpander;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistryStorage;
import io.camunda.connector.agenticai.aiagent.model.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.ConnectorResponse;
import io.camunda.connector.api.outbound.JobCompletionFailure;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
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
  private final DocumentRegistryStorage documentRegistryStorage;
  private final LocalToolCallHandler localToolCallHandler;
  private final DocumentExpander documentExpander;

  public BaseAgentRequestHandler(
      AgentInitializer agentInitializer,
      ConversationStoreRegistry conversationStoreRegistry,
      AgentConversationTurnInputComposer agentInputComposer,
      ChatModelRegistry chatModelRegistry,
      SystemPromptComposer systemPromptComposer,
      AgentResponseHandler responseHandler,
      AgentInstanceClient agentInstanceClient,
      DocumentRegistryStorage documentRegistryStorage,
      LocalToolCallHandler localToolCallHandler,
      DocumentExpander documentExpander) {
    this.agentInitializer = agentInitializer;
    this.conversationStoreRegistry = conversationStoreRegistry;
    this.agentInputComposer = agentInputComposer;
    this.chatModelRegistry = chatModelRegistry;
    this.systemPromptComposer = systemPromptComposer;
    this.responseHandler = responseHandler;
    this.agentInstanceClient = agentInstanceClient;
    this.documentRegistryStorage = documentRegistryStorage;
    this.localToolCallHandler = localToolCallHandler;
    this.documentExpander = documentExpander;
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
      // AgentConfiguration#toolDefinitions() becomes the authoritative current tool list for the
      // rest of this invocation once populated here: the durable tool definitions of the
      // AgentContext plus the local tools available for this agent, which are never persisted.
      final var requestConfiguration =
          executionContext.configuration().withFeatures(agentContext.features());
      final var configuration =
          requestConfiguration.withToolDefinitions(
              Stream.concat(
                      agentContext.toolDefinitions().stream(),
                      localToolCallHandler.availableToolDefinitions(requestConfiguration).stream())
                  .toList());
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
              executionContext,
              agentContext,
              arrivedResults,
              previousConversation,
              historyDocuments(agentContext, configuration));
          yield handleNoOp(executionContext);
        }
        case CompositionResult.NoInput ignored -> {
          LOGGER.debug("No input could be composed for this turn");
          yield handleNoInput(executionContext);
        }
        case CompositionResult.NextTurn(var newMessages, var newDocuments) ->
            proceed(
                executionContext,
                agentContext.supportsDocuments()
                    // the pending local results are part of newMessages now
                    ? registerDocuments(agentContext, newDocuments)
                        .withPendingLocalToolCallResults(List.of())
                    : agentContext,
                configuration,
                previousConversation,
                newMessages,
                session,
                store);
      };
    }
  }

  /**
   * Returns the agent context carrying the document registry extended by the documents that enter
   * the conversation with this turn.
   */
  private AgentContext registerDocuments(
      AgentContext agentContext, List<IngestedDocument> newDocuments) {
    return documentRegistryStorage.store(
        agentContext, documentRegistryStorage.load(agentContext).withDocuments(newDocuments));
  }

  /** The documents the history shows for the agent context, before this turn registers any. */
  private HistoryDocuments historyDocuments(
      AgentContext agentContext, AgentConfiguration configuration) {
    return agentContext.supportsDocuments()
        ? new HistoryDocuments(
            configuration.toolDefinitions(), documentRegistryStorage.load(agentContext))
        : HistoryDocuments.withoutDocumentFlow(
            configuration.toolDefinitions(), agentContext.supportsLocalTools());
  }

  /**
   * Reports {@code arrivedResults} to agent instance history. No-ops when empty or when there is no
   * previous turn to correlate against.
   */
  private void reportArrivedToolCallResults(
      C executionContext,
      AgentContext agentContext,
      List<ToolCallResult> arrivedResults,
      PreviousConversation previousConversation,
      HistoryDocuments documents) {
    if (arrivedResults.isEmpty() || previousConversation.turns().isEmpty()) {
      return;
    }
    agentInstanceClient.applyToolCallResults(
        executionContext,
        AgentInstanceKey.from(agentContext.metadata()),
        arrivedResults,
        previousConversation.turns().getLast(),
        documents);
  }

  private R proceed(
      final C executionContext,
      final AgentContext agentContext,
      final AgentConfiguration agentConfiguration,
      final PreviousConversation previousConversation,
      final List<Message> inputMessages,
      final ConversationSession session,
      final ConversationStore store) {
    var systemMessage = createSystemMessage(executionContext, agentContext);
    final var conversation =
        AgentConversation.rehydrate(
            agentConfiguration, agentContext, previousConversation, systemMessage, inputMessages);

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
        OffsetDateTime.now(),
        historyDocuments(agentConfiguration, conversation));

    final AgentConversation updatedConversation;
    try (final var chatModel = chatModelRegistry.resolve(agentConfiguration.chatModel())) {
      updatedConversation =
          driveContinuationLoop(
              executionContext, agentConfiguration, conversation, chatModel, agentInstanceKey);
    }

    LOGGER.debug("Storing conversation messages to session");
    final var storedRef =
        session.storeMessages(
            updatedConversation.toAgentContext(),
            ConversationStoreRequest.of(updatedConversation.allMessages()));

    final var storedConversation = updatedConversation.withStoredConversation(storedRef);
    final var agentResponse = responseHandler.createResponse(storedConversation);

    LOGGER.debug("Request processing completed with agent response, completing job");

    final var messageStorageCompletionListener =
        createStoreCompletionListener(executionContext, store, agentResponse);
    return buildConnectorResponse(
        executionContext, storedConversation, agentResponse, messageStorageCompletionListener);
  }

  /**
   * Drives the given chat model until it returns a {@code Completed} result whose tool calls (if
   * any) are left to the process, ingesting each round as its own turn. Further rounds are
   * intermediate {@code Continuation} rounds and rounds that follow a batch of tool calls the agent
   * answered completely itself (see {@link LocalToolCallHandler}). A batch that mixes local and
   * process calls ends the loop; its local results are kept as pending results of the conversation.
   */
  private AgentConversation driveContinuationLoop(
      C executionContext,
      AgentConfiguration agentConfiguration,
      AgentConversation conversation,
      ChatModel chatModel,
      @Nullable AgentInstanceKey agentInstanceKey) {
    var workingConversation = conversation;
    boolean anotherRound;
    do {
      LOGGER.debug(
          "Sending turn (iterationKey={}) to the model",
          workingConversation.currentTurn().iterationKey());

      final var windowedSnapshot =
          workingConversation.window(agentConfiguration.contextWindowSize(), documentExpander);
      final var chatResult = executeChatModel(chatModel, executionContext, windowedSnapshot);

      workingConversation =
          workingConversation.ingest(chatResult.assistantMessage(), chatResult.metrics());

      final boolean continued = chatResult instanceof ChatResult.Continuation;
      final var split =
          continued
              ? LocalToolCallHandler.LocalToolCallSplit.allRemaining(workingConversation, List.of())
              : localToolCallHandler.handle(
                  workingConversation, workingConversation.currentTurn().toolCalls());
      workingConversation = split.conversation();
      final boolean localRound = split.answersAllCalls();
      anotherRound = continued || localRound;

      // Exactly one status per round (engine constraint): a round that is followed by another one
      // within this job re-asserts THINKING alongside its assistant item, the final round sends its
      // terminal status instead. Every tool call the model requested counts as a tool call of the
      // turn, local or not.
      final var status =
          anotherRound
              ? AgentInstanceUpdateStatus.THINKING
              : nextAgentInstanceState(workingConversation.currentTurnMetrics().toolCalls());
      agentInstanceClient.applyTurnCompletion(
          executionContext,
          agentInstanceKey,
          workingConversation.currentTurn(),
          OffsetDateTime.now(),
          status,
          historyDocuments(agentConfiguration, workingConversation));

      if (continued) {
        LOGGER.debug(
            "Provider requested continuation (iterationKey={}); resuming with another round",
            workingConversation.currentTurn().iterationKey());

        throwIfLimitsReached(workingConversation, agentConfiguration);
        workingConversation = workingConversation.nextContinuationRound();
      } else if (localRound) {
        LOGGER.debug(
            "Answered the tool calls of turn (iterationKey={}) locally; calling the model again",
            workingConversation.currentTurn().iterationKey());

        throwIfLimitsReached(workingConversation, agentConfiguration);
        final var loadedDocumentsMessage =
            agentInputComposer.composeLoadedDocumentsMessage(
                workingConversation.documentRegistry(), split.localResults());
        workingConversation =
            workingConversation.nextLocalToolRound(
                ToolCallResultMessage.builder()
                    .results(
                        split.localResults().stream().map(ToolCallResultContent::from).toList())
                    .build(),
                loadedDocumentsMessage == null ? List.of() : List.of(loadedDocumentsMessage));
        // the previous turn is the one whose tool calls these results answer
        agentInstanceClient.applyTurnStart(
            executionContext,
            agentConfiguration,
            agentInstanceKey,
            workingConversation.currentTurn(),
            workingConversation.lastTurn(),
            OffsetDateTime.now(),
            historyDocuments(agentConfiguration, workingConversation));
      } else if (split.hasLocalResults()) {
        LOGGER.debug(
            "Answered {} of the tool calls of turn (iterationKey={}) locally; {} go to the process",
            split.localResults().size(),
            workingConversation.currentTurn().iterationKey(),
            split.remainingCalls().size());

        // the local results become visible in the history right away, correlated to the assistant
        // turn that was just completed (no status change: the round already carries its one
        // status). The next job reports them again with its turn start under the same history item
        // ids, which the engine deduplicates.
        agentInstanceClient.applyToolCallResults(
            executionContext,
            agentInstanceKey,
            split.localResults(),
            workingConversation.currentTurn(),
            historyDocuments(agentConfiguration, workingConversation));

        // the results join the process results of the remaining calls in the next job
        workingConversation =
            workingConversation.withPendingLocalToolCallResults(split.localResults());
      }
    } while (anotherRound);
    return workingConversation;
  }

  private static HistoryDocuments historyDocuments(
      AgentConfiguration configuration, AgentConversation conversation) {
    return conversation.supportsDocuments()
        ? new HistoryDocuments(configuration.toolDefinitions(), conversation.documentRegistry())
        : HistoryDocuments.withoutDocumentFlow(
            configuration.toolDefinitions(), conversation.supportsLocalTools());
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
      C executionContext, AgentContext agentContext, List<ToolCall> discoveryToolCalls) {
    var response =
        AgentResponse.builder()
            .context(agentContext)
            .toolCalls(discoveryToolCalls.stream().map(ToolCallProcessVariable::from).toList())
            .build();
    agentInstanceClient.applyToolDiscoveryStart(
        executionContext, AgentInstanceKey.from(agentContext.metadata()));
    return buildConnectorResponse(executionContext, null, response, null);
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
