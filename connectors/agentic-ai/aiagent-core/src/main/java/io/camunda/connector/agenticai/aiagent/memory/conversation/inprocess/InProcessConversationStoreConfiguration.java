/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess;

import io.camunda.connector.agenticai.aiagent.model.request.ConversationStoreConfiguration;

/** Configuration of the in-process conversation store, which has no settings. */
public record InProcessConversationStoreConfiguration() implements ConversationStoreConfiguration {

  @Override
  public String storeType() {
    return InProcessConversationStore.TYPE;
  }
}
