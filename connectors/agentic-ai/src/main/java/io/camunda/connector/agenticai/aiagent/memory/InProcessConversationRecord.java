/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.connector.agenticai.model.message.Message;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InProcessConversationRecord(List<Message> messages) implements ConversationRecord {
  public InProcessConversationRecord {
    if (messages == null) {
      throw new IllegalArgumentException("Messages cannot be null");
    }
  }

  public static InProcessConversationRecord empty() {
    return new InProcessConversationRecord(List.of());
  }
}
