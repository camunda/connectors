/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.chatmodel;

import java.util.List;

/** The tool call results of a chat request, in one message. */
public record ChatToolCallResultMessage(List<ChatToolCallResult> results)
    implements ChatRequestMessage {

  public ChatToolCallResultMessage {
    results = List.copyOf(results);
  }
}
