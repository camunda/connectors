/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import io.camunda.connector.agenticai.model.message.Message;
import java.util.List;

/** Result of loading a conversation from a {@link ConversationSession}. */
public record ConversationLoadResult(List<Message> messages) {

  public static ConversationLoadResult of(List<Message> messages) {
    return new ConversationLoadResult(List.copyOf(messages));
  }

  public static ConversationLoadResult empty() {
    return new ConversationLoadResult(List.of());
  }
}
