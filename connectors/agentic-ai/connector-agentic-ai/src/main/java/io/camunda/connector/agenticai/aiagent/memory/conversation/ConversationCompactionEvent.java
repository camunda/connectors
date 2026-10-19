/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import org.jspecify.annotations.Nullable;

/** Describes a compaction that occurred while the conversation store persisted a snapshot. */
public record ConversationCompactionEvent(
    String eventId, @Nullable Integer savedTokens, @Nullable String message) {

  public ConversationCompactionEvent {
    if (eventId == null || eventId.isBlank()) {
      throw new IllegalArgumentException("A conversation compaction event requires an event ID.");
    }
    if (savedTokens != null && savedTokens < 0) {
      throw new IllegalArgumentException("Saved token count cannot be negative.");
    }
  }
}
