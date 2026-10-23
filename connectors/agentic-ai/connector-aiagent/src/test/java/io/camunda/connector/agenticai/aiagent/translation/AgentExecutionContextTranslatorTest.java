/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.translation;

import static io.camunda.connector.agenticai.aiagent.ChatModelConfigurationFixture.TEST_CHAT_MODEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.request.AgentSubProcessRequestData;
import io.camunda.connector.agenticai.aiagent.request.AgentSubProcessResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskRequestData;
import io.camunda.connector.agenticai.aiagent.request.PromptTemplateConfiguration.SystemPromptTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.request.PromptTemplateConfiguration.UserPromptTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.request.ToolsConfiguration;
import io.camunda.connector.agenticai.tools.schema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.api.outbound.JobContext;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AgentExecutionContextTranslatorTest {

  private static final SystemPromptTemplateConfiguration SYSTEM_PROMPT =
      new SystemPromptTemplateConfiguration("Be nice.");
  private static final UserPromptTemplateConfiguration USER_PROMPT =
      new UserPromptTemplateConfiguration("Hello", null);

  private final JobContext jobContext = mock(JobContext.class);
  private final ProcessDefinitionAdHocToolElementsResolver resolver =
      mock(ProcessDefinitionAdHocToolElementsResolver.class);

  private AgentTaskRequestData taskData(@Nullable ToolsConfiguration tools) {
    return new AgentTaskRequestData(
        AgentContext.empty(), SYSTEM_PROMPT, USER_PROMPT, tools, null, null, null);
  }

  @ParameterizedTest
  @MethodSource("missingToolCallResults")
  void taskHasNoInitialToolCallResultsWhenNoneAreConfigured(@Nullable ToolsConfiguration tools) {
    final var context =
        AgentExecutionContextTranslator.task(
            jobContext, taskData(tools), TEST_CHAT_MODEL, resolver);

    assertThat(context.initialToolCallResults()).isEmpty();
  }

  @Test
  void taskTakesAgentContextAndConfigurationFromRequest() {
    final var data = taskData(null);

    final var context =
        AgentExecutionContextTranslator.task(jobContext, data, TEST_CHAT_MODEL, resolver);

    assertThat(context.initialAgentContext()).isSameAs(data.context());
    assertThat(context.configuration().chatModel()).isSameAs(TEST_CHAT_MODEL);
    assertThat(context.userPrompt().prompt()).isEqualTo("Hello");
  }

  @ParameterizedTest
  @MethodSource("includeAgentContextCases")
  void subProcessIncludesAgentContextOnlyWhenRequested(
      @Nullable AgentSubProcessResponseConfiguration response, boolean expected) {
    final var data =
        new AgentSubProcessRequestData(SYSTEM_PROMPT, USER_PROMPT, null, null, null, response);

    final var context =
        AgentExecutionContextTranslator.subProcess(
            jobContext, data, AgentContext.empty(), List.of(), List.of(), TEST_CHAT_MODEL);

    assertThat(context.includeAgentContext()).isEqualTo(expected);
  }

  static Stream<ToolsConfiguration> missingToolCallResults() {
    return Stream.of(
        null,
        new ToolsConfiguration("container", null),
        new ToolsConfiguration("container", List.of()));
  }

  static Stream<org.junit.jupiter.params.provider.Arguments> includeAgentContextCases() {
    return Stream.of(
        org.junit.jupiter.params.provider.Arguments.of(null, false),
        org.junit.jupiter.params.provider.Arguments.of(
            new AgentSubProcessResponseConfiguration(null, null, null), false),
        org.junit.jupiter.params.provider.Arguments.of(
            new AgentSubProcessResponseConfiguration(null, null, false), false),
        org.junit.jupiter.params.provider.Arguments.of(
            new AgentSubProcessResponseConfiguration(null, null, true), true));
  }
}
