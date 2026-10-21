/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore;

import io.camunda.connector.agenticai.aiagent.model.conversation.ConversationContext;
import io.camunda.connector.agenticai.aiagent.model.conversation.ConversationContextSubtypes;
import java.util.List;

/** Registers the {@link ConversationContext} implementation of the AWS AgentCore store. */
public class AwsAgentCoreConversationContextSubtypes implements ConversationContextSubtypes {

  @Override
  public List<Class<? extends ConversationContext>> subtypes() {
    return List.of(AwsAgentCoreConversationContext.class);
  }
}
