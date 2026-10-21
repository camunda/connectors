/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.AD_HOC_TOOL_ELEMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentTaskExecutionContextTest {

  private static final Long PROCESS_DEFINITION_KEY = 123456789L;
  private static final String CONTAINER_ELEMENT_ID = "test-container-element-id";

  @Mock(answer = RETURNS_DEEP_STUBS)
  private JobContext jobContext;

  @Mock private ChatModelConfiguration chatModel;

  @Mock private ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;

  private AgentTaskExecutionContext executionContext(@Nullable ToolsConfiguration tools) {
    return new AgentTaskExecutionContext(
        jobContext,
        new AgentTaskRequestData(
            null,
            new SystemPromptTemplateConfiguration("system prompt"),
            new UserPromptTemplateConfiguration("user prompt", null),
            tools,
            null,
            null,
            null),
        chatModel,
        toolElementsResolver);
  }

  @ParameterizedTest
  @MethodSource("missingToolCallResults")
  void returnsEmptyInitialToolCallResultsWhenToolCallResultsAreMissing(
      ToolsConfiguration toolsConfiguration) {
    final var executionContext = executionContext(toolsConfiguration);

    assertThat(executionContext.initialToolCallResults()).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("missingToolElementId")
  void returnsEmptyToolElementsWhenToolElementIdIsNotConfigured(
      ToolsConfiguration toolsConfiguration) {
    final var executionContext = executionContext(toolsConfiguration);

    assertThat(executionContext.toolElements()).isEmpty();
  }

  @Test
  void loadsToolElementsFromProcessDefinition() {
    final var executionContext =
        executionContext(new ToolsConfiguration(CONTAINER_ELEMENT_ID, List.of()));
    when(jobContext.getProcessDefinitionKey()).thenReturn(PROCESS_DEFINITION_KEY);

    when(toolElementsResolver.resolveToolElements(
            null, PROCESS_DEFINITION_KEY, CONTAINER_ELEMENT_ID))
        .thenReturn(AD_HOC_TOOL_ELEMENTS);

    assertThat(executionContext.toolElements()).containsExactlyElementsOf(AD_HOC_TOOL_ELEMENTS);
  }

  @Test
  void doesNotLoadToolElementsMultipleTimes() {
    final var executionContext =
        executionContext(new ToolsConfiguration(CONTAINER_ELEMENT_ID, List.of()));
    when(jobContext.getProcessDefinitionKey()).thenReturn(PROCESS_DEFINITION_KEY);

    when(toolElementsResolver.resolveToolElements(
            null, PROCESS_DEFINITION_KEY, CONTAINER_ELEMENT_ID))
        .thenReturn(AD_HOC_TOOL_ELEMENTS);

    final var toolElements1 = executionContext.toolElements();
    final var toolElements2 = executionContext.toolElements();
    assertThat(toolElements1)
        .containsExactlyElementsOf(toolElements2)
        .containsExactlyElementsOf(AD_HOC_TOOL_ELEMENTS);

    verify(toolElementsResolver, times(1))
        .resolveToolElements(null, PROCESS_DEFINITION_KEY, CONTAINER_ELEMENT_ID);
  }

  static Stream<ToolsConfiguration> missingToolCallResults() {
    return Stream.of(
        null,
        new ToolsConfiguration(CONTAINER_ELEMENT_ID, null),
        new ToolsConfiguration(CONTAINER_ELEMENT_ID, List.of()));
  }

  static Stream<ToolsConfiguration> missingToolElementId() {
    return Stream.of(
        null,
        new ToolsConfiguration(null, List.of()),
        new ToolsConfiguration("", List.of()),
        new ToolsConfiguration("  ", List.of()));
  }
}
