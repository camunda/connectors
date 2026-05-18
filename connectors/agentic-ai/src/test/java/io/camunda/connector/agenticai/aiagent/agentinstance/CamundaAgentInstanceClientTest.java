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
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep2;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep3;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep4;
import io.camunda.client.api.command.CreateAgentInstanceCommandStep1.CreateAgentInstanceCommandStep5;
import io.camunda.client.api.response.CreateAgentInstanceResponse;
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

  private static final long ELEMENT_INSTANCE_KEY = 77L;
  private static final CreateAgentInstanceParams PARAMS =
      new CreateAgentInstanceParams(ELEMENT_INSTANCE_KEY, "gpt-4o", "openai", "system prompt", 10);
  private static final CreateAgentInstanceParams PARAMS_NULL_MAX_MODEL_CALLS =
      new CreateAgentInstanceParams(
          ELEMENT_INSTANCE_KEY, "gpt-4o", "openai", "system prompt", null);

  @Mock private CamundaClient camundaClient;
  @Mock private CreateAgentInstanceCommandStep1 step1;
  @Mock private CreateAgentInstanceCommandStep2 step2;
  @Mock private CreateAgentInstanceCommandStep3 step3;
  @Mock private CreateAgentInstanceCommandStep4 step4;
  @Mock private CreateAgentInstanceCommandStep5 step5;
  @Mock private CamundaFuture<CreateAgentInstanceResponse> future;
  @Mock private CreateAgentInstanceResponse response;

  private List<Duration> recordedSleeps;
  private CamundaAgentInstanceClient client;

  @BeforeEach
  void setUp() {
    recordedSleeps = new ArrayList<>();
    client =
        new CamundaAgentInstanceClient(camundaClient) {
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
    when(step5.send()).thenReturn(future);
  }

  @Test
  void successOnFirstAttempt_returnsAgentInstanceKey() {
    setupCommandChain();
    when(future.join()).thenReturn(response);
    when(response.getAgentInstanceKey()).thenReturn(12345L);

    final long key = client.create(PARAMS);

    assertThat(key).isEqualTo(12345L);
    assertThat(recordedSleeps).isEmpty();
    verify(camundaClient, times(1)).newCreateAgentInstanceCommand();
  }

  @Test
  void successOnFirstAttempt_withNullMaxModelCalls() {
    // When maxModelCalls is null the command skips the maxModelCalls() step and calls send()
    // directly on the step5 object returned by systemPrompt() — no maxModelCalls() stub needed.
    when(camundaClient.newCreateAgentInstanceCommand()).thenReturn(step1);
    when(step1.elementInstanceKey(anyLong())).thenReturn(step2);
    when(step2.model(anyString())).thenReturn(step3);
    when(step3.provider(anyString())).thenReturn(step4);
    when(step4.systemPrompt(anyString())).thenReturn(step5);
    when(step5.send()).thenReturn(future);
    when(future.join()).thenReturn(response);
    when(response.getAgentInstanceKey()).thenReturn(67890L);

    final long key = client.create(PARAMS_NULL_MAX_MODEL_CALLS);

    assertThat(key).isEqualTo(67890L);
    assertThat(recordedSleeps).isEmpty();
    verify(camundaClient, times(1)).newCreateAgentInstanceCommand();
  }

  @Test
  void permanentError400_throwsConnectorExceptionImmediately() {
    setupCommandChain();
    when(future.join()).thenThrow(new ClientHttpException(400, "Bad Request"));

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
  void retryableErrorThenSuccess_returnsKeyAndRecordsOneSleep() {
    setupCommandChain();
    when(future.join()).thenThrow(new ClientHttpException(404, "Not Found")).thenReturn(response);
    when(response.getAgentInstanceKey()).thenReturn(999L);

    final long key = client.create(PARAMS);

    assertThat(key).isEqualTo(999L);
    assertThat(recordedSleeps).hasSize(1);
    assertThat(recordedSleeps).containsExactly(Duration.ofSeconds(1));
    verify(camundaClient, times(2)).newCreateAgentInstanceCommand();
  }

  @Test
  void allRetriesExhausted_throwsConnectorExceptionWithAttemptCount() {
    setupCommandChain();
    when(future.join()).thenThrow(new ClientHttpException(500, "Internal Server Error"));

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

  @Test
  void interruptedDuringSleep_throwsConnectorExceptionAndSetsInterruptFlag() {
    // Client that throws InterruptedException on first sleep
    final CamundaAgentInstanceClient interruptableClient =
        new CamundaAgentInstanceClient(camundaClient) {
          private boolean firstSleep = true;

          @Override
          protected void sleep(Duration delay) throws InterruptedException {
            if (firstSleep) {
              firstSleep = false;
              throw new InterruptedException("simulated interrupt");
            }
          }
        };

    when(camundaClient.newCreateAgentInstanceCommand()).thenReturn(step1);
    when(step1.elementInstanceKey(anyLong())).thenReturn(step2);
    when(step2.model(anyString())).thenReturn(step3);
    when(step3.provider(anyString())).thenReturn(step4);
    when(step4.systemPrompt(anyString())).thenReturn(step5);
    when(step5.maxModelCalls(anyInt())).thenReturn(step5);
    when(step5.send()).thenReturn(future);
    when(future.join()).thenThrow(new ClientHttpException(503, "Service Unavailable"));

    // Ensure thread interrupt flag is clear before the test
    Thread.interrupted();

    assertThatThrownBy(() -> interruptableClient.create(PARAMS))
        .isInstanceOf(ConnectorException.class)
        .satisfies(
            e -> {
              final var connectorException = (ConnectorException) e;
              assertThat(connectorException.getErrorCode())
                  .isEqualTo(ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED);
            });

    // Verify the thread interrupt flag was set
    assertThat(Thread.interrupted()).isTrue();
  }
}
