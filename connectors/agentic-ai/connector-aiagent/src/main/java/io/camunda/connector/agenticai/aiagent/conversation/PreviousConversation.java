/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.conversation;

import io.camunda.connector.agenticai.aiagent.memory.runtime.MessageWindowFilter;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reconstructed view of the conversation prior to the current invocation: the optional leading
 * system message and the completed turns. Decoupled from {@link TurnReconstructor} (which is only a
 * helper to rebuild this structure from the stored flat message list) so consumers depend on the
 * domain shape rather than the reconstruction mechanism.
 */
public record PreviousConversation(
    Optional<SystemMessage> systemMessage, List<AgentConversationTurn> turns) {

  public PreviousConversation {
    turns = List.copyOf(turns);
  }

  /**
   * Returns the ids of the documents whose content the model reads in the conversation as it is
   * before this invocation: the system message and the messages of the turns within the context
   * window.
   */
  public Set<DocumentId> documentIdsWithContent(int contextWindowSize) {
    final var messages = new ArrayList<Message>();
    systemMessage.ifPresent(messages::add);
    for (final var turn : turns) {
      messages.addAll(turn.inputMessages());
      if (turn.assistantMessage() != null) {
        messages.add(turn.assistantMessage());
      }
    }
    return MessageUtil.documentIdsWithContent(
        MessageWindowFilter.apply(messages, contextWindowSize));
  }
}
