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
import org.jspecify.annotations.Nullable;

public class MessageUtil {
  private MessageUtil() {}

  public static List<Content> singleTextContent(String text) {
    return List.of(TextContent.textContent(text));
  }

  public static List<Content> content(Content... contents) {
    return List.of(contents);
  }

  // Windowing never evicts the system message (see MessageWindowFilter), so it is always the
  // leading message if present.
  public static @Nullable SystemMessage leadingSystemMessage(List<Message> messages) {
    return !messages.isEmpty() && messages.getFirst() instanceof SystemMessage systemMessage
        ? systemMessage
        : null;
  }
}
