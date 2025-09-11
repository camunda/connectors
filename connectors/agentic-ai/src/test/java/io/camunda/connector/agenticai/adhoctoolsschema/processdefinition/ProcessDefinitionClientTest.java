/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.processdefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.fetch.ProcessDefinitionGetXmlRequest;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ProcessDefinitionConfiguration.RetriesConfiguration;
import io.camunda.connector.api.error.ConnectorException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessDefinitionClientTest {
  private static final Long PROCESS_DEFINITION_KEY = 123456L;
  private static final String PROCESS_DEFINITION_XML = "<bpmn>...</bpmn>";
  private static final RetriesConfiguration RETRIES_CONFIGURATION =
      new RetriesConfiguration(2, Duration.ofMillis(100));

  @Mock private CamundaClient camundaClient;
  @Mock private ProcessDefinitionGetXmlRequest xmlRequest;
  @Mock private CamundaFuture<String> camundaFuture;

  private ProcessDefinitionClient client;
  private ClientHttpException httpException;

  @BeforeEach
  void setUp() {
    client = new ProcessDefinitionClient(camundaClient, RETRIES_CONFIGURATION);
    httpException = new ClientHttpException(404, "Not Found");
  }

  @Test
  void shouldReturnXmlOnSuccessfulFirstAttempt() {
    when(camundaClient.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY))
        .thenReturn(xmlRequest);
    when(xmlRequest.send()).thenReturn(camundaFuture);
    when(camundaFuture.join()).thenReturn(PROCESS_DEFINITION_XML);

    final var result = client.getProcessDefinitionXml(PROCESS_DEFINITION_KEY);
    assertThat(result).isEqualTo(PROCESS_DEFINITION_XML);

    verify(camundaClient, times(1)).newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY);
  }

  @Test
  void shouldRetryAndSucceedOnSecondAttempt() {
    when(camundaClient.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY))
        .thenReturn(xmlRequest);
    when(xmlRequest.send()).thenReturn(camundaFuture);
    when(camundaFuture.join()).thenThrow(httpException).thenReturn(PROCESS_DEFINITION_XML);

    final var result = client.getProcessDefinitionXml(PROCESS_DEFINITION_KEY);
    assertThat(result).isEqualTo(PROCESS_DEFINITION_XML);

    verify(camundaClient, times(2)).newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY);
  }

  @Test
  void shouldThrowConnectorExceptionAfterMaxRetries() {
    when(camundaClient.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY))
        .thenReturn(xmlRequest);
    when(xmlRequest.send()).thenReturn(camundaFuture);
    when(camundaFuture.join()).thenThrow(httpException);

    assertThatThrownBy(() -> client.getProcessDefinitionXml(PROCESS_DEFINITION_KEY))
        .isInstanceOf(ConnectorException.class)
        .hasMessage(
            "Failed to retrieve process definition XML with key 123456 after 3 attempt(s): Failed with code 404: 'Not Found'")
        .hasCause(httpException);

    verify(camundaClient, times(3)).newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY);
  }

  @Test
  void shouldNotRetryWhenNotConfigured() {
    final var clientWithoutRetries =
        new ProcessDefinitionClient(
            camundaClient, new RetriesConfiguration(0, Duration.ofMillis(100)));

    when(camundaClient.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY))
        .thenReturn(xmlRequest);
    when(xmlRequest.send()).thenReturn(camundaFuture);
    when(camundaFuture.join()).thenThrow(httpException);

    assertThatThrownBy(() -> clientWithoutRetries.getProcessDefinitionXml(PROCESS_DEFINITION_KEY))
        .isInstanceOf(ConnectorException.class)
        .hasMessage(
            "Failed to retrieve process definition XML with key 123456 after 1 attempt(s): Failed with code 404: 'Not Found'")
        .hasCause(httpException);

    verify(camundaClient, times(1)).newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY);
  }

  @Test
  void shouldThrowConnectorExceptionOnInterruption() {
    when(camundaClient.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY))
        .thenReturn(xmlRequest);
    when(xmlRequest.send()).thenReturn(camundaFuture);
    when(camundaFuture.join()).thenThrow(httpException);

    Thread.currentThread().interrupt();

    assertThatThrownBy(() -> client.getProcessDefinitionXml(PROCESS_DEFINITION_KEY))
        .isInstanceOf(ConnectorException.class)
        .hasMessage(
            "Failed to retrieve process definition XML with key 123456 after 3 attempt(s): Interrupted while retrying to fetch process definition XML with key '123456'.");

    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }

  @Test
  @Timeout(value = 5, unit = TimeUnit.SECONDS)
  void shouldCalculateExponentialBackoffCorrectly() {
    when(camundaClient.newProcessDefinitionGetXmlRequest(1L)).thenReturn(xmlRequest);
    when(xmlRequest.send()).thenReturn(camundaFuture);
    when(camundaFuture.join()).thenThrow(httpException);

    long startTime = System.currentTimeMillis();

    assertThatThrownBy(() -> client.getProcessDefinitionXml(1L))
        .isInstanceOf(ConnectorException.class);

    long elapsedTime = System.currentTimeMillis() - startTime;

    // expected delays: 100ms (attempt 2) + 200ms (attempt 3) = 300ms
    assertThat(elapsedTime).isGreaterThan(300).isLessThan(500);
  }
}
