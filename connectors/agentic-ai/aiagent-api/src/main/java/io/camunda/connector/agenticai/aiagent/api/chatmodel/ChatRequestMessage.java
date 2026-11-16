/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.chatmodel;

/**
 * A message of a chat request, typed by its role. Each role holds only the content blocks that a
 * provider can receive in that role. {@link ChatConversation#of} builds the messages from the
 * persisted messages.
 */
public sealed interface ChatRequestMessage
    permits ChatSystemMessage, ChatUserMessage, ChatAssistantMessage, ChatToolCallResultMessage {}
