/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Reconstructs a {@link PreviousConversation} (optional system message + completed turns) from a
 * flat message list.
 */
public final class TurnReconstructor {

  private TurnReconstructor() {}

  public static PreviousConversation reconstruct(List<Message> messages) {
    if (messages.isEmpty()) {
      return new PreviousConversation(Optional.empty(), List.of());
    }

    Optional<SystemMessage> systemMessage =
        messages.getFirst() instanceof SystemMessage sm ? Optional.of(sm) : Optional.empty();
    var body = messages.subList(systemMessage.isPresent() ? 1 : 0, messages.size());

    if (!body.isEmpty() && !(body.getLast() instanceof AssistantMessage)) {
      throw new IllegalStateException(
          "Stored conversation ends with a non-assistant message at index "
              + (messages.size() - 1)
              + ": "
              + body.getLast().getClass().getSimpleName());
    }

    var assistantIndices =
        IntStream.range(0, body.size())
            .filter(i -> body.get(i) instanceof AssistantMessage)
            .boxed()
            .toList();

    var turns =
        IntStream.range(0, assistantIndices.size())
            .mapToObj(
                n -> {
                  int end = assistantIndices.get(n);
                  int start = n == 0 ? 0 : assistantIndices.get(n - 1) + 1;
                  return new AgentConversationTurn(
                      n + 1,
                      List.copyOf(body.subList(start, end)),
                      (AssistantMessage) body.get(end),
                      AgentMetrics.empty());
                })
            .toList();

    return new PreviousConversation(systemMessage, turns);
  }
}
