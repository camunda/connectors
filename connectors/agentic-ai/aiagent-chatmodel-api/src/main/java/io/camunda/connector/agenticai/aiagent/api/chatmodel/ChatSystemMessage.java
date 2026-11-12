/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.chatmodel;

import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import java.util.List;
import java.util.stream.Collectors;

/** The system message of a chat request: text blocks only. */
public record ChatSystemMessage(List<TextContent> content) implements ChatRequestMessage {

  public ChatSystemMessage {
    content = List.copyOf(content);
  }

  /** The texts of the blocks, joined with a line break. */
  public String text() {
    return content.stream().map(TextContent::text).collect(Collectors.joining("\n"));
  }
}
