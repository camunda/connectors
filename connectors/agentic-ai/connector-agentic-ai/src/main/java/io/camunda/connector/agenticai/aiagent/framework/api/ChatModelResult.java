/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;

/**
 * The outcome of a single round-trip performed by a {@link ChatModelApi}: the assistant message
 * produced for the turn and its metrics ({@code modelCalls}, {@code tokenUsage}, {@code toolCalls},
 * and the measured {@code executionTime}).
 */
public record ChatModelResult(AssistantMessage assistantMessage, AgentMetrics metrics) {}
