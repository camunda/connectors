/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.chatmodel;

import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;

/**
 * What a {@link ChatModel} needs for one round-trip against a provider.
 *
 * <p>The snapshot never holds an {@link
 * io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent}: the agent
 * expands every such block into the handle text of its document and, where the document is
 * delivered as content, the document, before it builds the request. A chat model does not need to
 * handle the block.
 */
public record ChatRequest(AgentExecutionContext executionContext, ConversationSnapshot snapshot) {}
