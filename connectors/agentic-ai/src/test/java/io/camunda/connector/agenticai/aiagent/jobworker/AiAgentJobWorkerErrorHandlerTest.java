/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.jobworker;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.connector.agenticai.util.WireMockUtils.assertJobFailRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorExceptionHandler;
import io.camunda.spring.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.spring.client.metrics.MetricsRecorder;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@WireMockTest
@ExtendWith(MockitoExtension.class)
class AiAgentJobWorkerErrorHandlerTest {

  private static final AgentResponse AGENT_RESPONSE =
      AgentResponse.builder().context(AgentContext.empty()).build();

  @Mock private SecretProvider secretProvider;
  @Mock private CommandExceptionHandlingStrategy exceptionHandlingStrategy;
  @Mock private MetricsRecorder metricsRecorder;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ActivatedJob job;

  private OutboundConnectorExceptionHandler outboundConnectorExceptionHandler;
  private AiAgentJobWorkerErrorHandler errorHandler;
  private CamundaClient camundaClient;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntimeInfo) throws URISyntaxException {
    camundaClient =
        spy(
            CamundaClient.newClientBuilder()
                .usePlaintext()
                .preferRestOverGrpc(true)
                .restAddress(new URI(wireMockRuntimeInfo.getHttpBaseUrl()))
                .build());

    lenient().when(job.getKey()).thenReturn(123456L);

    outboundConnectorExceptionHandler = new OutboundConnectorExceptionHandler(secretProvider);
    errorHandler =
        new AiAgentJobWorkerErrorHandler(
            outboundConnectorExceptionHandler, exceptionHandlingStrategy, metricsRecorder);

    stubFor(
        post(urlPathEqualTo("/v2/jobs/123456/failure")).willReturn(aResponse().withStatus(204)));
  }

  @Test
  void returnsEarlyWhenExecutionIsSuccessful() {
    errorHandler.executeWithErrorHandling(camundaClient, job, () -> AGENT_RESPONSE);
    verifyNoInteractions(camundaClient);
  }

  @Test
  void failsWithExceptionMessage() {
    final var exception = new RuntimeException("Execution failed");
    errorHandler.executeWithErrorHandling(
        camundaClient,
        job,
        () -> {
          throw exception;
        });

    assertJobFailRequest(
        request -> {
          assertThat(request.getErrorMessage()).isEqualTo("Execution failed");

          assertThat(request.getRetries()).isEqualTo(0);
          assertThat(request.getRetryBackOff()).isZero();

          assertThat(request.getVariables()).containsOnlyKeys("error");
          assertThat(request.getVariables().get("error"))
              .isEqualTo(
                  Map.of("type", "java.lang.RuntimeException", "message", "Execution failed"));
        });
  }

  @Test
  void failsWithRetriesAndRetryBackoff() {
    when(job.getRetries()).thenReturn(3);
    when(job.getCustomHeaders().get("retryBackoff")).thenReturn("PT10S");

    final var exception = new RuntimeException("Execution failed");
    errorHandler.executeWithErrorHandling(
        camundaClient,
        job,
        () -> {
          throw exception;
        });

    assertJobFailRequest(
        request -> {
          assertThat(request.getErrorMessage()).isEqualTo("Execution failed");

          assertThat(request.getRetries()).isEqualTo(2);
          assertThat(request.getRetryBackOff()).isEqualTo(Duration.ofSeconds(10).toMillis());

          assertThat(request.getVariables()).containsOnlyKeys("error");
          assertThat(request.getVariables().get("error"))
              .isEqualTo(
                  Map.of("type", "java.lang.RuntimeException", "message", "Execution failed"));
        });
  }

  @Test
  void failsWithInvalidRetryBackoffValue() {
    when(job.getCustomHeaders().get("retryBackoff")).thenReturn("invalid");

    final var exception = new RuntimeException("Execution failed");
    errorHandler.executeWithErrorHandling(
        camundaClient,
        job,
        () -> {
          throw exception;
        });

    assertJobFailRequest(
        request -> {
          assertThat(request.getErrorMessage())
              .isEqualTo(
                  "Failed to parse retry backoff header. Expected ISO-8601 duration, e.g. PT5M, got: invalid");
          assertThat(request.getRetryBackOff()).isZero();

          assertThat(request.getVariables()).containsOnlyKeys("error");
          assertThat(request.getVariables().get("error"))
              .isEqualTo(
                  Map.of(
                      "type",
                      "io.camunda.connector.runtime.core.error.InvalidBackOffDurationException",
                      "message",
                      "Failed to parse retry backoff header. Expected ISO-8601 duration, e.g. PT5M, got: invalid"));
        });
  }

  @Test
  void failsWithConnectorException() {
    final var exception = new ConnectorException("MY_ERROR_CODE", "Execution failed");

    errorHandler.executeWithErrorHandling(
        camundaClient,
        job,
        () -> {
          throw exception;
        });

    assertJobFailRequest(
        request -> {
          assertThat(request.getErrorMessage()).isEqualTo("Execution failed");

          assertThat(request.getVariables()).containsOnlyKeys("error");
          assertThat(request.getVariables().get("error"))
              .isEqualTo(
                  Map.of(
                      "code",
                      "MY_ERROR_CODE",
                      "type",
                      "io.camunda.connector.api.error.ConnectorException",
                      "message",
                      "Execution failed"));
        });
  }

  @Test
  void truncatesLongErrorMessage() {
    final var exceptionMessage = "abc".repeat(3000);
    final var expectedExceptionMessage = exceptionMessage.substring(0, 6000);

    final var exception = new RuntimeException(exceptionMessage);

    errorHandler.executeWithErrorHandling(
        camundaClient,
        job,
        () -> {
          throw exception;
        });

    assertJobFailRequest(
        request -> {
          assertThat(request.getErrorMessage())
              .isNotEqualTo(exceptionMessage)
              .isEqualTo(expectedExceptionMessage);

          assertThat(request.getVariables()).containsOnlyKeys("error");
          assertThat(request.getVariables().get("error"))
              .isEqualTo(
                  Map.of(
                      "type", "java.lang.RuntimeException", "message", expectedExceptionMessage));
        });
  }
}
