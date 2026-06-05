/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.model.message.Message;
import java.util.List;

/**
 * The windowed view of the conversation sent to the model this turn — NOT a full copy.
 *
 * <p>Immutable snapshot produced by {@link AgentConversation#window}.
 */
public record ConversationSnapshot(List<Message> messages) {}
