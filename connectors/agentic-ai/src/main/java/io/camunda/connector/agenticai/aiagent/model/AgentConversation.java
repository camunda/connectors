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
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.MessageUtil;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
  private final List<ConversationTurn> turns;
  private final @Nullable List<Message> pendingInputMessages;
  private final AgentContext currentContext;
  private final AgentInvocationInput invocationInput;
  private final AgentConfiguration configuration;
  private final AgentMetrics metricsDelta;

  private AgentConversation(
      @Nullable SystemMessage systemMessage,
      List<ConversationTurn> turns,
      @Nullable List<Message> pendingInputMessages,
      AgentContext currentContext,
      AgentInvocationInput invocationInput,
      AgentConfiguration configuration,
      AgentMetrics metricsDelta) {
    this.systemMessage = systemMessage;
    this.turns = List.copyOf(turns);
    this.pendingInputMessages =
        pendingInputMessages == null ? null : List.copyOf(pendingInputMessages);
    this.currentContext = currentContext;
    this.invocationInput = invocationInput;
    this.configuration = configuration;
    this.metricsDelta = metricsDelta;
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
        configuration,
        AgentMetrics.empty());
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
        newSysMsg,
        turns,
        pendingInputMessages,
            currentContext,
        invocationInput,
        configuration,
        metricsDelta);
  }

  /**
   * Returns a new instance with the given messages set as the pending input for the next LLM call.
   */
  public AgentConversation addNextTurn(List<Message> inputMessages) {
    return new AgentConversation(
        systemMessage,
        turns,
        inputMessages,
            currentContext,
        invocationInput,
        configuration,
        metricsDelta);
  }

  /**
   * Completes the current pending turn by recording the assistant response and token usage. Clears
   * {@code pendingInputMessages} and appends a new {@link ConversationTurn}.
   *
   * @throws IllegalStateException if called before {@link #addNextTurn}
   */
  public AgentConversation ingest(
      AssistantMessage assistantMessage, AgentMetrics.TokenUsage tokenUsage) {
    if (pendingInputMessages == null) {
      throw new IllegalStateException("ingest() called before applyInput()");
    }
    int nextKey = turns.size() + 1;
    int toolCallCount =
        assistantMessage.toolCalls() == null ? 0 : assistantMessage.toolCalls().size();
    var turnMetrics =
        AgentMetrics.builder()
            .modelCalls(1)
            .tokenUsage(tokenUsage)
            .toolCalls(toolCallCount)
            .build();
    var newTurn =
        new ConversationTurn(nextKey, pendingInputMessages, assistantMessage, turnMetrics);
    var newTurns = new ArrayList<>(turns);
    newTurns.add(newTurn);
    var newDelta =
        metricsDelta
            .incrementModelCalls(1)
            .incrementTokenUsage(tokenUsage)
            .incrementToolCalls(toolCallCount);
    return new AgentConversation(
        systemMessage, newTurns, null, currentContext, invocationInput, configuration, newDelta);
  }

  /**
   * Returns a new instance with the base agent context updated to reference the stored
   * conversation.
   */
  public AgentConversation withStoredConversation(ConversationContext ref) {
    var updatedCtx = currentContext.withConversation(ref);
    return new AgentConversation(
        systemMessage,
        turns,
        pendingInputMessages,
        updatedCtx,
        invocationInput,
        configuration,
        metricsDelta);
  }

  // --- query methods ---

  public Optional<SystemMessage> systemMessage() {
    return Optional.ofNullable(systemMessage);
  }

  public List<ConversationTurn> turns() {
    return turns;
  }

  public @Nullable List<Message> pendingInputMessages() {
    return pendingInputMessages;
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
    return metricsDelta;
  }

  /** Returns the agent instance key from metadata, or {@code null} if metadata is absent. */
  public @Nullable Long agentInstanceKey() {
    var metadata = currentContext.metadata();
    return metadata != null ? metadata.agentInstanceKey() : null;
  }

  /** Returns {@code true} if the last turn issued tool calls and results are not yet received. */
  public boolean expectingToolCallResults() {
    return !turns.isEmpty() && turns.getLast().hasToolCalls();
  }

  /**
   * Returns the flat list of all messages: system message (if present), all turn messages (input +
   * assistant), and any pending input messages.
   */
  public List<Message> allMessages() {
    var messages = new ArrayList<Message>();
    if (systemMessage != null) {
      messages.add(systemMessage);
    }
    for (var turn : turns) {
      messages.addAll(turn.inputMessages());
      messages.add(turn.assistantMessage());
    }
    if (pendingInputMessages != null) {
      messages.addAll(pendingInputMessages);
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
            .map(m -> m.contextWindowSize())
            .orElse(DEFAULT_CONTEXT_WINDOW_SIZE);
    var windowed = MessageWindowFilter.apply(allMessages(), windowSize);
    return new ConversationSnapshot(windowed, currentContext.toolDefinitions());
  }

  /** Validates the agent has not exceeded configured model call limits. */
  public ValidationResult checkLimits(LimitsConfiguration limits) {
    int maxModelCalls =
        Optional.ofNullable(limits)
            .map(LimitsConfiguration::maxModelCalls)
            .filter(Objects::nonNull)
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
    var delta = metricsDelta;
    var total =
        currentContext
            .metrics()
            .incrementModelCalls(delta.modelCalls())
            .incrementTokenUsage(delta.tokenUsage())
            .incrementToolCalls(delta.toolCalls());
    return currentContext.withMetrics(total);
  }

  /** Returns the last turn, or empty if no turns have been completed. */
  public Optional<ConversationTurn> lastTurn() {
    return turns.isEmpty() ? Optional.empty() : Optional.of(turns.getLast());
  }
}
