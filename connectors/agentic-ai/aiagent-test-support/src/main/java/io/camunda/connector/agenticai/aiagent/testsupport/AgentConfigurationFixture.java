/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.testsupport;

import static io.camunda.connector.agenticai.aiagent.testsupport.AgentConfigurations.TEST_CHAT_MODEL;
import static io.camunda.connector.agenticai.aiagent.testsupport.TestMessagesFixture.TEST_SYSTEM_PROMPT;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration.EventHandlingBehavior;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;

/**
 * Builds {@link AgentConfiguration} instances for tests. Everything a test does not set is a valid
 * default, so tests only state what they care about.
 */
public final class AgentConfigurationFixture {

  /** The response configuration of an agent that has none configured: plain text, no parsing. */
  public static final ResponseConfiguration DEFAULT_RESPONSE = AgentConfigurations.DEFAULT_RESPONSE;

  private ChatModelConfiguration chatModel = TEST_CHAT_MODEL;
  private SystemPromptConfiguration systemPrompt = TEST_SYSTEM_PROMPT;
  private UserPromptConfiguration userPrompt =
      new UserPromptConfiguration("user prompt", List.of());
  private MemoryConfiguration memory =
      new MemoryConfiguration(new TestConversationStoreConfiguration(), 20);
  private LimitsConfiguration limits = new LimitsConfiguration(10);
  private EventHandlingConfiguration events =
      new EventHandlingConfiguration(EventHandlingBehavior.WAIT_FOR_TOOL_CALL_RESULTS);
  private ResponseConfiguration response = DEFAULT_RESPONSE;
  private List<ToolDefinition> toolDefinitions = List.of();

  private AgentConfigurationFixture() {}

  public static AgentConfigurationFixture agentConfiguration() {
    return new AgentConfigurationFixture();
  }

  public AgentConfigurationFixture chatModel(ChatModelConfiguration chatModel) {
    this.chatModel = chatModel;
    return this;
  }

  public AgentConfigurationFixture systemPrompt(SystemPromptConfiguration systemPrompt) {
    this.systemPrompt = systemPrompt;
    return this;
  }

  public AgentConfigurationFixture userPrompt(UserPromptConfiguration userPrompt) {
    this.userPrompt = userPrompt;
    return this;
  }

  public AgentConfigurationFixture memory(MemoryConfiguration memory) {
    this.memory = memory;
    return this;
  }

  public AgentConfigurationFixture limits(LimitsConfiguration limits) {
    this.limits = limits;
    return this;
  }

  public AgentConfigurationFixture events(EventHandlingConfiguration events) {
    this.events = events;
    return this;
  }

  public AgentConfigurationFixture response(ResponseConfiguration response) {
    this.response = response;
    return this;
  }

  public AgentConfigurationFixture toolDefinitions(List<ToolDefinition> toolDefinitions) {
    this.toolDefinitions = toolDefinitions;
    return this;
  }

  public AgentConfiguration build() {
    return new AgentConfiguration(
        chatModel, systemPrompt, userPrompt, memory, limits, events, response, toolDefinitions);
  }
}
