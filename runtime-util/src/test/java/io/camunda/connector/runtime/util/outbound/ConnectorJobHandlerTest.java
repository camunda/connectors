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

package io.camunda.connector.runtime.util.outbound;

import static io.camunda.connector.runtime.util.ConnectorHelper.ERROR_EXPRESSION_HEADER_NAME;
import static io.camunda.connector.runtime.util.ConnectorHelper.RESULT_EXPRESSION_HEADER_NAME;
import static io.camunda.connector.runtime.util.ConnectorHelper.RESULT_VARIABLE_HEADER_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.secret.SecretProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ConnectorJobHandlerTest {

  @Nested
  class SecretTests {

    @Test
    void shouldReplaceSecretsViaSpiLoadedProvider() {
      // given
      var jobHandler =
          new ConnectorJobHandler(
              (context) -> {
                var input = new TestInput("secrets." + TestSecretProvider.SECRET_NAME);
                context.replaceSecrets(input);
                return input;
              });

      // when
      var result = JobBuilder.create().withResultVariableHeader("result").execute(jobHandler);

      // then
      assertThat(result.getVariable("result"))
          .extracting("value")
          .isEqualTo(TestSecretProvider.SECRET_VALUE);
    }

    @Test
    void shouldOverrideSecretProvider() {
      // given
      var jobHandler =
          new TestConnectorJobHandler(
              (context) -> {
                var input = new TestInput("secrets." + TestSecretProvider.SECRET_NAME);
                context.replaceSecrets(input);
                return input;
              });

      // when
      var result = JobBuilder.create().withResultVariableHeader("result").execute(jobHandler);

      // then
      assertThat(result.getVariable("result")).extracting("value").isEqualTo("baz");
    }
  }

  @Nested
  class OutputTests {

    @Nested
    class ResultVariableTests {

      @ParameterizedTest
      @NullSource
      @EmptySource
      @ValueSource(strings = {" ", "\t", "\n"})
      void shouldNotSetWithBlankResultVariable(String variableName) {
        // given
        var jobHandler = new ConnectorJobHandler((context) -> Map.of("hello", "world"));

        // when
        var result = JobBuilder.create().withResultVariableHeader(variableName).execute(jobHandler);

        // then
        assertThat(result.getVariables()).isEmpty();
      }

      @Test
      void shouldHandleMap() {
        // given
        var jobHandler = new ConnectorJobHandler((context) -> Map.of("hello", "world"));

        // when
        var result = JobBuilder.create().withResultVariableHeader("result").execute(jobHandler);

        // then
        assertThat(result.getVariables()).isEqualTo(Map.of("result", Map.of("hello", "world")));
      }

      @Test
      void shouldHandleNull() {
        // given
        var jobHandler = new ConnectorJobHandler((ctx) -> null);
        var expected = new HashMap<>();
        expected.put("result", null);

        // when
        var result = JobBuilder.create().withResultVariableHeader("result").execute(jobHandler);

        // then
        assertThat(result.getVariables()).isEqualTo(expected);
      }

      @Test
      void shouldHandleEmptyMap() {
        // given
        var jobHandler = new ConnectorJobHandler((ctx) -> Map.of());

        // when
        var result = JobBuilder.create().withResultVariableHeader("result").execute(jobHandler);

        // then
        assertThat(result.getVariables()).isEqualTo(Map.of("result", Map.of()));
      }

      @Test
      void shouldHandleScalarValue() {
        // given
        var jobHandler = new ConnectorJobHandler((ctx) -> 1);

        // when
        var result = JobBuilder.create().withResultVariableHeader("result").execute(jobHandler);

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
        var jobHandler = new ConnectorJobHandler((context) -> Map.of("hello", "world"));

        // when
        var result = JobBuilder.create().withResultExpressionHeader(expression).execute(jobHandler);

        // then
        assertThat(result.getVariables()).isEmpty();
      }

      @Test
      void shouldHandleMap() {
        // given
        var jobHandler =
            new ConnectorJobHandler(
                (context) -> Map.of("callStatus", Map.of("statusCode", "200 OK")));
        var resultExpression = "{\"processedOutput\": response.callStatus }";

        // when
        var result =
            JobBuilder.create().withResultExpressionHeader(resultExpression).execute(jobHandler);

        // then
        assertThat(result.getVariables())
            .isEqualTo(Map.of("processedOutput", Map.of("statusCode", "200 OK")));
      }

      @Test
      void shouldHandleMap_WithNullValues() {
        // given
        var jobHandler =
            new ConnectorJobHandler(
                (context) -> {
                  var map = new HashMap<>();
                  map.put("statusCode", 200);
                  map.put("failure", null);

                  return Map.of("callStatus", map);
                });
        var resultExpression = "= {\"processedOutput\": { status: callStatus.statusCode } }";

        // when
        var result =
            JobBuilder.create().withResultExpressionHeader(resultExpression).execute(jobHandler);

        // then
        assertThat(result.getVariables())
            .isEqualTo(Map.of("processedOutput", Map.of("status", 200)));
      }

      @Test
      void shouldHandleMap_MapNullValues() {
        // given
        var jobHandler =
            new ConnectorJobHandler(
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
            JobBuilder.create().withResultExpressionHeader(resultExpression).execute(jobHandler);

        // then
        assertThat(result.getVariables()).isEqualTo(Map.of("processedOutput", expected));
      }

      @Test
      void shouldHandlePojo_Mapped() {
        // given
        var jobHandler =
            new ConnectorJobHandler((context) -> new TestConnectorResponsePojo("responseValue"));
        var resultExpression = "{\"processedOutput\": response.value }";

        // when
        var result =
            JobBuilder.create().withResultExpressionHeader(resultExpression).execute(jobHandler);

        // then
        assertThat(result.getVariables()).isEqualTo(Map.of("processedOutput", "responseValue"));
      }

      @Test
      void shouldHandlePojo_NullValues() {
        // given
        var jobHandler = new ConnectorJobHandler((context) -> new TestConnectorResponsePojo(null));
        var resultExpression = "{\"processedOutput\": response.value }";
        var expected = new HashMap<>();
        expected.put("processedOutput", null);

        // when
        var result =
            JobBuilder.create().withResultExpressionHeader(resultExpression).execute(jobHandler);

        // then
        assertThat(result.getVariables()).isEqualTo(expected);
      }

      @Test
      void shouldHandlePojo_DirectAssignment() {
        // given
        var jobHandler =
            new ConnectorJobHandler((context) -> new TestConnectorResponsePojo("responseValue"));
        var resultExpression = "= response";

        // when
        var result =
            JobBuilder.create().withResultExpressionHeader(resultExpression).execute(jobHandler);

        // then
        assertThat(result.getVariables()).isEqualTo(Map.of("value", "responseValue"));
      }

      @Test
      void shouldHandleUnknownObject() {
        // given
        var jobHandler =
            new ConnectorJobHandler(
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
            JobBuilder.create().withResultExpressionHeader(resultExpression).execute(jobHandler);

        // then
        assertThat(result.getVariables())
            .isEqualTo(Map.of("processedOutput", "COMPLETED", "ignoredOutput", Map.of()));
      }

      @Test
      void shouldFail_MappingFromNull() {
        // given
        var jobHandler = new ConnectorJobHandler((context) -> null);
        var resultExpression = "{\"processedOutput\": response.callStatus }";

        // when
        var result =
            JobBuilder.create()
                .withResultExpressionHeader(resultExpression)
                .execute(jobHandler, false);

        // then
        assertThat(result.getErrorMessage()).contains("Context is null");
      }

      @Test
      void shouldFail_MappingNonExistingKeys() {
        // given
        var jobHandler = new ConnectorJobHandler((context) -> Map.of());
        var resultExpression = "{\"processedOutput\": response.callStatus }";

        // when
        var result =
            JobBuilder.create()
                .withResultExpressionHeader(resultExpression)
                .execute(jobHandler, false);

        // then
        assertThat(result.getErrorMessage())
            .contains("context contains no entry with key 'callStatus'");
      }

      @Test
      void shouldFail_MappingFromScalar() {
        // given
        var jobHandler = new ConnectorJobHandler((context) -> "FOO");
        var resultExpression = "= response";

        // when
        var result =
            JobBuilder.create()
                .withResultExpressionHeader(resultExpression)
                .execute(jobHandler, false);

        // then
        assertThat(result.getErrorMessage()).contains("Unable to parse 'FOO' as context");
      }

      @Test
      void shouldFail_ProducingScalar() {
        // given
        var jobHandler = new ConnectorJobHandler((context) -> Map.of("FOO", "BAR"));
        var resultExpression = "= FOO";

        // when & then
        var result =
            JobBuilder.create()
                .withResultExpressionHeader(resultExpression)
                .execute(jobHandler, false);

        // then
        assertThat(result.getErrorMessage()).contains("Cannot parse '\"BAR\"' as 'java.util.Map'");
      }
    }

    @Nested
    class ResultVariableAndExpressionTests {
      @Test
      void shouldNotSetWithoutResultVariableAndExpression() {
        // given
        var jobHandler = new ConnectorJobHandler((context) -> Map.of("hello", "world"));

        // when
        var result = JobBuilder.create().execute(jobHandler);

        // then
        assertThat(result.getVariables()).isEmpty();
      }

      @Test
      void shouldSetBothResultVariableAndExpression() {
        // given
        var jobHandler =
            new ConnectorJobHandler(
                (context) -> Map.of("callStatus", Map.of("statusCode", "200 OK")));
        var resultExpression = "{\"processedOutput\": response.callStatus }";
        var resultVariable = "result";

        // when
        var result =
            JobBuilder.create()
                .withHeaders(
                    Map.of(
                        RESULT_VARIABLE_HEADER_NAME, resultVariable,
                        RESULT_EXPRESSION_HEADER_NAME, resultExpression))
                .execute(jobHandler);

        // then
        assertThat(result.getVariables().size()).isEqualTo(2);
        assertThat(result.getVariable("processedOutput")).isEqualTo(Map.of("statusCode", "200 OK"));
        assertThat(result.getVariable(resultVariable))
            .isEqualTo(Map.of("callStatus", Map.of("statusCode", "200 OK")));
      }
    }
  }

  @Nested
  class ExecutionTests {

    @Test
    void shouldProduceFailCommandWhenCallThrowsException() {
      // given
      var jobHandler =
          new ConnectorJobHandler(
              context -> {
                throw new NullPointerException("expected");
              });

      // when
      var result = JobBuilder.create().execute(jobHandler, false);

      // then
      assertThat(result.getErrorMessage()).isEqualTo("expected");
    }

    @Test
    void shouldTruncateFailJobErrorMessage() {
      // given
      var veryLongMessage = "This is quite a long message".repeat(300); // 8400 chars
      var jobHandler =
          new ConnectorJobHandler(
              context -> {
                throw new IllegalArgumentException(veryLongMessage);
              });

      // when
      var result = JobBuilder.create().execute(jobHandler, false);

      // then
      assertThat(result.getErrorMessage().length())
          .isLessThanOrEqualTo(ConnectorJobHandler.MAX_ERROR_MESSAGE_LENGTH);
    }

    @Test
    void shouldTruncateBpmnErrorMessage() {
      // given
      var veryLongMessage = "This is quite a long message".repeat(300); // 8400 chars
      var errorExpression = "bpmnError(\"500\", testProperty)";
      var jobHandler = new ConnectorJobHandler(context -> veryLongMessage);

      // when
      var result =
          JobBuilder.create()
              .withHeaders(
                  Map.of(
                      RESULT_VARIABLE_HEADER_NAME,
                      "testProperty",
                      ERROR_EXPRESSION_HEADER_NAME,
                      errorExpression))
              .execute(jobHandler, false, true);

      // then
      assertThat(result.getErrorMessage().length())
          .isLessThanOrEqualTo(ConnectorJobHandler.MAX_ERROR_MESSAGE_LENGTH);
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
          new ConnectorJobHandler(
              context -> {
                // no error code provided
                throw new ConnectorException(null, "exception message");
              });
      // when
      var result =
          JobBuilder.create().withErrorExpressionHeader(expression).execute(jobHandler, false);

      // then
      assertThat(result.getErrorMessage()).isEqualTo("exception message");
    }

    @Test
    void shouldFail_BpmnErrorFunctionWithWrongArgument() {
      var jobHandler =
          new ConnectorJobHandler(
              context -> {
                // no error code provided
                throw new ConnectorException(null, "exception message");
              });
      var errorExpression = "bpmnError(123, \"\")";
      // when
      var result =
          JobBuilder.create().withErrorExpressionHeader(errorExpression).execute(jobHandler, false);

      // then
      assertThat(result.getErrorMessage())
          .contains("Parameter 'code' of function 'bpmnError' must be a String");
    }

    @Test
    void shouldCreateBpmnError_UsingExceptionCodeAndRawContext() {
      // given
      var errorExpression =
          "if error.code != null then "
              + "{ \"code\": error.code, \"message\": \"Message: \" + error.message} "
              + "else {}";
      var jobHandler =
          new ConnectorJobHandler(
              context -> {
                throw new ConnectorException("1013", "exception message");
              });
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .execute(jobHandler, false, true);
      // then
      assertThat(result.getErrorCode()).isEqualTo("1013");
      assertThat(result.getErrorMessage()).isEqualTo("Message: exception message");
    }

    @Test
    void shouldCreateBpmnError_UsingExceptionWithBpmnErrorFunction() {
      // given
      var errorExpression =
          "if error.code != null then "
              + "bpmnError(error.code, \"Message: \" + error.message) "
              + "else null";
      var jobHandler =
          new ConnectorJobHandler(
              context -> {
                throw new ConnectorException("1013", "exception message");
              });
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .execute(jobHandler, false, true);
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
          new ConnectorJobHandler(
              context -> {
                throw new ConnectorException("1013", "exception message");
              });
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .execute(jobHandler, false, true);
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
          new ConnectorJobHandler(
              context -> {
                throw new ConnectorException("1013", "exception message");
              });
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .execute(jobHandler, false, true);
      // then
      assertThat(result.getErrorCode()).isEqualTo("1013");
      assertThat(result.getErrorMessage()).isEqualTo("Message: exception message");
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
          new ConnectorJobHandler(
              context -> {
                throw new ConnectorException("1013", "exception message");
              });
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .execute(jobHandler, false, true);
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
          new ConnectorJobHandler(
              context -> {
                throw new ConnectorException("1013", "exception message");
              });
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .execute(jobHandler, false, true);
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
          new ConnectorJobHandler(
              context -> {
                throw new ConnectorException("1013", "exception message");
              });
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .execute(jobHandler, false, true);
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
      var jobHandler = new ConnectorJobHandler(context -> Map.of("testProperty", "foo"));
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .execute(jobHandler, false, true);
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
      var jobHandler = new ConnectorJobHandler(context -> Map.of("testProperty", "foo"));
      // when
      var result =
          JobBuilder.create()
              .withErrorExpressionHeader(errorExpression)
              .execute(jobHandler, false, true);
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
      var jobHandler = new ConnectorJobHandler(context -> "foo");
      // when
      var result =
          JobBuilder.create()
              .withHeaders(
                  Map.of(
                      RESULT_VARIABLE_HEADER_NAME,
                      "testProperty",
                      ERROR_EXPRESSION_HEADER_NAME,
                      errorExpression))
              .execute(jobHandler, false, true);
      // then
      assertThat(result.getErrorCode()).isEqualTo("9999");
      assertThat(result.getErrorMessage()).isEqualTo("Message for foo value on test property");
    }
  }

  public static class TestInput {
    @Secret private String value;

    public TestInput(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }
  }

  private static class TestConnectorJobHandler extends ConnectorJobHandler {

    public TestConnectorJobHandler(OutboundConnectorFunction call) {
      super(call);
    }

    @Override
    public SecretProvider getSecretProvider() {
      return name -> TestSecretProvider.SECRET_NAME.equals(name) ? "baz" : null;
    }
  }

  private static class TestConnectorResponsePojo {

    private final String value;

    private TestConnectorResponsePojo(final String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  private static class NonSerializable {

    private final UUID field = UUID.randomUUID();
  }
}
