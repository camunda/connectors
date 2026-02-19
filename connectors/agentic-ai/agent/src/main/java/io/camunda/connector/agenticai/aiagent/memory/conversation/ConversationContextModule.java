/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import com.fasterxml.jackson.databind.module.SimpleModule;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;

/** Jackson module that registers ConversationContext subtypes for polymorphic deserialization. */
public class ConversationContextModule extends SimpleModule {

  public ConversationContextModule() {
    super("ConversationContextModule");
  }

  @Override
  public void setupModule(SetupContext context) {
    super.setupModule(context);
    context.registerSubtypes(
        InProcessConversationContext.class, CamundaDocumentConversationContext.class);
  }
}
