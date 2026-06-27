/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import java.util.List;
import java.util.Optional;

/**
 * Reconstructed view of the conversation prior to the current invocation: the optional leading
 * system message, the completed turns, and any pending input of an open (in-progress) trailing
 * turn. Decoupled from {@link TurnReconstructor} (which is only a helper to rebuild this structure
 * from the stored flat message list) so consumers depend on the domain shape rather than the
 * reconstruction mechanism.
 *
 * <p>{@code pendingInputMessages} are tool-call results that were persisted for an assistant turn
 * whose response is not produced yet — e.g. a mixed turn where an in-process (sandbox) tool call
 * was executed and recorded while an external (BPMN) tool call is still being dispatched through
 * the ad-hoc sub-process. They are carried forward and merged into the next turn's input on
 * re-entry (see {@link AgentConversation#rehydrate}). Normally empty.
 */
public record PreviousConversation(
    Optional<SystemMessage> systemMessage,
    List<AgentConversationTurn> turns,
    List<Message> pendingInputMessages) {

  public PreviousConversation {
    turns = List.copyOf(turns);
    pendingInputMessages = List.copyOf(pendingInputMessages);
  }

  /** Convenience constructor for a conversation with no open (in-progress) trailing turn. */
  public PreviousConversation(
      Optional<SystemMessage> systemMessage, List<AgentConversationTurn> turns) {
    this(systemMessage, turns, List.of());
  }
}
