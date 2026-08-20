/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Static per-invocation configuration. Built once from AgentExecutionContext at the start of each
 * handler invocation; does not change mid-conversation, with the exception of {@link #tools()},
 * which the handler fills in once tool resolution has run (see {@link #withTools}) and which then
 * becomes the authoritative source of the current tool list for the rest of the invocation.
 */
public record AgentConfiguration(
    ChatModelConfiguration chatModel,
    SystemPromptConfiguration systemPrompt,
    UserPromptConfiguration userPrompt,
    @Nullable MemoryConfiguration memory,
    @Nullable LimitsConfiguration limits,
    @Nullable EventHandlingConfiguration events,
    @Nullable ResponseConfiguration response,
    List<ToolDefinition> tools) {

  public static final int DEFAULT_CONTEXT_WINDOW_SIZE = 20;
  public static final int DEFAULT_MAX_MODEL_CALLS = 10;

  public AgentConfiguration(
      ChatModelConfiguration chatModel,
      SystemPromptConfiguration systemPrompt,
      UserPromptConfiguration userPrompt,
      @Nullable MemoryConfiguration memory,
      @Nullable LimitsConfiguration limits,
      @Nullable EventHandlingConfiguration events,
      @Nullable ResponseConfiguration response) {
    this(chatModel, systemPrompt, userPrompt, memory, limits, events, response, List.of());
  }

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

  /** Returns a copy carrying the given tool definitions in place of the current ones. */
  public AgentConfiguration withTools(List<ToolDefinition> tools) {
    return new AgentConfiguration(
        chatModel, systemPrompt, userPrompt, memory, limits, events, response, tools);
  }

  /**
   * A stable fingerprint over the configuration attributes the engine records on a {@code
   * CONFIGURATION} agent-instance history item (model, provider, system prompt, model-call limit,
   * tools): equal attributes always yield an equal fingerprint. Used both to detect whether the
   * agent instance's recorded configuration is stale, and as that history item's id, so a repeat
   * send with unchanged content dedups for free.
   */
  public String fingerprint() {
    return Integer.toHexString(
        Objects.hash(
            chatModel.model(),
            chatModel.provider(),
            systemPrompt.prompt(),
            limits != null ? limits.maxModelCalls() : null,
            tools));
  }
}
