/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agentinstance;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep2;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep3;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep4;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep5;
import io.camunda.client.api.response.CreateAgentInstanceResponse;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.AiAgentProperties.AgentInstanceProperties.RetriesProperties;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.runtime.test.outbound.TestJobContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CamundaAgentInstanceClientTest {

  private static final RetriesProperties RETRIES_CONFIGURATION =
      new RetriesProperties(4, Duration.ofSeconds(1));

  private static final long ELEMENT_INSTANCE_KEY = 77L;

  @Mock private CamundaClient camundaClient;
  @Mock private CreateAgentInstanceCommandStep1 step1;
  @Mock private CreateAgentInstanceCommandStep2 step2;
  @Mock private CreateAgentInstanceCommandStep3 step3;
  @Mock private CreateAgentInstanceCommandStep4 step4;
  @Mock private CreateAgentInstanceCommandStep5 step5;
  @Mock private CreateAgentInstanceResponse response;

  private List<Duration> recordedSleeps;
  private CamundaAgentInstanceClient client;

  @BeforeEach
  void setUp() {
    recordedSleeps = new ArrayList<>();
    client =
        new CamundaAgentInstanceClient(camundaClient, RETRIES_CONFIGURATION) {
          @Override
          protected void sleep(Duration delay) {
            recordedSleeps.add(delay);
            // Do not actually sleep in tests
          }
        };
  }

  private void setupCommandChain() {
    when(camundaClient.newCreateAgentInstanceCommand()).thenReturn(step1);
    when(step1.elementInstanceKey(anyLong())).thenReturn(step2);
    when(step2.model(anyString())).thenReturn(step3);
    when(step3.provider(anyString())).thenReturn(step4);
    when(step4.systemPrompt(anyString())).thenReturn(step5);
    when(step5.maxModelCalls(anyInt())).thenReturn(step5);
  }

  @Test
  void shouldReturnAgentInstanceKeyOnFirstSuccessfulAttempt() {
    setupCommandChain();
    when(step5.execute()).thenReturn(response);
    when(response.getAgentInstanceKey()).thenReturn(12345L);

    final AgentInstanceKey key = client.create(TestAgentExecutionContext.withLimits());

    assertThat(key).isEqualTo(AgentInstanceKey.of(12345L));
    assertThat(recordedSleeps).isEmpty();
    verify(camundaClient, times(1)).newCreateAgentInstanceCommand();
  }

  @Test
  void shouldReturnAgentInstanceKeyOnFirstAttemptWhenMaxModelCallsIsNull() {
    when(camundaClient.newCreateAgentInstanceCommand()).thenReturn(step1);
    when(step1.elementInstanceKey(anyLong())).thenReturn(step2);
    when(step2.model(anyString())).thenReturn(step3);
    when(step3.provider(anyString())).thenReturn(step4);
    when(step4.systemPrompt(anyString())).thenReturn(step5);
    when(step5.execute()).thenReturn(response);
    when(response.getAgentInstanceKey()).thenReturn(67890L);

    final AgentInstanceKey key = client.create(TestAgentExecutionContext.withoutLimits());

    assertThat(key).isEqualTo(AgentInstanceKey.of(67890L));
    assertThat(recordedSleeps).isEmpty();
    verify(camundaClient, times(1)).newCreateAgentInstanceCommand();
  }

  @Test
  void shouldThrowConnectorExceptionImmediatelyForHttp400PermanentError() {
    setupCommandChain();
    when(step5.execute()).thenThrow(new ClientHttpException(400, "Bad Request"));

    assertThatThrownBy(() -> client.create(TestAgentExecutionContext.withLimits()))
        .isInstanceOf(ConnectorException.class)
        .satisfies(
            e -> {
              final var connectorException = (ConnectorException) e;
              assertThat(connectorException.getErrorCode())
                  .isEqualTo(ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED);
            });

    // Only 1 attempt, no sleeps
    assertThat(recordedSleeps).isEmpty();
    verify(camundaClient, times(1)).newCreateAgentInstanceCommand();
  }

  @Test
  void shouldReturnKeyAndRecordOneSleepWhenRetryableErrorPrecedesSuccess() {
    setupCommandChain();
    when(step5.execute()).thenThrow(new ClientHttpException(404, "Not Found")).thenReturn(response);
    when(response.getAgentInstanceKey()).thenReturn(999L);

    final AgentInstanceKey key = client.create(TestAgentExecutionContext.withLimits());

    assertThat(key).isEqualTo(AgentInstanceKey.of(999L));
    assertThat(recordedSleeps).hasSize(1);
    assertThat(recordedSleeps).containsExactly(Duration.ofSeconds(1));
    verify(camundaClient, times(2)).newCreateAgentInstanceCommand();
  }

  @Test
  void shouldThrowConnectorExceptionWithAttemptCountWhenAllRetriesAreExhausted() {
    setupCommandChain();
    when(step5.execute()).thenThrow(new ClientHttpException(500, "Internal Server Error"));

    assertThatThrownBy(() -> client.create(TestAgentExecutionContext.withLimits()))
        .isInstanceOf(ConnectorException.class)
        .satisfies(
            e -> {
              final var connectorException = (ConnectorException) e;
              assertThat(connectorException.getErrorCode())
                  .isEqualTo(ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED);
              assertThat(connectorException.getMessage()).contains("after 5 attempt(s)");
            });

    // 5 total attempts → 4 sleeps: before attempts 2, 3, 4, 5
    assertThat(recordedSleeps).hasSize(4);
    assertThat(recordedSleeps)
        .containsExactly(
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            Duration.ofSeconds(4),
            Duration.ofSeconds(8));
    verify(camundaClient, times(5)).newCreateAgentInstanceCommand();
  }

  private static class TestAgentExecutionContext implements AgentExecutionContext {

    public static TestAgentExecutionContext withoutLimits() {
      return new TestAgentExecutionContext(null);
    }

    public static TestAgentExecutionContext withLimits() {
      return new TestAgentExecutionContext(new LimitsConfiguration(10));
    }

    private final TestJobContext jobContext;

    private final LimitsConfiguration limitsConfiguration;

    private TestAgentExecutionContext(LimitsConfiguration limitsConfiguration) {
      this.jobContext = new TestJobContext(Map::of, () -> "");
      jobContext.setElementInstanceKey(ELEMENT_INSTANCE_KEY);

      this.limitsConfiguration = limitsConfiguration;
    }

    @Override
    public JobContext jobContext() {
      return jobContext;
    }

    @Override
    public AgentContext initialAgentContext() {
      return null;
    }

    @Override
    public List<ToolCallResult> initialToolCallResults() {
      return List.of();
    }

    @Override
    public List<AdHocToolElement> toolElements() {
      return List.of();
    }

    @Override
    public ProviderConfiguration provider() {
      return new OpenAiProviderConfiguration(
          new OpenAiProviderConfiguration.OpenAiConnection(
              null, null, new OpenAiProviderConfiguration.OpenAiModel("gpt-4o", null)));
    }

    @Override
    public PromptConfiguration.SystemPromptConfiguration systemPrompt() {
      return new PromptConfiguration.SystemPromptConfiguration("system prompt");
    }

    @Override
    public PromptConfiguration.UserPromptConfiguration userPrompt() {
      return null;
    }

    @Override
    public MemoryConfiguration memory() {
      return null;
    }

    @Override
    public LimitsConfiguration limits() {
      return limitsConfiguration;
    }

    @Override
    public EventHandlingConfiguration events() {
      return null;
    }

    @Override
    public ResponseConfiguration response() {
      return null;
    }
  }
}
