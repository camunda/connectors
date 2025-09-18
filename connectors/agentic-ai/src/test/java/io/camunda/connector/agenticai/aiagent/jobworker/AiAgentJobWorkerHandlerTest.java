/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.jobworker;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.common.ContentTypes.CONTENT_TYPE;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALLS_PROCESS_VARIABLES;
import static io.camunda.connector.agenticai.util.WireMockUtils.assertJobCompletionRequest;
import static io.camunda.connector.agenticai.util.WireMockUtils.assertJobErrorRequest;
import static io.camunda.connector.agenticai.util.WireMockUtils.assertJobFailRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.client.protocol.rest.JobResultActivateElement;
import io.camunda.client.protocol.rest.JobResultAdHocSubProcess;
import io.camunda.connector.agenticai.aiagent.AiAgentJobWorker;
import io.camunda.connector.agenticai.aiagent.agent.JobWorkerAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentCompletion;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.JobWorkerAgentResponse;
import io.camunda.connector.agenticai.model.tool.ToolCallProcessVariable;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.ConnectorResultHandler;
import io.camunda.connector.runtime.metrics.ConnectorsOutboundMetrics;
import io.camunda.connector.runtime.outbound.job.OutboundConnectorExceptionHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@WireMockTest
@ExtendWith(MockitoExtension.class)
class AiAgentJobWorkerHandlerTest {

  private static final String ERROR_EXPRESSION =
      """
      =if error.code = "JOB_ERROR_CODE" then
        jobError(error.message + " (job error)")
      else if error.code = "BPMN_ERROR_CODE" then
        bpmnError("MY_BPMN_ERROR_CODE", error.message + " (BPMN error)")
      else if response.responseText = "Job error response" then
        jobError("The text '" + response.responseText + "' led to a job error")
      else if response.responseText = "BPMN error response" then
        bpmnError("RESPONSE_TEXT_BPMN_ERROR_CODE", "The text '" + response.responseText + "' led to an BPMN error")
      else
        null
      """;

  private static final JobWorkerAgentResponse AGENT_RESPONSE_VARIABLE =
      JobWorkerAgentResponse.builder().responseText("Dummy response").build();

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Mock private JobWorkerAgentExecutionContextFactoryImpl executionContextFactory;
  @Mock private JobWorkerAgentRequestHandler agentRequestHandler;
  @Mock private SecretProvider secretProvider;
  @Mock private CommandExceptionHandlingStrategy exceptionHandlingStrategy;
  @Mock private JobWorkerAgentExecutionContext executionContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ActivatedJob job;

  private Map<String, String> jobHeaders;

  private AiAgentJobWorkerHandler handler;
  private CamundaClient camundaClient;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntimeInfo) throws URISyntaxException {
    camundaClient =
        spy(
            CamundaClient.newClientBuilder()
                .preferRestOverGrpc(true)
                .restAddress(new URI(wireMockRuntimeInfo.getHttpBaseUrl()))
                .build());

    jobHeaders = new HashMap<>();
    when(job.getKey()).thenReturn(123456L);
    when(job.getType()).thenReturn(AiAgentJobWorker.JOB_WORKER_TYPE);
    when(job.getCustomHeaders()).thenReturn(jobHeaders);

    when(executionContextFactory.createExecutionContext(camundaClient, job))
        .thenReturn(executionContext);

    final var outboundConnectorExceptionHandler =
        new OutboundConnectorExceptionHandler(secretProvider);
    final var connectorResultHandler =
        new ConnectorResultHandler(ConnectorsObjectMapperSupplier.getCopy());
    final var connectorsOutboundMetrics = new ConnectorsOutboundMetrics(new SimpleMeterRegistry());

    handler =
        new AiAgentJobWorkerHandlerImpl(
            executionContextFactory,
            agentRequestHandler,
            exceptionHandlingStrategy,
            outboundConnectorExceptionHandler,
            connectorResultHandler,
            connectorsOutboundMetrics);

    stubFor(post(urlPathEqualTo("/v2/jobs/123456/completion")).willReturn(jsonResponse("{}", 200)));
    stubFor(
        post(urlPathEqualTo("/v2/jobs/123456/failure")).willReturn(aResponse().withStatus(204)));
    stubFor(post(urlPathEqualTo("/v2/jobs/123456/error")).willReturn(aResponse().withStatus(204)));
  }

  /*
  @Test
  void returnsEarlyWhenExecutionIsSuccessful() {
    errorHandler.executeWithErrorHandling(camundaClient, job, () -> AGENT_RESPONSE);
    verifyNoInteractions(camundaClient);
  }
   */

  @Test
  void completesJobWithoutProcessChanges() {
    when(agentRequestHandler.handleRequest(executionContext))
        .thenReturn(
            JobWorkerAgentCompletion.builder()
                .completionConditionFulfilled(false)
                .cancelRemainingInstances(false)
                .build());

    handler.handle(camundaClient, job);

    assertJobCompletionRequest(
        request -> {
          assertThat(request.getVariables()).isEmpty();
          assertThat(request.getResult())
              .isNotNull()
              .isInstanceOfSatisfying(
                  JobResultAdHocSubProcess.class,
                  adHoc -> {
                    assertThat(adHoc.getIsCompletionConditionFulfilled()).isFalse();
                    assertThat(adHoc.getIsCancelRemainingInstances()).isFalse();
                    assertThat(adHoc.getActivateElements()).isEmpty();
                  });
        });
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void completesWithCompletingAdHocSubProcess(boolean cancelRemainingInstances) {
    when(agentRequestHandler.handleRequest(executionContext))
        .thenReturn(
            JobWorkerAgentCompletion.builder()
                .completionConditionFulfilled(true)
                .cancelRemainingInstances(cancelRemainingInstances)
                .variables(Map.of("agent", AGENT_RESPONSE_VARIABLE))
                .build());

    handler.handle(camundaClient, job);

    assertJobCompletionRequest(
        request -> {
          assertThat(request.getVariables())
              .containsExactly(
                  entry("agent", Map.of("responseText", AGENT_RESPONSE_VARIABLE.responseText())));
          assertThat(request.getResult())
              .isNotNull()
              .isInstanceOfSatisfying(
                  JobResultAdHocSubProcess.class,
                  adHoc -> {
                    assertThat(adHoc.getIsCompletionConditionFulfilled()).isTrue();
                    assertThat(adHoc.getIsCancelRemainingInstances())
                        .isEqualTo(cancelRemainingInstances);
                    assertThat(adHoc.getActivateElements()).isEmpty();
                  });
        });
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void completesWithActivatingElements(boolean cancelRemainingInstances) {
    final var agentResponse =
        AgentResponse.builder()
            .context(AgentContext.empty())
            .toolCalls(TOOL_CALLS_PROCESS_VARIABLES)
            .build();

    when(agentRequestHandler.handleRequest(executionContext))
        .thenReturn(
            JobWorkerAgentCompletion.builder()
                .completionConditionFulfilled(false)
                .cancelRemainingInstances(cancelRemainingInstances)
                .agentResponse(agentResponse)
                .variables(
                    Map.of("agentContext", agentResponse.context(), "toolCallResults", List.of()))
                .build());

    handler.handle(camundaClient, job);

    assertJobCompletionRequest(
        request -> {
          assertThat(request.getVariables()).containsOnlyKeys("agentContext", "toolCallResults");
          assertThat(request.getResult())
              .isNotNull()
              .isInstanceOfSatisfying(
                  JobResultAdHocSubProcess.class,
                  adHoc -> {
                    assertThat(adHoc.getIsCompletionConditionFulfilled()).isFalse();
                    assertThat(adHoc.getIsCancelRemainingInstances())
                        .isEqualTo(cancelRemainingInstances);
                    assertThat(adHoc.getActivateElements())
                        .extracting(
                            JobResultActivateElement::getElementId,
                            JobResultActivateElement::getVariables)
                        .containsExactly(
                            tuple(
                                "getWeather",
                                Map.ofEntries(
                                    Map.entry(
                                        "toolCall", asMap(agentResponse.toolCalls().getFirst())),
                                    Map.entry("toolCallResult", ""))),
                            tuple(
                                "getDateTime",
                                Map.ofEntries(
                                    Map.entry("toolCall", asMap(agentResponse.toolCalls().get(1))),
                                    Map.entry("toolCallResult", ""))));
                  });
        });
  }

  @Test
  void failsSuccessfulCompletionBasedOnErrorExpression() {
    jobHeaders.put("errorExpression", ERROR_EXPRESSION);

    final var agentResponse =
        AgentResponse.builder()
            .context(AgentContext.empty())
            .responseText("Job error response")
            .build();

    when(agentRequestHandler.handleRequest(executionContext))
        .thenReturn(
            JobWorkerAgentCompletion.builder()
                .completionConditionFulfilled(true)
                .cancelRemainingInstances(false)
                .agentResponse(agentResponse)
                .build());

    handler.handle(camundaClient, job);

    assertJobFailRequest(
        request -> {
          assertThat(request.getErrorMessage())
              .isEqualTo("The text 'Job error response' led to a job error");
          assertThat(request.getVariables())
              .containsExactly(entry("error", "The text 'Job error response' led to a job error"));
        });
  }

  @Test
  void throwsBpmnErrorForSuccessfulCompletionBasedOnErrorExpression() {
    jobHeaders.put("errorExpression", ERROR_EXPRESSION);

    final var agentResponse =
        AgentResponse.builder()
            .context(AgentContext.empty())
            .responseText("BPMN error response")
            .build();

    when(agentRequestHandler.handleRequest(executionContext))
        .thenReturn(
            JobWorkerAgentCompletion.builder()
                .completionConditionFulfilled(true)
                .cancelRemainingInstances(false)
                .agentResponse(agentResponse)
                .build());

    handler.handle(camundaClient, job);

    assertJobErrorRequest(
        request -> {
          assertThat(request.getErrorCode()).isEqualTo("RESPONSE_TEXT_BPMN_ERROR_CODE");
          assertThat(request.getErrorMessage())
              .isEqualTo("The text 'BPMN error response' led to an BPMN error");
          assertThat(request.getVariables()).isEmpty();
        });
  }

  @Test
  void triggersCompletionExceptionHandler() {
    stubFor(
        post(urlPathEqualTo("/v2/jobs/123456/completion"))
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withHeader(CONTENT_TYPE, "application/problem+json")
                    .withBody(
                        """
                        {
                          "type": "about:blank",
                          "title": "NOT_FOUND",
                          "status": 404,
                          "detail": "Job was not found",
                          "instance": "/v2/jobs/123456/completion"
                        }
                        """)));

    final List<Throwable> handledExceptions = new ArrayList<>();
    when(agentRequestHandler.handleRequest(executionContext))
        .thenReturn(
            JobWorkerAgentCompletion.builder()
                .completionConditionFulfilled(true)
                .cancelRemainingInstances(false)
                .onCompletionError(handledExceptions::add)
                .build());

    handler.handle(camundaClient, job);

    await().untilAsserted(() -> assertThat(handledExceptions).isNotEmpty());
    assertThat(handledExceptions)
        .hasSize(1)
        .first()
        .isInstanceOfSatisfying(
            ProblemException.class, e -> assertThat(e.details().getStatus()).isEqualTo(404));
  }

  @Test
  void failsWithExceptionMessage() {
    final var exception = new RuntimeException("Execution failed");
    when(agentRequestHandler.handleRequest(executionContext)).thenThrow(exception);

    handler.handle(camundaClient, job);

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

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = "  ")
  void failsWithEmptyExceptionMessage(String exceptionMessage) {
    final var exception = new RuntimeException(exceptionMessage);
    when(agentRequestHandler.handleRequest(executionContext)).thenThrow(exception);

    handler.handle(camundaClient, job);

    final var expectedExceptionMessage = exceptionMessage == null ? "" : exceptionMessage;

    assertJobFailRequest(
        request -> {
          assertThat(request.getErrorMessage()).isEqualTo(expectedExceptionMessage);

          assertThat(request.getRetries()).isEqualTo(0);
          assertThat(request.getRetryBackOff()).isZero();

          assertThat(request.getVariables()).containsOnlyKeys("error");
          assertThat(request.getVariables().get("error"))
              .isEqualTo(
                  Map.of(
                      "type", "java.lang.RuntimeException", "message", expectedExceptionMessage));
        });
  }

  @Test
  void failsWithRetriesAndRetryBackoff() {
    when(job.getRetries()).thenReturn(3);
    jobHeaders.put("retryBackoff", "PT10S");

    final var exception = new RuntimeException("Execution failed");
    when(agentRequestHandler.handleRequest(executionContext)).thenThrow(exception);

    handler.handle(camundaClient, job);

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
    Mockito.reset(executionContextFactory);
    jobHeaders.put("retryBackoff", "invalid");

    handler.handle(camundaClient, job);

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
    when(agentRequestHandler.handleRequest(executionContext)).thenThrow(exception);

    handler.handle(camundaClient, job);

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
  void failsWithJobErrorFromErrorExpression() {
    jobHeaders.put("errorExpression", ERROR_EXPRESSION);

    final var exception = new ConnectorException("JOB_ERROR_CODE", "Execution failed");
    when(agentRequestHandler.handleRequest(executionContext)).thenThrow(exception);

    handler.handle(camundaClient, job);

    assertJobFailRequest(
        request -> {
          assertThat(request.getErrorMessage()).isEqualTo("Execution failed (job error)");
          assertThat(request.getVariables())
              .containsExactly(entry("error", "Execution failed (job error)"));
        });
  }

  @Test
  void throwsBpmnErrorFromErrorExpression() {
    jobHeaders.put("errorExpression", ERROR_EXPRESSION);

    final var exception = new ConnectorException("BPMN_ERROR_CODE", "Execution failed");
    when(agentRequestHandler.handleRequest(executionContext)).thenThrow(exception);

    handler.handle(camundaClient, job);

    assertJobErrorRequest(
        request -> {
          assertThat(request.getErrorCode()).isEqualTo("MY_BPMN_ERROR_CODE");
          assertThat(request.getErrorMessage()).isEqualTo("Execution failed (BPMN error)");
          assertThat(request.getVariables()).isEmpty();
        });
  }

  @Test
  void failsJobWhenErrorExpressionCouldNotBeParsed() {
    jobHeaders.put("errorExpression", "=invalid expression");

    final var exception = new ConnectorException("MY_ERROR_CODE", "Execution failed");
    when(agentRequestHandler.handleRequest(executionContext)).thenThrow(exception);

    handler.handle(camundaClient, job);

    assertJobFailRequest(
        request -> {
          assertThat(request.getErrorMessage()).startsWith("Failed to evaluate expression");
          assertThat(request.getVariables().get("error"))
              .asInstanceOf(InstanceOfAssertFactories.map(String.class, Object.class))
              .containsEntry("type", "io.camunda.connector.feel.FeelEngineWrapperException");
        });
  }

  @Test
  void truncatesLongErrorMessage() {
    final var exceptionMessage = "abc".repeat(3000);
    final var expectedExceptionMessage = exceptionMessage.substring(0, 6000);

    final var exception = new RuntimeException(exceptionMessage);
    when(agentRequestHandler.handleRequest(executionContext)).thenThrow(exception);

    handler.handle(camundaClient, job);

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

  private Map<String, Object> asMap(final ToolCallProcessVariable toolCallProcessVariable) {
    return OBJECT_MAPPER.convertValue(toolCallProcessVariable, new TypeReference<>() {});
  }
}
