/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.outbound.job;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getAllServeEvents;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.common.ContentTypes.CONTENT_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.jobhandling.DefaultCommandExceptionHandlingStrategy;
import io.camunda.client.metrics.MicrometerMetricsRecorder;
import io.camunda.client.protocol.rest.JobCompletionRequest;
import io.camunda.client.protocol.rest.JobErrorRequest;
import io.camunda.client.protocol.rest.JobFailRequest;
import io.camunda.client.protocol.rest.JobResult;
import io.camunda.client.protocol.rest.JobResultActivateElement;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.outbound.ConnectorJobCompletion;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for {@link SpringConnectorJobHandler} behavior when the connector function returns a {@link
 * ConnectorJobCompletion}. These tests verify ad-hoc sub-process completion directives, element
 * activation, error callbacks, and IgnoreError rejection.
 */
@WireMockTest
@ExtendWith(MockitoExtension.class)
class ConnectorJobCompletionTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Mock private OutboundConnectorFunction connectorFunction;
  @Mock private SecretProvider secretProvider;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ActivatedJob job;

  private Map<String, String> jobHeaders;
  private SpringConnectorJobHandler handler;
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
    when(job.getType()).thenReturn("test:connector-job-completion:1");
    when(job.getCustomHeaders()).thenReturn(jobHeaders);

    handler =
        new SpringConnectorJobHandler(
            new MicrometerMetricsRecorder(new SimpleMeterRegistry()),
            new DefaultCommandExceptionHandlingStrategy(
                BackoffSupplier.newBackoffBuilder().build(),
                Executors.newSingleThreadScheduledExecutor()),
            new SecretProviderAggregator(List.of(secretProvider)),
            new DefaultValidationProvider(),
            org.mockito.Mockito.mock(DocumentFactory.class),
            ConnectorsObjectMapperSupplier.getCopy(),
            connectorFunction);

    stubFor(post(urlPathEqualTo("/v2/jobs/123456/completion")).willReturn(jsonResponse("{}", 200)));
    stubFor(
        post(urlPathEqualTo("/v2/jobs/123456/failure")).willReturn(aResponse().withStatus(204)));
    stubFor(post(urlPathEqualTo("/v2/jobs/123456/error")).willReturn(aResponse().withStatus(204)));
  }

  @Test
  void completesJobWithoutElementActivations() throws Exception {
    mockFunctionReturnsCompletion(
        TestConnectorJobCompletion.builder()
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
                  JobResult.class,
                  adHoc -> {
                    assertThat(adHoc.getIsCompletionConditionFulfilled()).isFalse();
                    assertThat(adHoc.getIsCancelRemainingInstances()).isFalse();
                    assertThat(adHoc.getActivateElements()).isEmpty();
                  });
        });
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void completesWithCompletionConditionFulfilled(boolean cancelRemainingInstances)
      throws Exception {
    mockFunctionReturnsCompletion(
        TestConnectorJobCompletion.builder()
            .completionConditionFulfilled(true)
            .cancelRemainingInstances(cancelRemainingInstances)
            .variables(Map.of("result", Map.of("status", "done")))
            .build());

    handler.handle(camundaClient, job);

    assertJobCompletionRequest(
        request -> {
          assertThat(request.getVariables())
              .containsExactly(entry("result", Map.of("status", "done")));
          assertThat(request.getResult())
              .isNotNull()
              .isInstanceOfSatisfying(
                  JobResult.class,
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
  void completesWithActivatingElements(boolean cancelRemainingInstances) throws Exception {
    mockFunctionReturnsCompletion(
        TestConnectorJobCompletion.builder()
            .completionConditionFulfilled(false)
            .cancelRemainingInstances(cancelRemainingInstances)
            .variables(Map.of("context", Map.of("state", "READY")))
            .elementsToActivate(
                List.of(
                    new ElementActivation("taskA", Map.of("input", "valueA", "extra", "")),
                    new ElementActivation("taskB", Map.of("input", "valueB", "extra", ""))))
            .build());

    handler.handle(camundaClient, job);

    assertJobCompletionRequest(
        request -> {
          assertThat(request.getVariables()).containsOnlyKeys("context");
          assertThat(request.getResult())
              .isNotNull()
              .isInstanceOfSatisfying(
                  JobResult.class,
                  adHoc -> {
                    assertThat(adHoc.getIsCompletionConditionFulfilled()).isFalse();
                    assertThat(adHoc.getIsCancelRemainingInstances())
                        .isEqualTo(cancelRemainingInstances);
                    assertThat(adHoc.getActivateElements())
                        .extracting(
                            JobResultActivateElement::getElementId,
                            JobResultActivateElement::getVariables)
                        .containsExactly(
                            tuple("taskA", Map.of("input", "valueA", "extra", "")),
                            tuple("taskB", Map.of("input", "valueB", "extra", "")));
                  });
        });
  }

  @Test
  void invokesOnErrorCallbackWhenCompletionFails() throws Exception {
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
    mockFunctionReturnsCompletion(
        TestConnectorJobCompletion.builder()
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
  void invokesOnSuccessCallbackWhenCompletionSucceeds() throws Exception {
    stubFor(
        post(urlPathEqualTo("/v2/jobs/123456/completion")).willReturn(aResponse().withStatus(204)));

    final List<String> successCallbacks = new ArrayList<>();
    mockFunctionReturnsCompletion(
        TestConnectorJobCompletion.builder()
            .completionConditionFulfilled(false)
            .cancelRemainingInstances(false)
            .onCompletionSuccess(response -> successCallbacks.add("called"))
            .build());

    handler.handle(camundaClient, job);

    await().untilAsserted(() -> assertThat(successCallbacks).hasSize(1));
  }

  @Test
  void rejectsIgnoreErrorWhenRejectIgnoreErrorIsTrue() throws Exception {
    jobHeaders.put(
        "errorExpression",
        """
        =if response.status = "trigger ignore" then
          ignoreError({})
        else
          null
        """);

    mockFunctionReturnsCompletion(
        TestConnectorJobCompletion.builder()
            .completionConditionFulfilled(true)
            .cancelRemainingInstances(false)
            .responseValue(Map.of("status", "trigger ignore"))
            .rejectIgnoreError(true)
            .build());

    handler.handle(camundaClient, job);

    assertJobFailRequest(
        request ->
            assertThat(request.getErrorMessage())
                .startsWith("IgnoreError is not supported for this connector"));
  }

  @Test
  void allowsIgnoreErrorWhenRejectIgnoreErrorIsFalse() throws Exception {
    jobHeaders.put(
        "errorExpression",
        """
        =if response.status = "trigger ignore" then
          ignoreError({"recovered": true})
        else
          null
        """);

    mockFunctionReturnsCompletion(
        TestConnectorJobCompletion.builder()
            .completionConditionFulfilled(true)
            .cancelRemainingInstances(false)
            .responseValue(Map.of("status", "trigger ignore"))
            .rejectIgnoreError(false)
            .build());

    handler.handle(camundaClient, job);

    assertJobCompletionRequest(
        request -> assertThat(request.getVariables()).containsEntry("recovered", true));
  }

  @Test
  void evaluatesErrorExpressionAgainstResponseValue() throws Exception {
    jobHeaders.put(
        "errorExpression",
        """
        =if response.status = "error" then
          jobError("Response indicated an error")
        else
          null
        """);

    mockFunctionReturnsCompletion(
        TestConnectorJobCompletion.builder()
            .completionConditionFulfilled(true)
            .cancelRemainingInstances(false)
            .responseValue(Map.of("status", "error"))
            .build());

    handler.handle(camundaClient, job);

    assertJobFailRequest(
        request -> assertThat(request.getErrorMessage()).startsWith("Response indicated an error"));
  }

  @Test
  void evaluatesErrorExpressionToBpmnError() throws Exception {
    jobHeaders.put(
        "errorExpression",
        """
        =if response.status = "fatal" then
          bpmnError("FATAL_ERROR", "A fatal error occurred")
        else
          null
        """);

    mockFunctionReturnsCompletion(
        TestConnectorJobCompletion.builder()
            .completionConditionFulfilled(true)
            .cancelRemainingInstances(false)
            .responseValue(Map.of("status", "fatal"))
            .build());

    handler.handle(camundaClient, job);

    assertJobErrorRequest(
        request -> {
          assertThat(request.getErrorCode()).isEqualTo("FATAL_ERROR");
          assertThat(request.getErrorMessage()).isEqualTo("A fatal error occurred");
        });
  }

  // --- Helpers ---

  private void mockFunctionReturnsCompletion(TestConnectorJobCompletion completion)
      throws Exception {
    when(connectorFunction.execute(any(OutboundConnectorContext.class))).thenReturn(completion);
  }

  private static void assertJobCompletionRequest(
      ThrowingConsumer<JobCompletionRequest> assertions) {
    await()
        .untilAsserted(
            () -> verify(1, postRequestedFor(urlPathMatching("^/v2/jobs/([0-9]+)/completion$"))));
    assertions.accept(getLastRequest(JobCompletionRequest.class));
  }

  private static void assertJobFailRequest(ThrowingConsumer<JobFailRequest> assertions) {
    await()
        .untilAsserted(
            () -> verify(1, postRequestedFor(urlPathMatching("^/v2/jobs/([0-9]+)/failure$"))));
    assertions.accept(getLastRequest(JobFailRequest.class));
  }

  private static void assertJobErrorRequest(ThrowingConsumer<JobErrorRequest> assertions) {
    await()
        .untilAsserted(
            () -> verify(1, postRequestedFor(urlPathMatching("^/v2/jobs/([0-9]+)/error$"))));
    assertions.accept(getLastRequest(JobErrorRequest.class));
  }

  private static <T> T getLastRequest(final Class<T> requestType) {
    assertThat(getAllServeEvents()).describedAs("WireMock has serve events").isNotEmpty();
    ServeEvent lastServeEvent = getAllServeEvents().getLast();
    try {
      return OBJECT_MAPPER.readValue(lastServeEvent.getRequest().getBodyAsString(), requestType);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  // --- Test ConnectorJobCompletion implementation ---

  record ElementActivation(String elementId, Map<String, Object> variables) {}

  static class TestConnectorJobCompletion implements ConnectorJobCompletion {

    private final boolean completionConditionFulfilled;
    private final boolean cancelRemainingInstances;
    private final Map<String, Object> variables;
    private final List<ElementActivation> elementsToActivate;
    private final Object responseValue;
    private final boolean rejectIgnoreError;
    private final Consumer<Throwable> onCompletionError;
    private final Consumer<Object> onCompletionSuccess;

    private TestConnectorJobCompletion(Builder builder) {
      this.completionConditionFulfilled = builder.completionConditionFulfilled;
      this.cancelRemainingInstances = builder.cancelRemainingInstances;
      this.variables = builder.variables;
      this.elementsToActivate = builder.elementsToActivate;
      this.responseValue = builder.responseValue;
      this.rejectIgnoreError = builder.rejectIgnoreError;
      this.onCompletionError = builder.onCompletionError;
      this.onCompletionSuccess = builder.onCompletionSuccess;
    }

    static Builder builder() {
      return new Builder();
    }

    @Override
    public Object responseValue() {
      return responseValue;
    }

    @Override
    public boolean rejectIgnoreError() {
      return rejectIgnoreError;
    }

    @Override
    public void onCompletionSuccess(Object response) {
      if (onCompletionSuccess != null) {
        onCompletionSuccess.accept(response);
      }
    }

    @Override
    public void onCompletionError(Throwable throwable) {
      if (onCompletionError != null) {
        onCompletionError.accept(throwable);
      }
    }

    @Override
    public FinalCommandStep<?> prepareCompleteCommand(
        JobClient client, ActivatedJob job, Map<String, Object> vars) {
      CompleteJobCommandStep1 command = client.newCompleteCommand(job).variables(variables);
      return command.withResult(
          result -> {
            var adHocSubProcess =
                result
                    .forAdHocSubProcess()
                    .completionConditionFulfilled(completionConditionFulfilled)
                    .cancelRemainingInstances(cancelRemainingInstances);
            for (ElementActivation element : elementsToActivate) {
              adHocSubProcess =
                  adHocSubProcess
                      .activateElement(element.elementId())
                      .variables(element.variables());
            }
            return adHocSubProcess;
          });
    }

    static class Builder {
      private boolean completionConditionFulfilled;
      private boolean cancelRemainingInstances;
      private Map<String, Object> variables = Map.of();
      private List<ElementActivation> elementsToActivate = List.of();
      private Object responseValue;
      private boolean rejectIgnoreError;
      private Consumer<Throwable> onCompletionError;
      private Consumer<Object> onCompletionSuccess;

      Builder completionConditionFulfilled(boolean value) {
        this.completionConditionFulfilled = value;
        return this;
      }

      Builder cancelRemainingInstances(boolean value) {
        this.cancelRemainingInstances = value;
        return this;
      }

      Builder variables(Map<String, Object> value) {
        this.variables = value;
        return this;
      }

      Builder elementsToActivate(List<ElementActivation> value) {
        this.elementsToActivate = value;
        return this;
      }

      Builder responseValue(Object value) {
        this.responseValue = value;
        return this;
      }

      Builder rejectIgnoreError(boolean value) {
        this.rejectIgnoreError = value;
        return this;
      }

      Builder onCompletionError(Consumer<Throwable> value) {
        this.onCompletionError = value;
        return this;
      }

      Builder onCompletionSuccess(Consumer<Object> value) {
        this.onCompletionSuccess = value;
        return this;
      }

      TestConnectorJobCompletion build() {
        return new TestConnectorJobCompletion(this);
      }
    }
  }
}
