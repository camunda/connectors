/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A record of a conversation, stored externally. This contains all the data needed to load the
 * conversation again.
 *
 * <p>Subtypes are registered via Jackson module in the agent module.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public interface ConversationContext {
  String conversationId();
}
