/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.aiagent.memory.runtime.SlidingMessagesWindow;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Immutable domain aggregate representing the agent's conversation state for one turn. The single
 * source of truth for conversation messages.
 *
 * <p>{@code context} and {@code messages} are <em>restored</em> from persisted state at the start
 * of each turn. {@code engineToolCallResults} is <em>this turn's new engine input</em>, primed to
 * be folded in by the message composer. {@code addedMessages} is the frozen delta of
 * user/tool/event messages the composer built this turn (excludes the system message).
 */
public final class AgentConversation {

  private final AgentContext context;
  private final List<Message> messages;
  private final List<Message> addedMessages;
  private final List<ToolCallResult> engineToolCallResults;

  private AgentConversation(
      AgentContext context,
      List<Message> messages,
      List<Message> addedMessages,
      List<ToolCallResult> engineToolCallResults) {
    this.context = context;
    this.messages = List.copyOf(messages);
    this.addedMessages = List.copyOf(addedMessages);
    this.engineToolCallResults = List.copyOf(engineToolCallResults);
  }

  /**
   * Builds the aggregate from persisted state and this turn's engine input.
   *
   * @param context the durable agent context restored from the process variable
   * @param loadedMessages messages restored from the conversation store
   * @param engineToolCallResults this turn's new tool-call results from the engine, to be folded in
   * @return a rehydrated {@code AgentConversation}
   */
  public static AgentConversation start(
      AgentContext context,
      List<Message> loadedMessages,
      List<ToolCallResult> engineToolCallResults) {
    return new AgentConversation(context, loadedMessages, List.of(), engineToolCallResults);
  }

  /**
   * Returns a new instance that applies the system-message upsert to {@code messages}, appends
   * {@code turnMessages}, and freezes {@code addedMessages = turnMessages}.
   *
   * <p>System-message semantics: if a {@link SystemMessage} already exists it is replaced only when
   * different; otherwise the new system message is prepended at front. Non-system messages are
   * appended. {@code addedMessages} is the frozen {@code turnMessages} list and therefore never
   * contains the system message.
   *
   * @param systemMessage optional system message to upsert; null means no change
   * @param turnMessages the user/tool/event messages for this turn
   */
  public AgentConversation withTurn(
      @Nullable SystemMessage systemMessage, List<Message> turnMessages) {
    final var newMessages = new ArrayList<>(messages);

    if (systemMessage != null) {
      final var existing =
          newMessages.stream().filter(m -> m instanceof SystemMessage).findFirst().orElse(null);
      if (existing != null) {
        if (!existing.equals(systemMessage)) {
          newMessages.set(newMessages.indexOf(existing), systemMessage);
        }
        // identical system message: keep original instance (preserves isSameAs identity)
      } else {
        newMessages.addFirst(systemMessage);
      }
    }

    newMessages.addAll(turnMessages);
    return new AgentConversation(context, newMessages, turnMessages, engineToolCallResults);
  }

  /**
   * Returns a new instance that ingests the LLM response: appends the assistant message and folds
   * all metrics ({@code modelCalls+1}, {@code tokenUsage+=usage}, {@code toolCalls+=size}) into the
   * context. {@code addedMessages} is not changed (its meaning must not drift post-append).
   */
  public AgentConversation ingest(
      AssistantMessage assistantMessage, AgentMetrics.TokenUsage tokenUsage) {
    Objects.requireNonNull(assistantMessage);
    Objects.requireNonNull(tokenUsage);

    final int toolCallsDelta =
        assistantMessage.hasToolCalls() ? assistantMessage.toolCalls().size() : 0;

    final var updatedMetrics =
        context
            .metrics()
            .incrementModelCalls(1)
            .incrementTokenUsage(tokenUsage)
            .incrementToolCalls(toolCallsDelta);

    final var updatedContext = context.withMetrics(updatedMetrics);
    final var newMessages = new ArrayList<>(messages);
    newMessages.add(assistantMessage);
    return new AgentConversation(updatedContext, newMessages, addedMessages, engineToolCallResults);
  }

  /** Returns a new instance with an updated context (e.g. after storing the conversation). */
  public AgentConversation withContext(AgentContext newContext) {
    return new AgentConversation(newContext, messages, addedMessages, engineToolCallResults);
  }

  /**
   * Applies the windowing strategy and returns an immutable {@link ConversationSnapshot} containing
   * the filtered messages to send to the LLM this turn.
   */
  public ConversationSnapshot window(SlidingMessagesWindow window) {
    return new ConversationSnapshot(window.apply(messages));
  }

  public AgentContext context() {
    return context;
  }

  public AgentState state() {
    return context.state();
  }

  /** All messages (full history). */
  public List<Message> messages() {
    return messages;
  }

  /** The frozen delta of user/tool/event messages added this turn (excludes the system message). */
  public List<Message> addedMessages() {
    return addedMessages;
  }

  public Optional<Message> lastMessage() {
    if (messages.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(messages.getLast());
  }

  public List<ToolCallResult> engineToolCallResults() {
    return engineToolCallResults;
  }
}
