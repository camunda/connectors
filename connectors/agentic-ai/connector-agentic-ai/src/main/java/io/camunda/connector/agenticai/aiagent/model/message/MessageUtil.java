/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MessageUtil {
  private MessageUtil() {}

  /**
   * Builder default for {@link Message#id()}; also backfills ids on pre-existing persisted data.
   */
  public static String generateId() {
    return UUID.randomUUID().toString();
  }

  public static List<Content> singleTextContent(String text) {
    return List.of(TextContent.textContent(text));
  }

  public static List<Content> content(Content... contents) {
    return List.of(contents);
  }

  public static Optional<SystemMessage> leadingSystemMessage(List<Message> messages) {
    return !messages.isEmpty() && messages.getFirst() instanceof SystemMessage systemMessage
        ? Optional.of(systemMessage)
        : Optional.empty();
  }
}
