/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.model.message.AssistantMessage;

/**
 * Result of {@link ChatClient#chat}. Carries the agent context with model-call metrics and token
 * usage already incremented, plus the assistant message produced by the underlying {@link
 * ChatModelApi}. Replaces {@code AiFrameworkChatResponse} at the {@code BaseAgentRequestHandler}
 * call site so the cutover stays a 1:1 swap.
 *
 * <p>Part of the ADR-004 Phase 1 SPI scaffolding. Wired by ChatClientImpl, dispatched via
 * ChatModelApiRegistry.
 */
public record ChatClientResult(AgentContext agentContext, AssistantMessage assistantMessage) {}
