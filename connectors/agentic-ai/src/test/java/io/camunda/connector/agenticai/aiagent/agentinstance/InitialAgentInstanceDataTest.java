/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration.OpenAiModel;
import io.camunda.connector.api.outbound.JobContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InitialAgentInstanceDataTest {

  private static final long ELEMENT_INSTANCE_KEY = 42L;
  private static final OpenAiProviderConfiguration OPENAI_PROVIDER =
      new OpenAiProviderConfiguration(
          new OpenAiConnection(
              new OpenAiAuthentication("api-key", null, null),
              null,
              new OpenAiModel("gpt-4o", null)));

  @Mock private AgentExecutionContext executionContext;
  @Mock private JobContext jobContext;

  @BeforeEach
  void setUp() {
    when(executionContext.jobContext()).thenReturn(jobContext);
    when(jobContext.getElementInstanceKey()).thenReturn(ELEMENT_INSTANCE_KEY);
  }

  @Test
  void shouldCreateParamsFromOpenAiProviderContext() {
    when(executionContext.provider()).thenReturn(OPENAI_PROVIDER);
    when(executionContext.systemPrompt())
        .thenReturn(new SystemPromptConfiguration("You are a helpful assistant."));
    when(executionContext.limits()).thenReturn(new LimitsConfiguration(10));

    final var params = InitialAgentInstanceData.from(executionContext);

    assertThat(params.elementInstanceKey()).isEqualTo(ELEMENT_INSTANCE_KEY);
    assertThat(params.model()).isEqualTo("gpt-4o");
    assertThat(params.provider()).isEqualTo(OpenAiProviderConfiguration.OPENAI_ID);
    assertThat(params.systemPrompt()).isEqualTo("You are a helpful assistant.");
    assertThat(params.limits()).isNotNull();
    assertThat(params.limits().maxModelCalls()).isEqualTo(10);
  }

  @Test
  void shouldProduceNullLimitsWhenLimitsIsNull() {
    when(executionContext.provider()).thenReturn(OPENAI_PROVIDER);
    when(executionContext.limits()).thenReturn(null);

    final var params = InitialAgentInstanceData.from(executionContext);

    assertThat(params.limits()).isNull();
  }

  @Test
  void shouldPropagateMaxModelCallsWhenLimitsIsNonNull() {
    when(executionContext.provider()).thenReturn(OPENAI_PROVIDER);
    when(executionContext.limits()).thenReturn(new LimitsConfiguration(10));

    final var params = InitialAgentInstanceData.from(executionContext);

    assertThat(params.limits()).isNotNull();
    assertThat(params.limits().maxModelCalls()).isEqualTo(10);
  }

  @Test
  void shouldProduceNullSystemPromptWhenSystemPromptIsNull() {
    when(executionContext.provider()).thenReturn(OPENAI_PROVIDER);
    when(executionContext.systemPrompt()).thenReturn(null);

    final var params = InitialAgentInstanceData.from(executionContext);

    assertThat(params.systemPrompt()).isNull();
  }
}
