/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static io.camunda.connector.agenticai.aiagent.testsupport.AgentConfigurationFixture.agentConfiguration;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.CustomChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link AgentConfiguration#fingerprint()} is the change-detection and dedup key for the {@code
 * CONFIGURATION} agent-instance history item: it must stay stable for unchanged configuration and
 * change whenever anything the engine records on that item (model, provider, system prompt,
 * model-call limit, tools) changes.
 */
class AgentConfigurationTest {

  private static AgentConfiguration configuration(
      String model, String systemPrompt, int maxModelCalls, List<ToolDefinition> tools) {
    return agentConfiguration()
        .chatModel(new CustomChatModelConfiguration("test-provider", model, Map.of()))
        .systemPrompt(new SystemPromptConfiguration(systemPrompt))
        .limits(new LimitsConfiguration(maxModelCalls))
        .build()
        .withToolDefinitions(tools);
  }

  private static AgentConfiguration configurationWithChatModel(ChatModelConfiguration chatModel) {
    return agentConfiguration()
        .chatModel(chatModel)
        .systemPrompt(new SystemPromptConfiguration("Be nice."))
        .build();
  }

  @Nested
  class WithToolDefinitions {

    @Test
    void returnsACopyCarryingTheGivenTools() {
      final var original = configuration("gpt-4o", "Be nice.", 10, List.of());
      final var tools =
          List.of(ToolDefinition.builder().name("getWeather").description("d").build());

      final var updated = original.withToolDefinitions(tools);

      assertThat(original.toolDefinitions()).isEmpty();
      assertThat(updated.toolDefinitions()).isEqualTo(tools);
    }
  }

  @Nested
  class Fingerprint {

    @Test
    void equalConfigurationYieldsEqualFingerprint() {
      final var tools =
          List.of(
              ToolDefinition.builder()
                  .name("getWeather")
                  .description("Get the weather forecast")
                  .inputSchema(Map.of("type", "object"))
                  .build());

      final var first = configuration("gpt-4o", "Be nice.", 10, tools).fingerprint();
      final var second = configuration("gpt-4o", "Be nice.", 10, tools).fingerprint();

      assertThat(first).isEqualTo(second);
    }

    @Test
    void changedModelYieldsDifferentFingerprint() {
      final var first = configuration("gpt-4o", "Be nice.", 10, List.of()).fingerprint();
      final var second = configuration("gpt-4o-mini", "Be nice.", 10, List.of()).fingerprint();

      assertThat(first).isNotEqualTo(second);
    }

    @Test
    void changedSystemPromptYieldsDifferentFingerprint() {
      final var first = configuration("gpt-4o", "Be nice.", 10, List.of()).fingerprint();
      final var second = configuration("gpt-4o", "Be mean.", 10, List.of()).fingerprint();

      assertThat(first).isNotEqualTo(second);
    }

    @Test
    void changedMaxModelCallsYieldsDifferentFingerprint() {
      final var first = configuration("gpt-4o", "Be nice.", 10, List.of()).fingerprint();
      final var second = configuration("gpt-4o", "Be nice.", 20, List.of()).fingerprint();

      assertThat(first).isNotEqualTo(second);
    }

    @Test
    void changedToolsYieldsDifferentFingerprint() {
      final var toolsA =
          List.of(ToolDefinition.builder().name("getWeather").description("d").build());
      final var toolsB = List.of(ToolDefinition.builder().name("getTime").description("d").build());

      final var first = configuration("gpt-4o", "Be nice.", 10, toolsA).fingerprint();
      final var second = configuration("gpt-4o", "Be nice.", 10, toolsB).fingerprint();

      assertThat(first).isNotEqualTo(second);
    }

    @Test
    void changedProviderYieldsDifferentFingerprintEvenWithSameModelAndPrompt() {
      final var first =
          configurationWithChatModel(new CustomChatModelConfiguration("acme", "m-1", Map.of()));
      final var second =
          configurationWithChatModel(new CustomChatModelConfiguration("other", "m-1", Map.of()));

      assertThat(first.fingerprint()).isNotEqualTo(second.fingerprint());
    }

    @Test
    void fingerprintIsNeverBlank() {
      assertThat(configuration("gpt-4o", "Be nice.", 10, List.of()).fingerprint()).isNotBlank();
    }

    @Test
    void fingerprintIsACollisionResistantDigest() {
      // guards against a 32-bit Integer.hashCode()-based fingerprint: it doubles as both
      // change-detection and the engine's CONFIGURATION history item id, so a collision would
      // silently suppress a real update or dedup two distinct configurations
      final var fingerprint = configuration("gpt-4o", "Be nice.", 10, List.of()).fingerprint();
      assertThat(fingerprint).matches("[0-9a-f]{64}");
    }
  }
}
