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
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
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
      return new PreviousConversation(Optional.empty(), List.of(), List.of());
    }

    Optional<SystemMessage> systemMessage =
        messages.getFirst() instanceof SystemMessage sm ? Optional.of(sm) : Optional.empty();
    var body = messages.subList(systemMessage.isPresent() ? 1 : 0, messages.size());

    var assistantIndices =
        IntStream.range(0, body.size())
            .filter(i -> body.get(i) instanceof AssistantMessage)
            .boxed()
            .toList();

    // Messages after the last assistant message belong to an open (in-progress) turn whose
    // assistant response has not been produced yet — a mixed turn where an in-process tool result
    // was recorded while an external (BPMN) tool call is still being dispatched. Only tool-call
    // results are valid in that position; anything else means the stored conversation is malformed.
    int lastAssistant = assistantIndices.isEmpty() ? -1 : assistantIndices.getLast();
    var pendingInputMessages = List.copyOf(body.subList(lastAssistant + 1, body.size()));
    if (!pendingInputMessages.stream().allMatch(m -> m instanceof ToolCallResultMessage)) {
      throw new IllegalStateException(
          "Stored conversation ends with a non-assistant message at index "
              + (messages.size() - 1)
              + ": "
              + body.getLast().getClass().getSimpleName());
    }

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

    return new PreviousConversation(systemMessage, turns, pendingInputMessages);
  }
}
