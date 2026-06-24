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
import io.camunda.connector.agenticai.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

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

  private final AgentConfiguration configuration;
  private final AgentContext currentContext;
  private final @Nullable AgentInstanceKey agentInstanceKey;
  private final @Nullable SystemMessage systemMessage;
  private final List<AgentConversationTurn> previousTurns;
  private final AgentConversationTurn currentTurn;
  private final DocumentRegistry documentRegistry;

  private AgentConversation(
      AgentConfiguration configuration,
      AgentContext currentContext,
      @Nullable SystemMessage systemMessage,
      List<AgentConversationTurn> previousTurns,
      AgentConversationTurn currentTurn,
      DocumentRegistry documentRegistry) {
    this.configuration = configuration;
    this.currentContext = currentContext;
    this.systemMessage = systemMessage;
    this.previousTurns = List.copyOf(previousTurns);
    this.currentTurn = currentTurn;
    this.agentInstanceKey = AgentInstanceKey.from(currentContext.metadata());
    this.documentRegistry = documentRegistry;
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
      List<Message> inputMessages,
      DocumentRegistry documentRegistry) {
    int nextKey = previousConversation.turns().size() + 1;
    var currentTurn = new AgentConversationTurn(nextKey, inputMessages, null, AgentMetrics.empty());
    return new AgentConversation(
        configuration,
        agentContext,
        systemMessage,
        previousConversation.turns(),
        currentTurn,
        documentRegistry);
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
        documentRegistry);
  }

  /**
   * Returns a new instance with the base agent context updated to reference the stored
   * conversation.
   */
  public AgentConversation withStoredConversation(ConversationContext ref) {
    var updatedCtx = currentContext.withConversation(ref);
    return new AgentConversation(
        configuration, updatedCtx, systemMessage, previousTurns, currentTurn, documentRegistry);
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

  /** Returns the static per-invocation configuration. */
  public AgentConfiguration configuration() {
    return configuration;
  }

  /** Returns the document registry for this conversation. */
  public DocumentRegistry documentRegistry() {
    return documentRegistry;
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
   * the LLM.
   */
  public ConversationSnapshot window(int size) {
    var windowed = MessageWindowFilter.apply(allMessages(), size);
    return new ConversationSnapshot(windowed, currentContext.toolDefinitions());
  }

  /**
   * Produces an updated {@link AgentContext} with cumulative metrics from all turns ingested in
   * this invocation applied on top of the base context metrics.
   */
  public AgentContext toAgentContext() {
    return currentContext.withMetrics(totalMetrics());
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

  /** Returns cumulative metrics: the base context metrics plus the current turn's delta. */
  public AgentMetrics totalMetrics() {
    // it's currently the only total projection, as the TurnReconstructor is always assigning empty
    // metrics per turn
    return currentContext.metrics().add(currentTurnMetrics());
  }
}
