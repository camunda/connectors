/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.AgentConfigurationFixture.agentConfiguration;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.AD_HOC_TOOL_ELEMENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.tools.schema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.api.outbound.JobContext;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentTaskExecutionContextTest {

  private static final Long PROCESS_DEFINITION_KEY = 123456789L;
  private static final String CONTAINER_ELEMENT_ID = "test-container-element-id";

  @Mock(answer = RETURNS_DEEP_STUBS)
  private JobContext jobContext;

  @Mock private ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;

  private AgentTaskExecutionContext executionContext(@Nullable String toolsContainerElementId) {
    return new AgentTaskExecutionContext(
        jobContext,
        agentConfiguration().build(),
        AgentContext.empty(),
        List.of(),
        toolsContainerElementId,
        toolElementsResolver);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  "})
  void returnsEmptyToolElementsWhenToolElementIdIsNotConfigured(
      @Nullable String toolsContainerElementId) {
    final var executionContext = executionContext(toolsContainerElementId);

    assertThat(executionContext.toolElements()).isEmpty();
  }

  @Test
  void loadsToolElementsFromProcessDefinition() {
    final var executionContext = executionContext(CONTAINER_ELEMENT_ID);
    when(jobContext.getProcessDefinitionKey()).thenReturn(PROCESS_DEFINITION_KEY);

    when(toolElementsResolver.resolveToolElements(
            null, PROCESS_DEFINITION_KEY, CONTAINER_ELEMENT_ID))
        .thenReturn(AD_HOC_TOOL_ELEMENTS);

    assertThat(executionContext.toolElements()).containsExactlyElementsOf(AD_HOC_TOOL_ELEMENTS);
  }

  @Test
  void doesNotLoadToolElementsMultipleTimes() {
    final var executionContext = executionContext(CONTAINER_ELEMENT_ID);
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
}
