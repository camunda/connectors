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
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Static per-invocation configuration. Built once from AgentExecutionContext at the start of each
 * handler invocation; does not change mid-conversation.
 */
@NullMarked
public record AgentConfiguration(
    ProviderConfiguration provider,
    SystemPromptConfiguration systemPrompt,
    UserPromptConfiguration userPrompt,
    @Nullable MemoryConfiguration memory,
    @Nullable LimitsConfiguration limits,
    @Nullable EventHandlingConfiguration events,
    @Nullable ResponseConfiguration response,
    @Nullable SandboxConfiguration sandbox) {

  public static final int DEFAULT_CONTEXT_WINDOW_SIZE = 20;
  public static final int DEFAULT_MAX_MODEL_CALLS = 10;

  public int contextWindowSize() {
    return Optional.ofNullable(memory)
        .map(MemoryConfiguration::contextWindowSize)
        .orElse(DEFAULT_CONTEXT_WINDOW_SIZE);
  }

  public int maxModelCalls() {
    return Optional.ofNullable(limits)
        .map(LimitsConfiguration::maxModelCalls)
        .orElse(DEFAULT_MAX_MODEL_CALLS);
  }

  public static final int DEFAULT_MAX_INTERNAL_TOOL_ITERATIONS = 10;

  public int maxInternalToolIterations() {
    return Optional.ofNullable(limits)
        .map(LimitsConfiguration::maxInternalToolIterations)
        .filter(v -> v != null)
        .orElse(DEFAULT_MAX_INTERNAL_TOOL_ITERATIONS);
  }

  /**
   * Returns the sandbox configuration wrapped in an Optional for convenient presence checks.
   * Returns {@link Optional#empty()} when sandbox is {@code null} or {@link
   * SandboxConfiguration.DisabledSandboxConfiguration} (the default element-template value).
   */
  public Optional<SandboxConfiguration> sandboxConfiguration() {
    return Optional.ofNullable(sandbox)
        .filter(s -> !(s instanceof SandboxConfiguration.DisabledSandboxConfiguration));
  }
}
