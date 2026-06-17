/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.aiagent.agent.ValidationResult;
import io.camunda.connector.agenticai.aiagent.agent.ValidationResult.Violation;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.runtime.MessageWindowFilter;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.MessageUtil;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Immutable domain aggregate representing the agent's full conversation across all turns.
 *
 * <p>Built once per invocation via {@link #rehydrate}, then mutated through copy-on-write methods:
 * {@link #updateSystemMessage}, {@link #addNextTurn}, {@link #ingest}, {@link
 * #withStoredConversation}.
 */
public final class AgentConversation {

  private static final int DEFAULT_CONTEXT_WINDOW_SIZE = 20;
  private static final int DEFAULT_MAX_MODEL_CALLS = 10;
  private static final String ERROR_MAX_MODEL_CALLS = "MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED";

  private final @Nullable SystemMessage systemMessage;
  private final List<ConversationTurn> previousTurns;
  private final @Nullable ConversationTurn currentTurn;
  private final AgentContext currentContext;
  private final AgentInvocationInput invocationInput;
  private final AgentConfiguration configuration;

  private AgentConversation(
      @Nullable SystemMessage systemMessage,
      List<ConversationTurn> previousTurns,
      @Nullable ConversationTurn currentTurn,
      AgentContext currentContext,
      AgentInvocationInput invocationInput,
      AgentConfiguration configuration) {
    this.systemMessage = systemMessage;
    this.previousTurns = List.copyOf(previousTurns);
    this.currentTurn = currentTurn;
    this.currentContext = currentContext;
    this.invocationInput = invocationInput;
    this.configuration = configuration;
  }

  /**
   * Rehydrates a conversation from persisted messages and current invocation inputs.
   *
   * @param loadedMessages flat message list loaded from the conversation store
   * @param agentContext durable agent context restored from the process variable
   * @param invocationInput per-invocation user prompt and engine tool call results
   * @param configuration static per-invocation configuration
   * @return a rehydrated {@code AgentConversation}
   */
  public static AgentConversation rehydrate(
      List<Message> loadedMessages,
      AgentContext agentContext,
      AgentInvocationInput invocationInput,
      AgentConfiguration configuration) {
    var reconstructed = TurnReconstructor.reconstruct(loadedMessages);
    return new AgentConversation(
        reconstructed.systemMessage().orElse(null),
        reconstructed.turns(),
        null,
        agentContext,
        invocationInput,
        configuration);
  }

  /**
   * Returns a new instance with the system message set (or removed if blank).
   *
   * @param composedPrompt the composed system prompt; blank or null removes the system message
   */
  public AgentConversation updateSystemMessage(String composedPrompt) {
    SystemMessage newSysMsg =
        StringUtils.isBlank(composedPrompt)
            ? null
            : SystemMessage.builder()
                .content(MessageUtil.singleTextContent(composedPrompt))
                .build();
    return new AgentConversation(
        newSysMsg, previousTurns, currentTurn, currentContext, invocationInput, configuration);
  }

  /**
   * Returns a new instance with a pending turn holding the given input messages. The turn is
   * completed by {@link #ingest}.
   */
  public AgentConversation addNextTurn(List<Message> inputMessages) {
    int nextKey = previousTurns.size() + 1;
    var pending = new ConversationTurn(nextKey, inputMessages, null, AgentMetrics.empty());
    return new AgentConversation(
        systemMessage, previousTurns, pending, currentContext, invocationInput, configuration);
  }

  /**
   * Completes the pending turn by recording the assistant response and token usage.
   *
   * @throws IllegalStateException if called before {@link #addNextTurn}
   */
  public AgentConversation ingest(
      AssistantMessage assistantMessage, AgentMetrics.TokenUsage tokenUsage) {
    if (currentTurn == null || currentTurn.assistantMessage() != null) {
      throw new IllegalStateException("ingest() called before addNextTurn()");
    }
    int toolCallCount =
        assistantMessage.toolCalls() == null ? 0 : assistantMessage.toolCalls().size();
    var turnMetrics =
        AgentMetrics.builder()
            .modelCalls(1)
            .tokenUsage(tokenUsage)
            .toolCalls(toolCallCount)
            .build();
    var completedTurn = currentTurn.withAssistantMessage(assistantMessage, turnMetrics);
    return new AgentConversation(
        systemMessage,
        previousTurns,
        completedTurn,
        currentContext,
        invocationInput,
        configuration);
  }

  /**
   * Returns a new instance with the base agent context updated to reference the stored
   * conversation.
   */
  public AgentConversation withStoredConversation(ConversationContext ref) {
    var updatedCtx = currentContext.withConversation(ref);
    return new AgentConversation(
        systemMessage, previousTurns, currentTurn, updatedCtx, invocationInput, configuration);
  }

  // --- query methods ---

  public Optional<SystemMessage> systemMessage() {
    return Optional.ofNullable(systemMessage);
  }

  /** Returns all completed turns: previous turns followed by the current turn (if complete). */
  public List<ConversationTurn> turns() {
    return allTurns();
  }

  public Optional<ConversationTurn> currentTurn() {
    return Optional.ofNullable(currentTurn);
  }

  public AgentContext baseAgentContext() {
    return currentContext;
  }

  public AgentInvocationInput invocationInput() {
    return invocationInput;
  }

  public AgentConfiguration configuration() {
    return configuration;
  }

  public AgentMetrics metricsDelta() {
    if (currentTurn == null || currentTurn.assistantMessage() == null) {
      return AgentMetrics.empty();
    }
    return currentTurn.metrics();
  }

  /** Returns the agent instance key from metadata, or {@code null} if metadata is absent. */
  public @Nullable Long agentInstanceKey() {
    var metadata = currentContext.metadata();
    return metadata != null ? metadata.agentInstanceKey() : null;
  }

  /** Returns {@code true} if the last turn issued tool calls and results are not yet received. */
  public boolean expectingToolCallResults() {
    var all = allTurns();
    return !all.isEmpty() && all.getLast().hasToolCalls();
  }

  /**
   * Returns the flat list of all messages: system message (if present), all completed turn messages
   * (input + assistant), and any pending input messages when the current turn is not yet ingested.
   */
  public List<Message> allMessages() {
    var messages = new ArrayList<Message>();
    if (systemMessage != null) {
      messages.add(systemMessage);
    }
    for (var turn : allTurns()) {
      messages.addAll(turn.inputMessages());
      messages.add(turn.assistantMessage());
    }
    if (currentTurn != null && currentTurn.assistantMessage() == null) {
      messages.addAll(currentTurn.inputMessages());
    }
    return List.copyOf(messages);
  }

  /**
   * Applies the context window filter and returns a {@link ConversationSnapshot} ready to send to
   * the LLM.
   */
  public ConversationSnapshot window() {
    int windowSize =
        Optional.ofNullable(configuration.memory())
            .map(MemoryConfiguration::contextWindowSize)
            .orElse(DEFAULT_CONTEXT_WINDOW_SIZE);
    var windowed = MessageWindowFilter.apply(allMessages(), windowSize);
    return new ConversationSnapshot(windowed, currentContext.toolDefinitions());
  }

  /** Validates the agent has not exceeded configured model call limits. */
  public ValidationResult checkLimits(LimitsConfiguration limits) {
    int maxModelCalls =
        Optional.ofNullable(limits)
            .map(LimitsConfiguration::maxModelCalls)
            .orElse(DEFAULT_MAX_MODEL_CALLS);
    int current = currentContext.metrics().modelCalls();
    if (current >= maxModelCalls) {
      return ValidationResult.of(
          List.of(
              new Violation(
                  ERROR_MAX_MODEL_CALLS,
                  "Maximum number of model calls reached (modelCalls: %d, limit: %d)"
                      .formatted(current, maxModelCalls))));
    }
    return ValidationResult.valid();
  }

  /**
   * Produces an updated {@link AgentContext} with cumulative metrics from all turns ingested in
   * this invocation applied on top of the base context metrics.
   */
  public AgentContext toAgentContext() {
    var delta = metricsDelta();
    var total =
        currentContext
            .metrics()
            .incrementModelCalls(delta.modelCalls())
            .incrementTokenUsage(delta.tokenUsage())
            .incrementToolCalls(delta.toolCalls());
    return currentContext.withMetrics(total);
  }

  /** Returns the last completed turn, or empty if no turns have been completed. */
  public Optional<ConversationTurn> lastTurn() {
    var all = allTurns();
    return all.isEmpty() ? Optional.empty() : Optional.of(all.getLast());
  }

  private List<ConversationTurn> allTurns() {
    if (currentTurn == null || currentTurn.assistantMessage() == null) {
      return previousTurns;
    }
    var all = new ArrayList<>(previousTurns);
    all.add(currentTurn);
    return List.copyOf(all);
  }
}
