/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.message.Message;
import java.util.List;

/** The input messages of the next turn, ready to be applied to the conversation. */
public record TurnInput(List<Message> messages) {

  public TurnInput {
    messages = List.copyOf(messages);
  }
}
