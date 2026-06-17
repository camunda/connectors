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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reconstructs {@link ConversationTurn} list and optional system message from a flat message list.
 */
public final class TurnReconstructor {

  private TurnReconstructor() {}

  public record Result(Optional<SystemMessage> systemMessage, List<ConversationTurn> turns) {}

  public static Result reconstruct(List<Message> messages) {
    if (messages.isEmpty()) {
      return new Result(Optional.empty(), List.of());
    }

    var iter = messages.listIterator();
    Optional<SystemMessage> systemMessage = Optional.empty();

    // extract leading system message
    if (iter.hasNext()) {
      var first = iter.next();
      if (first instanceof SystemMessage sm) {
        systemMessage = Optional.of(sm);
      } else {
        iter.previous(); // put back
      }
    }

    var turns = new ArrayList<ConversationTurn>();
    var currentInput = new ArrayList<Message>();
    int positionIndex = 0;

    while (iter.hasNext()) {
      var message = iter.next();
      if (message instanceof AssistantMessage assistant) {
        positionIndex++;
        turns.add(
            new ConversationTurn(
                positionIndex, List.copyOf(currentInput), assistant, AgentMetrics.empty()));
        currentInput.clear();
      } else {
        currentInput.add(message);
      }
    }
    // trailing non-assistant messages (pending input) are intentionally ignored

    return new Result(systemMessage, List.copyOf(turns));
  }
}
