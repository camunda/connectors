/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.AwsAgentCoreConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;

/**
 * A record of a conversation, stored externally. This contains all the data needed to load the
 * conversation again.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = InProcessConversationContext.class, name = "in-process"),
  @JsonSubTypes.Type(value = CamundaDocumentConversationContext.class, name = "camunda-document"),
  @JsonSubTypes.Type(value = AwsAgentCoreConversationContext.class, name = "aws-agentcore")
})
public interface ConversationContext {
  String conversationId();
}
