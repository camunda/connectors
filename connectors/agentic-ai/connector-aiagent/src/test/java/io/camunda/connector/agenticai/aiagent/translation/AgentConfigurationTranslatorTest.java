/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.translation;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_CHAT_MODEL;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration.EventHandlingBehavior;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.request.AgentSubProcessRequestData;
import io.camunda.connector.agenticai.aiagent.request.AgentSubProcessResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskRequestData;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.request.EventHandlingTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.request.LimitsTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.request.MemoryStorageTemplateConfiguration.InProcessMemoryStorageConfiguration;
import io.camunda.connector.agenticai.aiagent.request.MemoryTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.request.PromptTemplateConfiguration.SystemPromptTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.request.PromptTemplateConfiguration.UserPromptTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.request.ResponseFormatTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.request.ResponseFormatTemplateConfiguration.JsonResponseFormatTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.request.ResponseFormatTemplateConfiguration.TextResponseFormatTemplateConfiguration;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AgentConfigurationTranslatorTest {

  private static final SystemPromptTemplateConfiguration SYSTEM_PROMPT =
      new SystemPromptTemplateConfiguration("Be nice.");
  private static final UserPromptTemplateConfiguration USER_PROMPT =
      new UserPromptTemplateConfiguration("Hello", null);

  private static AgentConfiguration translateTask(
      @Nullable MemoryTemplateConfiguration memory,
      @Nullable LimitsTemplateConfiguration limits,
      @Nullable AgentTaskResponseConfiguration response) {
    return AgentConfigurationTranslator.translate(
        TEST_CHAT_MODEL,
        new AgentTaskRequestData(null, SYSTEM_PROMPT, USER_PROMPT, null, memory, limits, response));
  }

  private static AgentConfiguration translateSubProcess(
      @Nullable EventHandlingTemplateConfiguration events,
      @Nullable AgentSubProcessResponseConfiguration response) {
    return AgentConfigurationTranslator.translate(
        TEST_CHAT_MODEL,
        new AgentSubProcessRequestData(SYSTEM_PROMPT, USER_PROMPT, null, null, events, response));
  }

  private static ResponseFormatConfiguration formatOf(
      ResponseFormatTemplateConfiguration templateFormat) {
    return translateTask(null, null, new AgentTaskResponseConfiguration(templateFormat, null))
        .response()
        .format();
  }

  @Test
  void appliesTheBehaviorOfUnsetOptionsWhenNothingIsConfigured() {
    final var configuration = translateTask(null, null, null);

    assertThat(configuration.chatModel()).isSameAs(TEST_CHAT_MODEL);
    assertThat(configuration.systemPrompt().prompt()).isEqualTo("Be nice.");
    assertThat(configuration.userPrompt().prompt()).isEqualTo("Hello");
    assertThat(configuration.userPrompt().documents()).isEmpty();
    assertThat(configuration.memory().storage())
        .isInstanceOf(InProcessMemoryStorageConfiguration.class);
    assertThat(configuration.contextWindowSize()).isEqualTo(20);
    assertThat(configuration.maxModelCalls()).isEqualTo(10);
    assertThat(configuration.events().behavior())
        .isEqualTo(EventHandlingBehavior.WAIT_FOR_TOOL_CALL_RESULTS);
    assertThat(configuration.response().includeAssistantMessage()).isFalse();
    assertThat(configuration.response().format())
        .isEqualTo(new TextResponseFormatConfiguration(false));
  }

  @Test
  void translatesConfiguredValues() {
    final var configuration =
        translateTask(
            new MemoryTemplateConfiguration(new InProcessMemoryStorageConfiguration(), 5),
            new LimitsTemplateConfiguration(3),
            new AgentTaskResponseConfiguration(
                new TextResponseFormatTemplateConfiguration(true), true));

    assertThat(configuration.contextWindowSize()).isEqualTo(5);
    assertThat(configuration.maxModelCalls()).isEqualTo(3);
    assertThat(configuration.response().includeAssistantMessage()).isTrue();
    assertThat(configuration.response().format())
        .isEqualTo(new TextResponseFormatConfiguration(true));
  }

  @Test
  void partiallyConfiguredOptionsFallBackPerValue() {
    final var configuration =
        translateTask(
            new MemoryTemplateConfiguration(null, null),
            new LimitsTemplateConfiguration(null),
            new AgentTaskResponseConfiguration(
                new TextResponseFormatTemplateConfiguration(null), null));

    assertThat(configuration.memory().storage())
        .isInstanceOf(InProcessMemoryStorageConfiguration.class);
    assertThat(configuration.contextWindowSize()).isEqualTo(20);
    assertThat(configuration.maxModelCalls()).isEqualTo(10);
    assertThat(configuration.response().includeAssistantMessage()).isFalse();
    assertThat(configuration.response().format())
        .isEqualTo(new TextResponseFormatConfiguration(false));
  }

  @Test
  void jsonFormatKeepsSchemaAndTreatsAnEmptySchemaAsNone() {
    final var schema = Map.<String, Object>of("type", "object");

    final var withSchema =
        (JsonResponseFormatConfiguration)
            formatOf(new JsonResponseFormatTemplateConfiguration(schema, "Answer"));
    assertThat(withSchema.schema()).isEqualTo(schema);
    assertThat(withSchema.schemaName()).isEqualTo("Answer");
    assertThat(withSchema.hasSchema()).isTrue();

    final var noSchema =
        (JsonResponseFormatConfiguration)
            formatOf(new JsonResponseFormatTemplateConfiguration(null, null));
    assertThat(noSchema.hasSchema()).isFalse();

    final var emptySchema =
        (JsonResponseFormatConfiguration)
            formatOf(new JsonResponseFormatTemplateConfiguration(Map.of(), null));
    assertThat(emptySchema.hasSchema()).isFalse();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void usesTheDefaultSchemaNameIfNullOrBlank(@Nullable String schemaName) {
    final var format =
        (JsonResponseFormatConfiguration)
            formatOf(
                new JsonResponseFormatTemplateConfiguration(Map.of("type", "object"), schemaName));

    assertThat(format.schemaName()).isEqualTo("Response");
  }

  @Test
  void taskFlavorHasNoEventHandlingAndWaitsForToolCallResults() {
    assertThat(translateTask(null, null, null).events().behavior())
        .isEqualTo(EventHandlingBehavior.WAIT_FOR_TOOL_CALL_RESULTS);
  }

  @Test
  void subProcessFlavorTranslatesEventHandling() {
    assertThat(translateSubProcess(null, null).events().behavior())
        .isEqualTo(EventHandlingBehavior.WAIT_FOR_TOOL_CALL_RESULTS);
    assertThat(
            translateSubProcess(
                    new EventHandlingTemplateConfiguration(
                        EventHandlingBehavior.INTERRUPT_TOOL_CALLS),
                    null)
                .events()
                .behavior())
        .isEqualTo(EventHandlingBehavior.INTERRUPT_TOOL_CALLS);
  }

  @Test
  void unsetOptionsYieldTheSameFingerprintAsTheirExplicitDefaults() {
    final var unset = translateTask(null, null, null).fingerprint();
    final var explicit =
        translateTask(
                new MemoryTemplateConfiguration(new InProcessMemoryStorageConfiguration(), 20),
                new LimitsTemplateConfiguration(10),
                new AgentTaskResponseConfiguration(
                    new TextResponseFormatTemplateConfiguration(false), false))
            .fingerprint();

    assertThat(unset).isEqualTo(explicit);
    assertThat(List.of(unset)).doesNotContainNull();
  }
}
