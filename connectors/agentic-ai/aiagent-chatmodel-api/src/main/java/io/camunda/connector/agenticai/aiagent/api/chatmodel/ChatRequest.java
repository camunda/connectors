/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.chatmodel;

import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;

/**
 * What a {@link ChatModel} needs for one round-trip against a provider: the execution context and
 * the conversation as the provider receives it. The types of the conversation hold no content block
 * that only the agent uses.
 */
public record ChatRequest(AgentExecutionContext executionContext, ChatConversation conversation) {}
