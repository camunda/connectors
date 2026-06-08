/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import org.springframework.lang.Nullable;

/**
 * Static per-invocation configuration. Built once from AgentExecutionContext at the start of each
 * handler invocation; does not change mid-conversation.
 */
public record AgentConfiguration(
    ProviderConfiguration provider,
    @Nullable SystemPromptConfiguration systemPrompt,
    @Nullable MemoryConfiguration memory,
    @Nullable LimitsConfiguration limits,
    @Nullable EventHandlingConfiguration events,
    @Nullable ResponseConfiguration response) {

  public static AgentConfiguration from(AgentExecutionContext executionContext) {
    return new AgentConfiguration(
        executionContext.provider(),
        executionContext.systemPrompt(),
        executionContext.memory(),
        executionContext.limits(),
        executionContext.events(),
        executionContext.response());
  }
}
