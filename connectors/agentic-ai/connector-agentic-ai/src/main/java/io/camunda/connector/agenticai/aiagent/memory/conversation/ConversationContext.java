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
 * Storage cursor for a conversation. Contains all data needed to locate and load the conversation
 * from the external store (e.g., a document reference, version number, or branch pointer).
 *
 * <p>This is persisted as part of the {@code agentContext} process variable in Zeebe and acts as
 * the pointer in the write-ahead with pointer-based visibility contract — see {@link
 * ConversationStore}. Implementations must be serializable and self-contained: they must not rely
 * on external state that could change between turns.
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
