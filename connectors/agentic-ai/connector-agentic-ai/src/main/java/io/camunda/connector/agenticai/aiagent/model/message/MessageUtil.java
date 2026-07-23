/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public class MessageUtil {
  private MessageUtil() {}

  public static List<Content> singleTextContent(String text) {
    return List.of(TextContent.textContent(text));
  }

  public static List<Content> content(Content... contents) {
    return List.of(contents);
  }

  public static Map<String, Object> defaultMessageMetadata() {
    return Map.of("timestamp", ZonedDateTime.now());
  }
}
