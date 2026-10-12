/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceKey;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.runtime.MessageWindowFilter;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentExpander;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.EvictedDocumentsNote;
import io.camunda.connector.agenticai.aiagent.model.document.LegacyDocumentRendering;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Immutable domain aggregate representing the agent's full conversation across all turns.
 *
 * <p>Built once per invocation via {@link #rehydrate}, then mutated through copy-on-write methods:
 * {@link #ingest}, {@link #withStoredConversation}.
 *
 * <p>{@link #currentTurn} is always non-null: it is created as a pending turn at construction time
 * and completed by {@link #ingest}.
 */
public final class AgentConversation {

  private static final Logger LOGGER = LoggerFactory.getLogger(AgentConversation.class);

  private final AgentConfiguration configuration;
  private final AgentContext currentContext;
  private final @Nullable AgentInstanceKey agentInstanceKey;
  private final @Nullable SystemMessage systemMessage;
  private final List<AgentConversationTurn> previousTurns;
  private final AgentConversationTurn currentTurn;
  private final int invocationStartIterationKey;
  private final @Nullable String precedingConfigurationFingerprint;

  private AgentConversation(
      AgentConfiguration configuration,
      AgentContext currentContext,
      @Nullable SystemMessage systemMessage,
      List<AgentConversationTurn> previousTurns,
      AgentConversationTurn currentTurn,
      int invocationStartIterationKey,
      @Nullable String precedingConfigurationFingerprint) {
    this.configuration = configuration;
    this.currentContext = currentContext;
    this.systemMessage = systemMessage;
    this.previousTurns = List.copyOf(previousTurns);
    this.currentTurn = currentTurn;
    this.agentInstanceKey = AgentInstanceKey.from(currentContext.metadata());
    this.invocationStartIterationKey = invocationStartIterationKey;
    this.precedingConfigurationFingerprint = precedingConfigurationFingerprint;
  }

  /**
   * Rehydrates a conversation from the reconstructed previous conversation and the composed
   * current-turn input.
   *
   * @param configuration static per-invocation configuration
   * @param agentContext durable agent context restored from the process variable
   * @param previousConversation turns reconstructed from the flat stored message list
   * @param systemMessage composed system message for this invocation, or {@code null} when the
   *     composed system prompt is blank
   * @param inputMessages messages to place into the pending current turn
   * @return a rehydrated {@code AgentConversation} with a pending current turn
   */
  public static AgentConversation rehydrate(
      AgentConfiguration configuration,
      AgentContext agentContext,
      PreviousConversation previousConversation,
      @Nullable SystemMessage systemMessage,
      List<Message> inputMessages) {
    int nextKey = nextIterationKey(agentContext, previousConversation);
    var currentTurn =
        new AgentConversationTurn(
            nextKey, inputMessages, null, AgentMetrics.empty(), configuration.fingerprint());
    var precedingTurns = previousConversation.turns();
    var precedingFingerprint =
        precedingTurns.isEmpty() ? null : precedingTurns.getLast().configurationFingerprint();
    return new AgentConversation(
        configuration,
        agentContext,
        systemMessage,
        precedingTurns,
        currentTurn,
        nextKey,
        precedingFingerprint);
  }

  /**
   * Determines the next turn's iterationKey. The stored {@code lastIterationKey} on the agent
   * context metadata is authoritative when present; the reconstructed turn count is used as a
   * fallback for conversations created before this field was introduced, and to cross-validate the
   * stored value, logging a warning on drift instead of failing silently.
   */
  private static int nextIterationKey(
      AgentContext agentContext, PreviousConversation previousConversation) {
    var metadata = agentContext.metadata();
    var storedLastIterationKey = metadata != null ? metadata.lastIterationKey() : null;
    int reconstructedCount = previousConversation.turns().size();

    if (storedLastIterationKey == null) {
      return reconstructedCount + 1;
    }

    if (storedLastIterationKey != reconstructedCount) {
      LOGGER.warn(
          "Turn reconstruction drift detected: stored lastIterationKey={} but reconstructed {} turn(s) from history",
          storedLastIterationKey,
          reconstructedCount);
    }
    return storedLastIterationKey + 1;
  }

  /**
   * Completes the pending turn by recording the assistant response and token usage.
   *
   * @throws IllegalStateException if the current turn is already complete
   */
  public AgentConversation ingest(AssistantMessage assistantMessage, AgentMetrics turnMetrics) {
    if (currentTurn.assistantMessage() != null) {
      throw new IllegalStateException("ingest() called on an already-completed turn");
    }
    var completedTurn = currentTurn.withAssistantMessage(assistantMessage, turnMetrics);
    return new AgentConversation(
        configuration,
        currentContext,
        systemMessage,
        previousTurns,
        completedTurn,
        invocationStartIterationKey,
        precedingConfigurationFingerprint);
  }

  /**
   * Closes the current (already-ingested) turn into {@link #previousTurns} and opens a new pending
   * turn for a continuation round within this same invocation (e.g. an Anthropic-style {@code
   * pause_turn}). A continuation round carries no new input messages — the model resumes on the
   * existing conversation state — so the new pending turn starts empty; its iterationKey continues
   * from the closed turn's.
   *
   * @throws IllegalStateException if the current turn has not been ingested yet
   */
  public AgentConversation nextContinuationRound() {
    if (currentTurn.assistantMessage() == null) {
      throw new IllegalStateException(
          "nextContinuationRound() called before the current turn was ingested");
    }
    return nextRound(List.of());
  }

  /**
   * Closes the current (already-ingested) turn into {@link #previousTurns} and opens a new pending
   * turn whose input is the given tool call results, answered by the agent itself instead of the
   * process (e.g. a tool call rejected before it ran). The results must cover exactly the tool
   * calls of the closed turn's assistant message, so the turn stays pairable with it when the
   * conversation is persisted, reloaded and windowed. The new turn's iterationKey continues from
   * the closed turn's.
   *
   * @throws IllegalStateException if the current turn has not been ingested yet
   * @throws IllegalArgumentException if the results do not match the assistant message's tool calls
   */
  public AgentConversation nextRuntimeToolRound(ToolCallResultMessage toolCallResults) {
    return nextRuntimeToolRound(toolCallResults, List.of());
  }

  /**
   * Same as {@link #nextRuntimeToolRound(ToolCallResultMessage)}; the new turn's input also holds
   * the given messages that follow the tool call results, such as the message that carries the
   * content of the documents a tool loaded.
   */
  public AgentConversation nextRuntimeToolRound(
      ToolCallResultMessage toolCallResults, List<Message> followUpMessages) {
    if (currentTurn.assistantMessage() == null) {
      throw new IllegalStateException(
          "nextRuntimeToolRound() called before the current turn was ingested");
    }
    var expectedIds = currentTurn.toolCallsById().keySet();
    var resultIds = toolCallResults.results().stream().map(r -> r.id()).collect(Collectors.toSet());
    if (expectedIds.isEmpty()
        || toolCallResults.results().size() != expectedIds.size()
        || !expectedIds.equals(resultIds)) {
      throw new IllegalArgumentException(
          "Tool call results %s do not match the tool calls %s of the current turn"
              .formatted(resultIds, expectedIds));
    }
    return nextRound(Stream.concat(Stream.of(toolCallResults), followUpMessages.stream()).toList());
  }

  private AgentConversation nextRound(List<Message> inputMessages) {
    var updatedPreviousTurns = new ArrayList<>(previousTurns);
    updatedPreviousTurns.add(currentTurn);
    var nextTurn =
        new AgentConversationTurn(
            currentTurn.iterationKey() + 1,
            inputMessages,
            null,
            AgentMetrics.empty(),
            currentTurn.configurationFingerprint());
    return new AgentConversation(
        configuration,
        currentContext,
        systemMessage,
        updatedPreviousTurns,
        nextTurn,
        invocationStartIterationKey,
        precedingConfigurationFingerprint);
  }

  /**
   * Returns a new instance with the base agent context updated to reference the stored
   * conversation.
   */
  public AgentConversation withStoredConversation(ConversationContext ref) {
    var updatedCtx = currentContext.withConversation(ref);
    return new AgentConversation(
        configuration,
        updatedCtx,
        systemMessage,
        previousTurns,
        currentTurn,
        invocationStartIterationKey,
        precedingConfigurationFingerprint);
  }

  /**
   * Returns a new instance whose agent context is {@code update} applied to the current one. The
   * update sees the persisted context of this conversation, not the metrics-merged view of {@link
   * #toAgentContext()}.
   */
  public AgentConversation withContextUpdate(UnaryOperator<AgentContext> update) {
    final var updatedContext = update.apply(currentContext);
    if (updatedContext == currentContext) {
      return this;
    }
    return new AgentConversation(
        configuration,
        updatedContext,
        systemMessage,
        previousTurns,
        currentTurn,
        invocationStartIterationKey,
        precedingConfigurationFingerprint);
  }

  /**
   * Returns a new instance whose agent context carries the given results of tool calls the agent
   * answered itself while the other calls of the same assistant message go to the process. The
   * results are merged with the process results when the next turn input is composed.
   */
  public AgentConversation withPendingRuntimeToolCallResults(List<ToolCallResult> results) {
    return new AgentConversation(
        configuration,
        currentContext.withPendingRuntimeToolCallResults(results),
        systemMessage,
        previousTurns,
        currentTurn,
        invocationStartIterationKey,
        precedingConfigurationFingerprint);
  }

  /**
   * Returns the results of tool calls the agent answered itself that still wait for the process
   * results of the same assistant message.
   */
  public List<ToolCallResult> pendingRuntimeToolCallResults() {
    return currentContext.pendingRuntimeToolCallResults();
  }

  /** Returns the composed system message for this invocation, or {@code null} when it was blank. */
  public @Nullable SystemMessage systemMessage() {
    return systemMessage;
  }

  /** Returns all completed turns: previous turns followed by the current turn (if complete). */
  public List<AgentConversationTurn> turns() {
    return allCompletedTurns();
  }

  /** Returns the current turn. Always non-null; pending until {@link #ingest} completes it. */
  public AgentConversationTurn currentTurn() {
    return currentTurn;
  }

  /** Returns the agent instance key of that conversation, or null if it is not existing (yet). */
  public @Nullable AgentInstanceKey agentInstanceKey() {
    return agentInstanceKey;
  }

  /** Returns {@code true} if the agent instance was created with the documents feature. */
  public boolean supportsDocuments() {
    return currentContext.supportsDocuments();
  }

  /** Returns {@code true} if the agent instance has the runtime tools feature. */
  public boolean supportsRuntimeTools() {
    return currentContext.supportsRuntimeTools();
  }

  /**
   * Returns the document registry of the current agent context, which includes the documents that
   * entered the conversation with this invocation's input.
   */
  public DocumentRegistry documentRegistry() {
    return DocumentRegistry.of(currentContext.documents());
  }

  /**
   * Returns the ids of the documents whose content the model reads in the windowed conversation:
   * the documents of the tags marked as loaded in the windowed user messages.
   */
  public Set<String> loadedDocumentIds() {
    return loadedDocumentIds(
        MessageWindowFilter.apply(allMessages(), configuration.contextWindowSize()));
  }

  /**
   * Returns the ids of the documents whose content the model reads in the given messages: the
   * documents of the tags marked as loaded in the user messages.
   */
  public static Set<String> loadedDocumentIds(List<Message> messages) {
    final var ids = new LinkedHashSet<String>();
    for (final var message : messages) {
      if (message instanceof UserMessage userMessage) {
        for (final var content : userMessage.content()) {
          if (content instanceof TextContent text && DocumentReferenceXmlTag.isLoaded(text)) {
            ids.add(Objects.requireNonNull(DocumentReferenceXmlTag.documentIdOf(text)));
          }
        }
      }
    }
    return Set.copyOf(ids);
  }

  /** Returns the documents selected for the final response so far, by key. */
  public Map<String, ResponseDocumentSelection> responseDocuments() {
    return currentContext.responseDocuments();
  }

  /**
   * Returns {@code true} if the input of any turn of this invocation (the first round and every
   * round that followed it) carries an interrupted tool call result.
   */
  public boolean hasInterruptedToolCallResultsInInvocation() {
    return Stream.concat(previousTurns.stream(), Stream.of(currentTurn))
        .filter(turn -> turn.iterationKey() >= invocationStartIterationKey)
        .anyMatch(AgentConversationTurn::hasInterruptedToolCallResults);
  }

  /** Returns the static per-invocation configuration. */
  public AgentConfiguration configuration() {
    return configuration;
  }

  /** Returns the current turn's metrics, or empty metrics while the turn is still pending. */
  public AgentMetrics currentTurnMetrics() {
    if (currentTurn.assistantMessage() == null) {
      return AgentMetrics.empty();
    }
    return currentTurn.metrics();
  }

  /**
   * Returns the flat list of all messages: the system message (when present), all completed turn
   * messages (input + assistant), and the pending current turn's input messages when not yet
   * ingested.
   */
  public List<Message> allMessages() {
    var messages = new ArrayList<Message>();
    if (systemMessage != null) {
      messages.add(systemMessage);
    }
    for (var turn : allCompletedTurns()) {
      messages.addAll(turn.inputMessages());
      messages.add(turn.assistantMessage());
    }
    if (currentTurn.assistantMessage() == null) {
      messages.addAll(currentTurn.inputMessages());
    }
    return List.copyOf(messages);
  }

  /**
   * Applies the context window filter and returns a {@link ConversationSnapshot} ready to send to
   * the LLM. Tool definitions come from {@link #configuration}, not the durable {@link
   * AgentContext} — {@link AgentConfiguration#toolDefinitions()} is the authoritative current tool
   * list for this invocation once the handler has populated it via {@link
   * AgentConfiguration#withToolDefinitions} with the tools of the process and the available runtime
   * tools.
   *
   * <p>For an agent instance with the {@link AgentContext#FEATURE_DOCUMENTS documents feature}, the
   * tags of the loaded documents in the windowed user messages are expanded from the registry of
   * the {@link AgentContext#documents() context} (see {@link DocumentExpander}); every other
   * document stays a tag. When registry documents are not visible in the windowed messages, the
   * snapshot ends with a transient user message listing them (see {@link EvictedDocumentsNote}).
   * For an agent instance without the feature, the documents of the tool results are marked to be
   * rendered as their document reference (see {@link LegacyDocumentRendering}) and the messages are
   * otherwise left as they are. {@link #allMessages()} and the stored conversation are not
   * affected.
   */
  public ConversationSnapshot window(int size, DocumentExpander documentExpander) {
    var windowed = MessageWindowFilter.apply(allMessages(), size);
    final List<Message> messages;
    if (supportsDocuments()) {
      var registry = documentRegistry();
      var expanded = documentExpander.expandMessages(windowed, registry);
      messages = EvictedDocumentsNote.appendTo(expanded, registry);
    } else {
      messages = LegacyDocumentRendering.apply(windowed);
    }
    return new ConversationSnapshot(
        messages,
        DocumentParameterSchema.toProviderToolDefinitions(configuration.toolDefinitions()));
  }

  /**
   * Produces an updated {@link AgentContext} with cumulative metrics from all turns ingested in
   * this invocation applied on top of the base context metrics, {@code lastIterationKey} stamped to
   * the current turn's key, and a new {@code configurationFingerprintHistory} entry recorded when
   * the current turn's {@link AgentConversationTurn#configurationFingerprint()} differs from the
   * turn preceding it — once the current turn has been ingested.
   */
  public AgentContext toAgentContext() {
    var withMetrics = currentContext.withMetrics(totalMetrics());
    var metadata = currentContext.metadata();
    if (metadata == null || currentTurn.assistantMessage() == null) {
      return withMetrics;
    }
    var updatedMetadata = metadata.withLastIterationKey(currentTurn.iterationKey());
    if (!Objects.equals(
        currentTurn.configurationFingerprint(), precedingConfigurationFingerprint)) {
      var history = new LinkedHashMap<>(updatedMetadata.configurationFingerprintHistory());
      history.put(invocationStartIterationKey, currentTurn.configurationFingerprint());
      updatedMetadata = updatedMetadata.withConfigurationFingerprintHistory(history);
    }
    return withMetrics.withMetadata(updatedMetadata);
  }

  /** Returns the last completed turn, or empty if no turns have been completed yet. */
  public Optional<AgentConversationTurn> lastTurn() {
    var all = allCompletedTurns();
    return all.isEmpty() ? Optional.empty() : Optional.of(all.getLast());
  }

  private List<AgentConversationTurn> allCompletedTurns() {
    if (currentTurn.assistantMessage() == null) {
      return previousTurns;
    }
    var all = new ArrayList<>(previousTurns);
    all.add(currentTurn);
    return List.copyOf(all);
  }

  /**
   * Returns the metrics accrued during THIS invocation: the current turn's delta plus any
   * continuation-round and runtime tool round turns rolled into {@link #previousTurns} within this
   * invocation. Durable turns reconstructed by {@link TurnReconstructor} carry empty metrics, so
   * turns from prior invocations contribute zero here. This is the per-job delta pushed to the
   * agent instance (the engine applies it additively): it must cover the whole invocation — not
   * just the final continuation round — and must exclude the cumulative base-context counter.
   */
  public AgentMetrics jobMetrics() {
    var sum = AgentMetrics.empty();
    for (var turn : previousTurns) {
      sum = sum.add(turn.metrics());
    }
    return sum.add(currentTurnMetrics());
  }

  /**
   * Returns cumulative metrics: the base context metrics plus {@link #jobMetrics()} (every previous
   * turn's metrics plus the current turn's delta). Durable turns reconstructed by {@link
   * TurnReconstructor} always carry empty metrics (the cumulative counter lives on the base context
   * instead), so they contribute zero here; the only {@link #previousTurns} entries with non-empty
   * metrics are those rolled in by {@link #nextContinuationRound} or {@link #nextRuntimeToolRound}
   * within this invocation.
   */
  public AgentMetrics totalMetrics() {
    return currentContext.metrics().add(jobMetrics());
  }
}
