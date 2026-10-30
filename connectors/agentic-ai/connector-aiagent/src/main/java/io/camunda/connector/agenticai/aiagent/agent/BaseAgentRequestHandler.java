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
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRegistry;
import io.camunda.connector.agenticai.aiagent.conversation.AgentConversation;
import io.camunda.connector.agenticai.aiagent.conversation.AgentInput;
import io.camunda.connector.agenticai.aiagent.conversation.PreviousConversation;
import io.camunda.connector.agenticai.aiagent.conversation.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.document.DocumentExpander;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistries;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.AgentInstanceKey;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.agenticai.aiagent.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistry;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.ConnectorResponse;
import io.camunda.connector.api.outbound.JobCompletionFailure;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
  private final ResponseSchemaValidator responseSchemaValidator;
  private final RuntimeToolRegistry runtimeToolRegistry;
  private final RuntimeToolExecutor runtimeToolExecutor;
  private final DocumentExpander documentExpander;

  public BaseAgentRequestHandler(
      AgentInitializer agentInitializer,
      ConversationStoreRegistry conversationStoreRegistry,
      AgentConversationTurnInputComposer agentInputComposer,
      ChatModelRegistry chatModelRegistry,
      SystemPromptComposer systemPromptComposer,
      AgentResponseHandler responseHandler,
      AgentInstanceClient agentInstanceClient,
      ResponseSchemaValidator responseSchemaValidator,
      RuntimeToolRegistry runtimeToolRegistry,
      RuntimeToolExecutor runtimeToolExecutor,
      DocumentExpander documentExpander) {
    this.agentInitializer = agentInitializer;
    this.conversationStoreRegistry = conversationStoreRegistry;
    this.agentInputComposer = agentInputComposer;
    this.chatModelRegistry = chatModelRegistry;
    this.systemPromptComposer = systemPromptComposer;
    this.responseHandler = responseHandler;
    this.agentInstanceClient = agentInstanceClient;
    this.responseSchemaValidator = responseSchemaValidator;
    this.runtimeToolRegistry = runtimeToolRegistry;
    this.runtimeToolExecutor = runtimeToolExecutor;
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
      // AgentContext plus the runtime tools enabled for this agent, which are never persisted.
      final var configuration =
          executionContext
              .configuration()
              .withToolDefinitions(toolDefinitions(executionContext, agentContext));
      final var agentInput = AgentInput.from(configuration.userPrompt(), toolCallResults);

      LOGGER.trace("Loading previous conversation (if any) for rehydration");
      final var loadedMessages = session.loadMessages(agentContext).messages();
      final var previousConversation =
          TurnReconstructor.reconstruct(loadedMessages, agentContext.metadata());

      LOGGER.trace("Composing turn input from history and invocation state");
      var compositionResult =
          agentInputComposer.compose(configuration, agentContext, previousConversation, agentInput);
      if (compositionResult
          instanceof CompositionResult.RuntimeToolResultsRequired(var runtimeToolCalls)) {
        // the results of the process arrived: the agent executes its own calls now
        final var executions =
            runtimeToolExecutor.execute(executionContext, agentContext, runtimeToolCalls);
        compositionResult =
            agentInputComposer.composeWithRuntimeToolResults(
                configuration, agentContext, previousConversation, agentInput, executions);
      }
      return switch (compositionResult) {
        case CompositionResult.Deferred(var arrivedResults) -> {
          LOGGER.debug("No input ready to add, completing job without agent response");
          reportArrivedToolCallResults(
              executionContext, agentContext, arrivedResults, previousConversation);
          yield handleNoOp(executionContext);
        }
        case CompositionResult.NoInput ignored -> {
          LOGGER.debug("No input could be composed for this turn");
          yield handleNoInput(executionContext);
        }
        case CompositionResult.RuntimeToolResultsRequired ignored ->
            throw new IllegalStateException(
                "The turn input was composed without the runtime tool results after they were computed");
        case CompositionResult.NextTurn(var newMessages, var newDocuments) ->
            proceed(
                executionContext,
                DocumentRegistries.register(agentContext, newDocuments),
                configuration,
                previousConversation,
                newMessages,
                session,
                store);
      };
    }
  }

  /**
   * The tool definitions of the process and the definitions of the runtime tools enabled for the
   * agent.
   *
   * @throws ConnectorException if a tool of the process has the name of a registered runtime tool
   */
  private List<ToolDefinition> toolDefinitions(C executionContext, AgentContext agentContext) {
    final var processToolDefinitions = agentContext.toolDefinitions();
    processToolDefinitions.stream()
        .map(ToolDefinition::name)
        .filter(name -> runtimeToolRegistry.kindOf(agentContext, name) == ToolKind.RUNTIME)
        .findFirst()
        .ifPresent(
            name -> {
              throw new ConnectorException(
                  AgentErrorCodes.ERROR_CODE_RUNTIME_TOOL_NAME_CONFLICT,
                  "The tool name '%s' is also the name of a runtime tool of the AI Agent. Rename the tool of the process with this name or the runtime tool."
                      .formatted(name));
            });
    return Stream.concat(
            processToolDefinitions.stream(),
            runtimeToolRegistry.enabledToolDefinitions(executionContext, agentContext).stream())
        .toList();
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
        agentContext.agentInstanceKey(),
        arrivedResults.stream().map(ToolCallResultContent::from).toList(),
        previousConversation.turns().getLast(),
        agentContext);
  }

  private R proceed(
      final C executionContext,
      final AgentContext agentContext,
      final AgentConfiguration agentConfiguration,
      final PreviousConversation previousConversation,
      final List<Message> inputMessages,
      final ConversationSession session,
      final ConversationStore store) {
    final var response = agentConfiguration.response();
    responseSchemaValidator.validate(response != null ? response.format() : null);

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
        OffsetDateTime.now(ZoneId.systemDefault()),
        conversation.toAgentContext());

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
   * Drives the given chat model until it returns a {@code Completed} result that needs no further
   * round in this job, ingesting each round as its own turn. The tool calls of each message are
   * tagged with the kind of their tool before the turn is ingested. Further rounds are intermediate
   * {@code Continuation} rounds and rounds that follow a message that calls runtime tools only: the
   * agent executes the calls itself and the results are the input of the next round. A message that
   * calls process tools ends the loop, also when it calls runtime tools too: only the process tool
   * calls are handed out, and the runtime tool calls are executed when the turn that follows the
   * process results is composed.
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
          workingConversation.window(
              agentConfiguration.contextWindowSize(),
              documentExpander.windowDecorator(workingConversation));
      final var chatResult = executeChatModel(chatModel, executionContext, windowedSnapshot);

      // the one place the tool calls are tagged with the kind of their tool
      final var assistantMessage =
          runtimeToolRegistry.tagToolCalls(
              workingConversation.persistedContext(), chatResult.assistantMessage());
      workingConversation = workingConversation.ingest(assistantMessage, chatResult.metrics());

      final var currentTurn = workingConversation.currentTurn();
      final boolean continued = chatResult instanceof ChatResult.Continuation;
      final boolean runtimeRound =
          !continued && currentTurn.hasRuntimeToolCalls() && !currentTurn.hasProcessToolCalls();
      anotherRound = continued || runtimeRound;

      // Exactly one status per round (engine constraint): a round that is followed by another one
      // within this job re-asserts THINKING alongside its assistant item, the final round sends its
      // terminal status instead. Every tool call the model requested counts as a tool call of the
      // turn, runtime or not.
      final var status =
          anotherRound
              ? AgentInstanceUpdateStatus.THINKING
              : nextAgentInstanceState(workingConversation.currentTurnMetrics().toolCalls());
      agentInstanceClient.applyTurnCompletion(
          executionContext,
          agentInstanceKey,
          currentTurn,
          OffsetDateTime.now(ZoneId.systemDefault()),
          status);

      if (continued) {
        LOGGER.debug(
            "Provider requested continuation (iterationKey={}); resuming with another round",
            currentTurn.iterationKey());

        throwIfLimitsReached(workingConversation, agentConfiguration);
        workingConversation = workingConversation.nextContinuationRound();
      } else if (runtimeRound) {
        LOGGER.debug(
            "Executing the runtime tool calls of turn (iterationKey={}); calling the model again",
            currentTurn.iterationKey());

        throwIfLimitsReached(workingConversation, agentConfiguration);
        final var executions =
            runtimeToolExecutor.execute(
                executionContext,
                workingConversation.persistedContext(),
                currentTurn.runtimeToolCalls());
        // the documents of the results are registered after all calls ran, in call order, so a call
        // never sees what another call of the same message returned
        final var input =
            ToolCallResultsInputBuilder.build(
                executions.stream().map(ToolCallResultsInputBuilder.Entry::of).toList(),
                workingConversation.persistedContext().supports(AgentFeatures.DOCUMENTS));
        workingConversation =
            workingConversation
                .withPersistedContext(
                    DocumentRegistries.register(
                        workingConversation.persistedContext(), input.documents()))
                .nextRuntimeToolRound(input.messages());
        // the previous turn is the one whose tool calls these results belong to
        agentInstanceClient.applyTurnStart(
            executionContext,
            agentConfiguration,
            agentInstanceKey,
            workingConversation.currentTurn(),
            workingConversation.lastTurn(),
            OffsetDateTime.now(ZoneId.systemDefault()),
            workingConversation.toAgentContext());
      } else if (currentTurn.hasRuntimeToolCalls()) {
        LOGGER.debug(
            "{} runtime tool calls of turn (iterationKey={}) are executed with the next turn; {} go to the process",
            currentTurn.runtimeToolCalls().size(),
            currentTurn.iterationKey(),
            currentTurn.processToolCalls().size());
      }
    } while (anotherRound);
    return workingConversation;
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
    agentInstanceClient.applyToolDiscoveryStart(executionContext, agentContext.agentInstanceKey());
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
