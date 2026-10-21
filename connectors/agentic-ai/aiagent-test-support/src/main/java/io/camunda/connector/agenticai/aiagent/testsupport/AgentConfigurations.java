/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.testsupport;

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
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import java.util.List;

/**
 * {@link AgentConfiguration}s for tests of the modules below the agent core, which know no
 * conversation store.
 */
public final class AgentConfigurations {

  /** The response configuration of an agent that has none configured: plain text, no parsing. */
  public static final ResponseConfiguration DEFAULT_RESPONSE =
      new ResponseConfiguration(new TextResponseFormatConfiguration(false), false);

  private AgentConfigurations() {}

  /** An agent calling the given chat model, with valid defaults for everything else. */
  public static AgentConfiguration agentConfiguration(final ChatModelConfiguration chatModel) {
    return agentConfiguration(chatModel, TEST_SYSTEM_PROMPT);
  }

  /** An agent calling the given chat model with the given system prompt. */
  public static AgentConfiguration agentConfiguration(
      final ChatModelConfiguration chatModel, final SystemPromptConfiguration systemPrompt) {
    return new AgentConfiguration(
        chatModel,
        systemPrompt,
        new UserPromptConfiguration("user prompt", List.of()),
        new MemoryConfiguration(() -> "test-store", 20),
        new LimitsConfiguration(10),
        new EventHandlingConfiguration(EventHandlingBehavior.WAIT_FOR_TOOL_CALL_RESULTS),
        DEFAULT_RESPONSE,
        List.of());
  }
}
