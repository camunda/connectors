/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.model.message.Message;
import java.util.List;
import java.util.Optional;

/**
 * Result of {@link AgentMessagesHandler#addUserMessages}. Carries the messages added to the runtime
 * memory along with an optional partition of matched tool call results, available when the
 * activation was driven by tool call results (as opposed to a fresh user prompt).
 */
public record AddedUserMessagesResult(
    List<Message> messages, Optional<ToolCallResultsPartition> toolCallResultsPartition) {

  public static AddedUserMessagesResult ofMessages(List<Message> messages) {
    return new AddedUserMessagesResult(messages, Optional.empty());
  }

  public static AddedUserMessagesResult ofMessagesAndPartition(
      List<Message> messages, ToolCallResultsPartition partition) {
    return new AddedUserMessagesResult(messages, Optional.of(partition));
  }
}
