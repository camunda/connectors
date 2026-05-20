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
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.AiAgentProperties.AgentInstanceProperties.RetriesProperties;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
  private static final InitialAgentInstanceData PARAMS =
      new InitialAgentInstanceData(
          ELEMENT_INSTANCE_KEY,
          "gpt-4o",
          "openai",
          "system prompt",
          new InitialAgentInstanceData.Limits(10));
  private static final InitialAgentInstanceData PARAMS_NULL_MAX_MODEL_CALLS =
      new InitialAgentInstanceData(ELEMENT_INSTANCE_KEY, "gpt-4o", "openai", "system prompt", null);

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

    final AgentInstanceKey key = client.create(PARAMS);

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

    final AgentInstanceKey key = client.create(PARAMS_NULL_MAX_MODEL_CALLS);

    assertThat(key).isEqualTo(AgentInstanceKey.of(67890L));
    assertThat(recordedSleeps).isEmpty();
    verify(camundaClient, times(1)).newCreateAgentInstanceCommand();
  }

  @Test
  void shouldThrowConnectorExceptionImmediatelyForHttp400PermanentError() {
    setupCommandChain();
    when(step5.execute()).thenThrow(new ClientHttpException(400, "Bad Request"));

    assertThatThrownBy(() -> client.create(PARAMS))
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

    final AgentInstanceKey key = client.create(PARAMS);

    assertThat(key).isEqualTo(AgentInstanceKey.of(999L));
    assertThat(recordedSleeps).hasSize(1);
    assertThat(recordedSleeps).containsExactly(Duration.ofSeconds(1));
    verify(camundaClient, times(2)).newCreateAgentInstanceCommand();
  }

  @Test
  void shouldThrowConnectorExceptionWithAttemptCountWhenAllRetriesAreExhausted() {
    setupCommandChain();
    when(step5.execute()).thenThrow(new ClientHttpException(500, "Internal Server Error"));

    assertThatThrownBy(() -> client.create(PARAMS))
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
}
