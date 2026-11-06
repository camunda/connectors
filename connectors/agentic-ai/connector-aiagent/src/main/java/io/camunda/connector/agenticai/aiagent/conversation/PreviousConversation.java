/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.conversation;

import io.camunda.connector.agenticai.aiagent.memory.runtime.MessageWindowFilter;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
   * Returns the ids of the documents whose content the model reads in the conversation of the next
   * model call: the system message and the messages of the turns that stay within the context
   * window once the messages this invocation adds are counted.
   *
   * <p>Windowing the previous messages together with {@code addedMessages} counted messages at the
   * end evicts the same previous messages as windowing the previous messages alone with {@code
   * contextWindowSize - addedMessages}; the system message always stays.
   *
   * @param contextWindowSize the maximum number of messages the model call reads
   * @param addedMessages the number of messages this invocation adds that the window counts
   */
  public Set<AgentDocumentId> documentIdsWithContent(int contextWindowSize, int addedMessages) {
    final var messages = new ArrayList<Message>();
    systemMessage.ifPresent(messages::add);
    for (final var turn : turns) {
      messages.addAll(turn.inputMessages());
      if (turn.assistantMessage() != null) {
        messages.add(turn.assistantMessage());
      }
    }
    final int windowOfPreviousMessages =
        Math.max(systemMessage.isPresent() ? 1 : 0, contextWindowSize - addedMessages);
    return documentIdsWithContentIn(MessageWindowFilter.apply(messages, windowOfPreviousMessages));
  }

  /** The ids of the documents whose content the model reads in the given messages. */
  static Set<AgentDocumentId> documentIdsWithContentIn(List<Message> messages) {
    return messages.stream()
        .filter(UserMessage.class::isInstance)
        .map(UserMessage.class::cast)
        .flatMap(message -> message.documentIdsWithContent().stream())
        .collect(Collectors.toUnmodifiableSet());
  }
}
