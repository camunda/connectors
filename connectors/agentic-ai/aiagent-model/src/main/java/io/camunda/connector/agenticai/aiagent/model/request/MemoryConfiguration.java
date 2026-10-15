/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import org.jspecify.annotations.Nullable;

/**
 * Conversation memory settings of an agent: where the conversation is stored and how much of it.
 */
public interface MemoryConfiguration {

  /** The conversation store configuration, or {@code null} to use the default store. */
  @Nullable ConversationStoreConfiguration storage();

  /** Maximum number of recent conversation messages which are passed to the model. */
  @Nullable Integer contextWindowSize();
}
