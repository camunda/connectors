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

package io.camunda.connector.runtime.core.outbound;

import static io.camunda.connector.runtime.core.Keywords.ERROR_EXPRESSION_KEYWORD;
import static io.camunda.connector.runtime.core.Keywords.RESULT_EXPRESSION_KEYWORD;
import static io.camunda.connector.runtime.core.Keywords.RESULT_VARIABLE_KEYWORD;
import static io.camunda.connector.runtime.core.outbound.ConnectorJobHandlerTest.OutputTests.ResultVariableTests.newConnectorJobHandler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.client.api.command.FailJobCommandStep1;
import io.camunda.client.api.worker.JobClient;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorExceptionBuilder;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.error.ConnectorRetryExceptionBuilder;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.runtime.core.ConnectorHelper;
import io.camunda.connector.runtime.core.FooBarSecretProvider;
import io.camunda.connector.runtime.core.Keywords;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;

class ConnectorJobHandlerTest {

  private record TestConnectorResponsePojo(String value) {}

  private static class NonSerializable {
    private final UUID field = UUID.randomUUID();
  }

  @Nested
  class OutputTests {

    @Nested
    class ResultVariableTests {

      protected static ConnectorJobHandler newConnectorJobHandler(OutboundConnectorFunction call) {
        return new ConnectorJobHandler(call, new FooBarSecretProvider(), e -> {}, null, null);
      }

      @ParameterizedTest
      @NullSource
      @EmptySource
      @ValueSource(strings = {" ", "\t", "\n"})
      void shouldNotSetWithBlankResultVariable(String variableName) {
        // given
        var jobHandler = newConnectorJobHandler((context) -> Map.of("hello", "world"));

        // when
        var result =
            JobBuilder.create()
                .withResultVariableHeader(variableName)
                .executeAndCaptureResult(jobHandler);

        // then
        assertThat(result.getVariables()).isEmpty();
      }

      @Test
      void shouldHandleMap() {
        // given
        var jobHandler = newConnectorJobHandler((context) -> Map.of("hello", "world"));

        // when
        var result =
            JobBuilder.create()
                .withResultVariableHeader("result")
                .executeAndCaptureResult(jobHandler);

        // then
        assertThat(result.getVariables()).isEqualTo(Map.of("result", Map.of("hello", "world")));
      }

      @Test
      void shouldHandleNull() {
        // given
        var jobHandler = newConnectorJobHandler((ctx) -> null);
        var expected = new HashMap<>();
        expected.put("result", null);

        // when
        var result =
            JobBuilder.create()
                .withResultVariableHeader("result")
                .executeAndCaptureResult(jobHandler);

        // then
        assertThat(result.getVariables()).isEqualTo(expected);
      }

      @Test
      void shouldHandleEmptyMap() {
        // given
        var jobHandler = newConnectorJobHandler((ctx) -> Map.of());

        // when
        var result =
            JobBuilder.create()
                .withResultVariableHeader("result")
                .executeAndCaptureResult(jobHandler);

        // then
        assertThat(result.getVariables()).isEqualTo(Map.of("result", Map.of()));
      }

      @Test
      void shouldHandleScalarValue() {
        // given
        var jobHandler = newConnectorJobHandler((ctx) -> 1);

        // when
        var result =
            JobBuilder.create()
                .withResultVariableHeader("result")
                .executeAndCaptureResult(jobHandler);

        // then
        assertThat(result.getVariables()).isEqualTo(Map.of("result", 1));
      }
    }

    @Nested
    class ResultExpressionTests {

      @ParameterizedTest
      @NullSource
      @EmptySource
      @ValueSource(strings = {" ", "\t", "\n"})
      void shouldNotSetWithBlankResultExpression(String expression) {
        // given
        var jobHandler = newConnectorJobHandler((context) -> Map.of("hello", "world"));

        // when
        var result =
            JobBuilder.create()
                .withResultExpressionHeader(expression)
                .executeAndCaptureResult(jobHandler);

        // then
        assertThat(result.getVariables()).isEmpty();
      }

      @Test
      void shouldHandleMap() {
        // given
        var jobHandler =
            newConnectorJobHandler(
                (context) -> Map.of("callStatus", Map.of("statusCode", "200 OK")));
        var resultExpression = "{\"processedOutput\": response.callStatus }";

        // when
        var result =
            JobBuilder.create()
                .withResultExpressionHeader(resultExpression)
                .executeAndCaptureResult(jobHandler);

        // then
        assertThat(result.getVariables())
            .isEqualTo(Map.of("processedOutput", Map.of("statusCode", "200 OK")));
      }

      @Test
      void shouldHandleMap_WithNullValues() {
        // given
        var jobHandler =
            newConnectorJobHandler(
                (context) -> {
                  var map = new HashMap<>();
                  map.put("statusCode", 200);
                  map.put("failure", null);

                  return Map.of("callStatus", map);
                });
        var resultExpression = "= {\"processedOutput\": { status: callStatus.statusCode } }";

        // when
        var result =
            JobBuilder.create()
                .withResultExpressionHeader(resultExpression)
                .executeAndCaptureResult(jobHandler);

        // then
        assertThat(result.getVariables())
            .isEqualTo(Map.of("processedOutput", Map.of("status", 200)));
      }

      @Test
      void shouldHandleMap_MapNullValues() {
        // given
        var jobHandler =
            newConnectorJobHandler(
                (context) -> {
                  var map = new HashMap<>();
                  map.put("statusCode", 200);
                  map.put("failure", null);

                  return Map.of("callStatus", map);
                });
        var resultExpression = "= {\"processedOutput\": { failure: callStatus.failure } }";

        var expected = new HashMap<>();
        expected.put("failure", null);

        // when
        var result =
            JobBuilder.create()
                .withResultExpressionHeader(resultExpression)
                .executeAndCaptureResult(jobHandler);

        // then
        assertThat(result.getVariables()).isEqualTo(Map.of("processedOutput", expected));
      }

      @Test
      void shouldHandlePojo_Mapped() {
        // given
        var jobHandler =
            newConnectorJobHandler((context) -> new TestConnectorResponsePojo("responseValue"));
        var resultExpression = "{\"processedOutput\": response.value }";

        // when
        var result =
            JobBuilder.create()
                .withResultExpressionHeader(resultExpression)
                .executeAndCaptureResult(jobHandler);

        // then
        assertThat(result.getVariables()).isEqualTo(Map.of("processedOutput", "responseValue"));
      }

      @Test
      void shouldHandlePojo_NullValues() {
        // given
        var jobHandler = newConnectorJobHandler((context) -> new TestConnectorResponsePojo(null));
        var resultExpression = "{\"processedOutput\": response.value }";
        var expected = new HashMap<>();
        expected.put("processedOutput", null);

        // when
        var result =
            JobBuilder.create()
                .withResultExpressionHeader(resultExpression)
                .executeAndCaptureResult(jobHandler);

        // then
        assertThat(result.getVariables()).isEqualTo(expected);
      }

      @Test
      void shouldHandlePojo_DirectAssignment() {
        // given
        var jobHandler =
            newConnectorJobHandler((context) -> new TestConnectorResponsePojo("responseValue"));
        var resultExpression = "= response";

        // when
        var result =
            JobBuilder.create()
                .withResultExpressionHeader(resultExpression)
                .executeAndCaptureResult(jobHandler);

        // then
        assertThat(result.getVariables()).isEqualTo(Map.of("value", "responseValue"));
      }

      @Test
      void shouldHandleUnknownObject() {
        // given
        var jobHandler =
            newConnectorJobHandler(
                (context) -> {
                  var response = new HashMap<>();
                  response.put("status", "COMPLETED");
                  response.put("failure", new NonSerializable());
                  return response;
                });
        var resultExpression =
            "{\"processedOutput\": response.status, \"ignoredOutput\": response.failure}";

        // when
        var result =
            JobBuilder.create()
                .withResultExpressionHeader(resultExpression)
                .executeAndCaptureResult(jobHandler);

        // then
        assertThat(result.getVariables())
            .isEqualTo(Map.of("processedOutput", "COMPLETED", "ignoredOutput", Map.of()));
      }

      @Test
      void shouldFail_MappingFromNull() {
        // given
        var jobHandler = newConnectorJobHandler((context) -> null);
        var resultExpression = "{\"processedOutput\": response.callStatus }";

        // when
        var result =
            JobBuilder.create()
                .withResultExpressionHeader(resultExpression)
                .executeAndCaptureResult(jobHandler, false);

        // then
        assertThat(result.getErrorMessage()).contains("Context is null");
      }

      @Test
      void shouldNotFail_MappingNonExistingKeys() {
        // given
        var jobHandler = newConnectorJobHandler((context) -> Map.of());
        var resultExpression = "{\"processedOutput\": response.callStatus }";

        // when
        var result =
            JobBuilder.create()
                .withResultExpressionHeader(resultExpression)
                .executeAndCaptureResult(jobHandler, true);

        // then
        assertThat(result).isNotNull();
      }

      @Test
      void shouldSucceed_MappingFromScalarToContext() {
        // given
        var jobHandler = newConnectorJobHandler((context) -> "FOO");
        var resultExpression = "{processedOutput: response}";

        // when
        var result =
            JobBuilder.create()
                .withResultExpressionHeader(resultExpression)
                .executeAndCaptureResult(jobHandler);

        // then
        assertThat(result.getVariables()).isEqualTo(Map.of("processedOutput", "FOO"));
      }

      @Test
      void shouldFail_MappingFromScalar() {
        // given
        var jobHandler = newConnectorJobHandler((context) -> "FOO");
        var resultExpression = "= response";

        // when
        var result =
            JobBuilder.create()
                .withResultExpressionHeader(resultExpression)
                .executeAndCaptureResult(jobHandler, false);

        // then
        assertThat(result.getErrorMessage()).contains("Cannot parse '\"FOO\"' as 'java.util.Map'");
      }

      @Test
      void shouldFail_ProducingScalar() {
        // given
        var jobHandler = newConnectorJobHandler((context) -> Map.of("FOO", "BAR"));
        var resultExpression = "= FOO";

        // when & then
        var result =
            JobBuilder.create()
                .withResultExpressionHeader(resultExpression)
                .executeAndCaptureResult(jobHandler, false);

        // then
        assertThat(result.getErrorMessage()).contains("Cannot parse '\"BAR\"' as 'java.util.Map'");
      }
    }

    @Nested
    class ResultVariableAndExpressionTests {
      @Test
      void shouldNotSetWithoutResultVariableAndExpression() {
        // given
        var jobHandler = newConnectorJobHandler((context) -> Map.of("hello", "world"));

        // when
        var result = JobBuilder.create().executeAndCaptureResult(jobHandler);

        // then
        assertThat(result.getVariables()).isEmpty();
      }

      @Test
      void shouldSetBothResultVariableAndExpression() {
        // given
        var jobHandler =
            newConnectorJobHandler(
                (context) -> Map.of("callStatus", Map.of("statusCode", "200 OK")));

        var resultExpression = "{\"processedOutput\": response.callStatus, \"nullVar\": null}";
        var resultVariable = "result";

        // when
        var result =
            JobBuilder.create()
                .withHeaders(
                    Map.of(
                        RESULT_VARIABLE_KEYWORD, resultVariable,
                        RESULT_EXPRESSION_KEYWORD, resultExpression))
                .executeAndCaptureResult(jobHandler);

        // then
        assertThat(result.getVariables().size()).isEqualTo(3);
        assertThat(result.getVariable("processedOutput")).isEqualTo(Map.of("statusCode", "200 OK"));
        assertThat(result.getVariable("nullVar")).isNull();
        assertThat(result.getVariable(resultVariable))
            .isEqualTo(Map.of("callStatus", Map.of("statusCode", "200 OK")));
      }
    }
  }

  @Nested
  class ExecutionTests {

    private static Stream<RuntimeException> provideInputExceptions() {
      return Stream.of(
          new ConnectorExceptionBuilder()
              .message("expected Connector Input Exception")
              .cause(new ConnectorInputException(new Exception()))
              .build(),
          new ConnectorInputException(
              "expected Connector Input Exception", new RuntimeException("cause")));
    }

    @Test
    void shouldProduceFailCommandWhenCallThrowsException() {
      // given
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new NullPointerException("expected");
              });

      // when
      var result = JobBuilder.create().executeAndCaptureResult(jobHandler, false);

      // then
      assertThat(result.getErrorMessage()).isEqualTo("expected");
    }

    @Test
    void shouldTruncateFailJobErrorMessage() {
      // given
      var veryLongMessage = "This is quite a long message".repeat(300); // 8400 chars
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new IllegalArgumentException(veryLongMessage);
              });

      // when
      var result = JobBuilder.create().executeAndCaptureResult(jobHandler, false);

      // then
      assertThat(result.getErrorMessage().length())
          .isLessThanOrEqualTo(ConnectorJobHandler.MAX_ERROR_MESSAGE_LENGTH);
    }

    @Test
    void shouldTruncateBpmnErrorMessage() {
      // given
      var veryLongMessage = "This is quite a long message".repeat(300); // 8400 chars
      var errorExpression = "bpmnError(\"500\", testProperty)";
      var jobHandler = newConnectorJobHandler(context -> veryLongMessage);

      // when
      var result =
          JobBuilder.create()
              .withHeaders(
                  Map.of(
                      RESULT_VARIABLE_KEYWORD,
                      "testProperty",
                      ERROR_EXPRESSION_KEYWORD,
                      errorExpression))
              .executeAndCaptureResult(jobHandler, false, true);

      // then
      assertThat(result.getErrorMessage().length())
          .isLessThanOrEqualTo(ConnectorJobHandler.MAX_ERROR_MESSAGE_LENGTH);
    }

    @ParameterizedTest
    @MethodSource("provideInputExceptions")
    void shouldNotRetry_OnConnectorInputException(Exception exception) {
      // given
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw exception;
              });

      // when
      var result = JobBuilder.create().withRetries(3).executeAndCaptureResult(jobHandler, false);

      // then
      assertThat(result.getErrorMessage()).isEqualTo("expected Connector Input Exception");
      assertThat(result.getRetries()).isEqualTo(0);
    }
  }

  @Nested
  class RetryBackoffTests {

    private FailJobCommandStep1 firstStepMock;
    private FailJobCommandStep1.FailJobCommandStep2 secondStepMock;
    private JobClient jobClient;

    @BeforeEach
    void init() {
      firstStepMock = mock(FailJobCommandStep1.class);
      secondStepMock = mock(FailJobCommandStep1.FailJobCommandStep2.class, RETURNS_DEEP_STUBS);
      jobClient = mock(JobClient.class);
      when(firstStepMock.retries(anyInt())).thenReturn(secondStepMock);
      when(secondStepMock.retryBackoff(any())).thenReturn(secondStepMock);
      when(secondStepMock.errorMessage(any())).thenReturn(secondStepMock);
      when(secondStepMock.variables(anyMap())).thenReturn(secondStepMock);
      when(secondStepMock.variables(any(Object.class))).thenReturn(secondStepMock);
      jobClient = mock(JobClient.class);
      when(jobClient.newFailCommand(any())).thenReturn(firstStepMock);
    }

    @Test
    void shouldParseRetryBackoffHeader_Duration() {
      // given
      int initialRetries = 3;

      var jobBuilder =
          JobBuilder.create()
              .useJobClient(jobClient)
              .withRetries(initialRetries)
              .withHeaders(Map.of(Keywords.RETRY_BACKOFF_KEYWORD, "PT1M"));
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new RuntimeException("oops");
              });

      // when
      jobBuilder.execute(jobHandler);

      // then
      verify(firstStepMock).retries(initialRetries - 1);
      ArgumentCaptor<Duration> backoffCaptor = ArgumentCaptor.forClass(Duration.class);
      verify(secondStepMock).retryBackoff(backoffCaptor.capture());
      assertThat(backoffCaptor.getValue()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void shouldParseRetryBackoffHeader_Period() {
      // given
      int initialRetries = 3;
      var jobBuilder =
          JobBuilder.create()
              .useJobClient(jobClient)
              .withRetries(initialRetries)
              .withHeaders(Map.of(Keywords.RETRY_BACKOFF_KEYWORD, "P1D"));
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new RuntimeException("oops");
              });

      // when
      jobBuilder.execute(jobHandler);

      // then
      verify(firstStepMock).retries(initialRetries - 1);
      ArgumentCaptor<Duration> backoffCaptor = ArgumentCaptor.forClass(Duration.class);
      verify(secondStepMock).retryBackoff(backoffCaptor.capture());
      assertThat(backoffCaptor.getValue()).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void shouldParseRetryBackoffHeader_Invalid_ConnectorNotInvoked() throws Exception {
      // given
      int initialRetries = 3;
      var jobBuilder =
          JobBuilder.create()
              .useJobClient(jobClient)
              .withRetries(initialRetries)
              .withHeaders(Map.of(Keywords.RETRY_BACKOFF_KEYWORD, "P1D1S")); // invalid
      var connectorFunction = mock(OutboundConnectorFunction.class);
      var jobHandler = newConnectorJobHandler(connectorFunction);

      // when
      jobBuilder.execute(jobHandler);

      // then
      verify(firstStepMock).retries(0);
      verify(secondStepMock, times(0)).retryBackoff(any()); // not set
      verify(secondStepMock).errorMessage(contains("Failed to parse retry backoff header"));
      verify(secondStepMock).send();
      verify(connectorFunction, times(0)).execute(any()); // not invoked
    }

    @Test
    void shouldHandleMissingRetryBackoffHeader() {
      // given
      int initialRetries = 3;
      var jobBuilder = JobBuilder.create().useJobClient(jobClient).withRetries(initialRetries);

      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new RuntimeException("oops");
              });

      // when
      jobBuilder.execute(jobHandler);

      // then
      verify(firstStepMock).retries(initialRetries - 1);
      verify(secondStepMock).errorMessage(any());
      verify(secondStepMock, times(0)).retryBackoff(any()); // not set
      verify(secondStepMock).send();
    }
  }

  @Nested
  class ConnectorRetryExceptionTests {
    @Test
    void shouldHandleConnectorRetryException_Default_Error() throws JsonProcessingException {
      // given
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new ConnectorRetryExceptionBuilder().message("Test retry exception").build();
              });

      // when
      var result = JobBuilder.create().withRetries(3).executeAndCaptureResult(jobHandler, false);

      // then
      assertThat(result.getErrorMessage()).isEqualTo("Test retry exception");
      assertThat(result.getRetries()).isEqualTo(2);
    }

    @Test
    void shouldHandleConnectorRetryException_Custom_Error_Code() throws JsonProcessingException {
      // given
      var jobRetries = 3;
      var policyRetries = 4;
      var policyBackoff = Duration.ofSeconds(10);
      var customErrorCode = "customErrorCode";
      var errorMessage = "Test retry exception";
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new ConnectorRetryExceptionBuilder()
                    .message(errorMessage)
                    .errorCode(customErrorCode)
                    .retries(policyRetries)
                    .backoffDuration(policyBackoff)
                    .build();
              });

      // when
      var result =
          JobBuilder.create().withRetries(jobRetries).executeAndCaptureResult(jobHandler, false);

      // then
      assertThat(result.getErrorMessage()).isEqualTo(errorMessage);
      assertThat(result.getRetries()).isEqualTo(policyRetries);

      // Second occurrence of this Exception
      result =
          JobBuilder.create()
              .withRetries(policyRetries)
              .withVariables(
                  ConnectorHelper.OBJECT_MAPPER.writer().writeValueAsString(result.getVariables()))
              .executeAndCaptureResult(jobHandler, false);
      assertThat(result.getErrorMessage()).isEqualTo(errorMessage);
      // this is still the same value as this is the developer's responsibility to handle the
      // retries state
      // and decrement the retries value
      assertThat(result.getRetries()).isEqualTo(policyRetries);
    }

    @Test
    void shouldHandleConnectorRetryException_Basic_And_Retry_Exceptions()
        throws JsonProcessingException {
      AtomicInteger occurrence = new AtomicInteger();
      // given
      var jobRetries = 3;
      var policyRetries = 4;
      var policyBackoff = Duration.ofSeconds(10);
      var customRetryErrorCode = "customErrorCode";
      var retryErrorMessage = "Test retry exception";
      var basicErrorMessage = "Basic exception";
      var basicErrorCode = "basicErrorCode";
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                if (occurrence.getAndIncrement() == 0) {
                  throw new ConnectorRetryExceptionBuilder()
                      .message(retryErrorMessage)
                      .errorCode(customRetryErrorCode)
                      .retries(policyRetries)
                      .backoffDuration(policyBackoff)
                      .build();
                } else {
                  throw new ConnectorException(basicErrorCode, basicErrorMessage);
                }
              });

      // when
      var result =
          JobBuilder.create().withRetries(jobRetries).executeAndCaptureResult(jobHandler, false);

      // then
      assertThat(result.getErrorMessage()).isEqualTo(retryErrorMessage);
      assertThat(result.getRetries()).isEqualTo(policyRetries);

      // Second occurrence, will throw the ConnectorException
      result =
          JobBuilder.create()
              .withRetries(policyRetries)
              .withVariables(
                  ConnectorHelper.OBJECT_MAPPER.writer().writeValueAsString(result.getVariables()))
              .executeAndCaptureResult(jobHandler, false);
      assertThat(result.getErrorMessage()).isEqualTo(basicErrorMessage);
      assertThat(result.getRetries()).isEqualTo(policyRetries - 1);
    }
  }

  @Nested
  class ErrorExpressionTests {

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(
        strings = {
          " ",
          "\t",
          "\n",
          "if error.code != null then bpmnError(\"123\", \"\") else {}",
          "if error.code != null then bpmnError(\"123\", \"\") else null",
          "if unknownFunction(error.code) then bpmnError(\"123\", \"\") else null"
        })
    void shouldNotCreateBpmnErrorWithExpression(String expression) {
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                // no error code provided
                throw new ConnectorException(null, "exception message");
              });
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(expression)
              .executeAndCaptureResult(jobHandler, false);

      // then
      assertThat(result.getErrorMessage()).isEqualTo("exception message");
    }

    @Test
    void shouldFail_BpmnErrorFunctionWithWrongArgument() {
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                // no error code provided
                throw new ConnectorException(null, "exception message");
              });
      var errorExpression = "bpmnError(123, \"\")";
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .executeAndCaptureResult(jobHandler, false);

      // then
      assertThat(result.getErrorMessage())
          .contains("Parameter 'code' of function 'bpmnError' must be a String");
    }

    @Test
    void shouldCreateBpmnError_UsingExceptionCodeAndRawContext() {
      // given
      var errorExpression =
          "if error.code != null then "
              + "{ \"errorType\": \"bpmnError\", \"code\": error.code, \"message\": \"Message: \" + error.message} "
              + "else {}";
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new ConnectorException("1013", "exception message");
              });
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .executeAndCaptureResult(jobHandler, false, true);
      // then
      assertThat(result.getErrorCode()).isEqualTo("1013");
      assertThat(result.getErrorMessage()).isEqualTo("Message: exception message");
    }

    @Test
    void shouldCreateBpmnError_UsingExceptionCodeAndErrorVariables() {
      // given
      var errorExpression =
          "if error.code != null then "
              + "{ \"errorType\": \"bpmnError\", \"code\": error.code, \"message\": \"Message: \" + error.message, \"variables\": error.variables} "
              + "else {}";
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new ConnectorExceptionBuilder()
                    .errorCode("1013")
                    .message("exception message")
                    .errorVariables(Map.of("foo", "bar"))
                    .build();
              });
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .executeAndCaptureResult(jobHandler, false, true);
      // then
      assertThat(result.getErrorCode()).isEqualTo("1013");
      assertThat(result.getVariables()).isEqualTo(Map.of("foo", "bar"));
      assertThat(result.getErrorMessage()).isEqualTo("Message: exception message");
    }

    @Test
    void shouldHideSecretsInJobErrorMessage() {
      // given
      var errorMessage = "Something went wrong: bar is not the correct password";
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new IllegalArgumentException(errorMessage);
              });

      // when
      var result =
          JobBuilder.create()
              .withVariables("{{secrets.FOO}}")
              .executeAndCaptureResult(jobHandler, false);

      // then
      assertThat(result.getErrorMessage())
          .isEqualTo("Something went wrong: *** is not the correct password");
    }

    @Test
    void shouldHideSecretsInJsonProcessingError() {
      // given
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new ConnectorException(
                    "JSON_PROCESSING_ERROR, bar could not be parsed as JSON String");
              });

      // when
      var result =
          JobBuilder.create()
              .withVariables("{ \"integer\" : {{secrets.FOO}} }")
              .executeAndCaptureResult(jobHandler, false);

      // then
      assertThat(result.getErrorMessage())
          .isEqualTo("JSON_PROCESSING_ERROR, *** could not be parsed as JSON String");
    }

    @Test
    void shouldHideSecretsInJobErrorJsonMessage() {
      // given
      var errorMessage = "Something went wrong: bar is not the correct password";
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new IllegalArgumentException(errorMessage);
              });

      // when
      var result =
          JobBuilder.create()
              .withVariables("{ \"integer\" : {{secrets.FOO}} }")
              .executeAndCaptureResult(jobHandler, false);

      // then
      assertThat(result.getErrorMessage())
          .isEqualTo("Something went wrong: *** is not the correct password");
    }

    @Test
    void shouldCreateBpmnErro2r_UsingExceptionCodeAndErrorVariables() {
      // given
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new ConnectorExceptionBuilder()
                    .errorCode("1013")
                    .message("exception message")
                    .errorVariables(Map.of("foo", "bar"))
                    .build();
              });
      // when
      var result = JobBuilder.create().executeAndCaptureResult(jobHandler, false, false);
      // then
      assertThat(result.getVariables())
          .isEqualTo(
              Map.of(
                  "error",
                  Map.of(
                      "code",
                      "1013",
                      "variables",
                      Map.of("foo", "bar"),
                      "message",
                      "exception message",
                      "type",
                      "io.camunda.connector.api.error.ConnectorException")));
      assertThat(result.getErrorMessage()).isEqualTo("exception message");
    }

    @Test
    void shouldCreateBpmnError_UsingExceptionWithBpmnErrorFunction() {
      // given
      var errorExpression =
          "if error.code != null then "
              + "bpmnError(error.code, \"Message: \" + error.message) "
              + "else null";
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new ConnectorException("1013", "exception message");
              });
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .executeAndCaptureResult(jobHandler, false, true);
      // then
      assertThat(result.getErrorCode()).isEqualTo("1013");
      assertThat(result.getErrorMessage()).isEqualTo("Message: exception message");
    }

    @Test
    void shouldCreateBpmnError_UsingExceptionWithDefaultFunction() {
      // given
      var errorExpression =
          "if contains(error.code,\"10\") then "
              + "bpmnError(error.code, \"Message: \" + error.message) "
              + "else null";
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new ConnectorException("1013", "exception message");
              });
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .executeAndCaptureResult(jobHandler, false, true);
      // then
      assertThat(result.getErrorCode()).isEqualTo("1013");
      assertThat(result.getErrorMessage()).isEqualTo("Message: exception message");
    }

    @Test
    void shouldCreateBpmnError_UsingExceptionCodeAsFirstCondition() {
      // given
      var errorExpression =
          "if error.code != null then "
              + "bpmnError(error.code, \"Message: \" + error.message) "
              + "else if testProperty = \"foo\" then "
              + "bpmnError(\"9999\", \"Message for foo value on test property\") "
              + "else null";
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new ConnectorException("1013", "exception message");
              });
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .executeAndCaptureResult(jobHandler, false, true);
      // then
      assertThat(result.getErrorCode()).isEqualTo("1013");
      assertThat(result.getErrorMessage()).isEqualTo("Message: exception message");
    }

    @Test
    void shouldCreateJobError_UsingExceptionCodeAsSecondConditionAfterResponseProperty()
        throws JsonProcessingException {
      // given
      var errorExpression =
          """
          if response.testProperty = "foo" then
            jobError("Message for foo value on test property")
          else if error.code != null then
            jobError("Message: " + error.message)
          else {}
          """;
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new ConnectorException("1013", "exception message");
              });
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .executeAndCaptureResult(jobHandler, false, false);
      // then
      assertThat(result.getErrorMessage()).isEqualTo("Message: exception message");
    }

    @Test
    void shouldCreateJobError_UsingResponseProperty() throws JsonProcessingException {
      // given
      var errorExpression =
          """
          if response.testProperty = "foo" then
            jobError("Message for foo value on test property")
          else if error.code != null then
            jobError("Message: " + error.message)
          else {}
          """;
      var jobHandler = newConnectorJobHandler(context -> Map.of("testProperty", "foo"));
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .executeAndCaptureResult(jobHandler, false, false);
      // then
      assertThat(result.getErrorMessage()).isEqualTo("Message for foo value on test property");
    }

    @Test
    void shouldCreateJobError_UsingResponsePropertySettingRetriesRelativeToCurrentRetries() {
      // given
      var errorExpression =
          """
          if response.testProperty = "foo" then
            jobError("Message for foo value on test property", {}, job.retries - 1)
          else if error.code != null then
            jobError("Message: " + error.message)
          else {}
          """;
      var jobHandler = newConnectorJobHandler(context -> Map.of("testProperty", "foo"));
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .withRetries(5)
              .executeAndCaptureResult(jobHandler, false, false);
      // then
      assertThat(result.getErrorMessage()).isEqualTo("Message for foo value on test property");
      assertThat(result.getRetries()).isEqualTo(4);
    }

    @Test
    void shouldCreateBpmnError_UsingExceptionCodeAsSecondConditionAfterResponseProperty() {
      // given
      var errorExpression =
          "if response.testProperty = \"foo\" then "
              + "bpmnError(\"9999\", \"Message for foo value on test property\") "
              + "else if error.code != null then "
              + "bpmnError(error.code, \"Message: \" + error.message) "
              + "else {}";
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new ConnectorException("1013", "exception message");
              });
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .executeAndCaptureResult(jobHandler, false, true);
      // then
      assertThat(result.getErrorCode()).isEqualTo("1013");
      assertThat(result.getErrorMessage()).isEqualTo("Message: exception message");
    }

    @Test
    void shouldCreateBpmnError_UsingExceptionCodeAsSecondConditionAfterPlainProperty() {
      // given
      var errorExpression =
          "if testProperty = \"foo\" then "
              + "bpmnError(\"9999\", \"Message for foo value on test property\") "
              + "else if error.code != null then "
              + "bpmnError(error.code, \"Message: \" + error.message) "
              + "else {}";
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new ConnectorException("1013", "exception message");
              });
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .executeAndCaptureResult(jobHandler, false, true);
      // then
      assertThat(result.getErrorCode()).isEqualTo("1013");
      assertThat(result.getErrorMessage()).isEqualTo("Message: exception message");
    }

    @Test
    void shouldCreateBpmnError_UsingExceptionCodeAsSecondConditionAfterContextProperty() {
      // given
      var errorExpression =
          "if testObject.testProperty = \"foo\" then "
              + "bpmnError(\"9999\", \"Message for foo value on test property\") "
              + "else if error.code != null then "
              + "bpmnError(error.code, \"Message: \" + error.message) "
              + "else {}";
      var jobHandler =
          newConnectorJobHandler(
              context -> {
                throw new ConnectorException("1013", "exception message");
              });
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .executeAndCaptureResult(jobHandler, false, true);
      // then
      assertThat(result.getErrorCode()).isEqualTo("1013");
      assertThat(result.getErrorMessage()).isEqualTo("Message: exception message");
    }

    @Test
    void shouldCreateBpmnError_UsingResponseValueAsFirstCondition() {
      // given
      var errorExpression =
          "if response.testProperty = \"foo\" then "
              + "bpmnError(\"9999\", \"Message for foo value on test property\") "
              + "else if error.code != null then "
              + "bpmnError(error.code, \"Message: \" + error.message) "
              + "else {}";
      var jobHandler = newConnectorJobHandler(context -> Map.of("testProperty", "foo"));
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .executeAndCaptureResult(jobHandler, false, true);
      // then
      assertThat(result.getErrorCode()).isEqualTo("9999");
      assertThat(result.getErrorMessage()).isEqualTo("Message for foo value on test property");
    }

    @Test
    void shouldCreateBpmnError_UsingResponseValueAsSecondCondition() {
      // given
      var errorExpression =
          "if error.code != null then "
              + "bpmnError(error.code, \"Message: \" + error.message) "
              + "else if response.testProperty = \"foo\" then "
              + "bpmnError(\"9999\", \"Message for foo value on test property\") "
              + "else {}";
      var jobHandler = newConnectorJobHandler(context -> Map.of("testProperty", "foo"));
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .executeAndCaptureResult(jobHandler, false, true);
      // then
      assertThat(result.getErrorCode()).isEqualTo("9999");
      assertThat(result.getErrorMessage()).isEqualTo("Message for foo value on test property");
    }

    @Test
    void shouldCreateBpmnError_UsingResultVariable() {
      // given
      var errorExpression =
          "if testProperty = \"foo\" then "
              + "bpmnError(\"9999\", \"Message for foo value on test property\") "
              + "else if error.code != null then "
              + "bpmnError(error.code, \"Message: \" + error.message) "
              + "else {}";
      var jobHandler = newConnectorJobHandler(context -> "foo");
      // when
      var result =
          JobBuilder.create()
              .withHeaders(
                  Map.of(
                      RESULT_VARIABLE_KEYWORD,
                      "testProperty",
                      ERROR_EXPRESSION_KEYWORD,
                      errorExpression))
              .executeAndCaptureResult(jobHandler, false, true);
      // then
      assertThat(result.getErrorCode()).isEqualTo("9999");
      assertThat(result.getErrorMessage()).isEqualTo("Message for foo value on test property");
    }
  }
}
