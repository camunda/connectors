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

import static io.camunda.connector.runtime.util.ConnectorHelper.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.secret.SecretProvider;
import java.util.Collections;
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
              (context) ->
                  context
                      .getSecretStore()
                      .replaceSecret("secrets." + TestSecretProvider.SECRET_NAME));

      // when
      var result = JobBuilder.create().withResultVariableHeader("result").execute(jobHandler);

      // then
      assertThat(result.getVariable("result")).isEqualTo(TestSecretProvider.SECRET_VALUE);
    }

    @Test
    void shouldOverrideSecretProvider() {
      // given
      var jobHandler =
          new TestConnectorJobHandler(
              (context) ->
                  context
                      .getSecretStore()
                      .replaceSecret("secrets." + TestSecretProvider.SECRET_NAME));

      // when
      var result = JobBuilder.create().withResultVariableHeader("result").execute(jobHandler);

      // then
      assertThat(result.getVariable("result")).isEqualTo("baz");
    }
  }

  @Nested
  class OutputTests {

    @Test
    void shouldNotSetWithoutResultVariableAndExpression() {
      // given
      var jobHandler = new ConnectorJobHandler((context) -> Map.of("hello", "world"));

      // when
      var result = JobBuilder.create().execute(jobHandler);

      // then
      assertThat(result.getVariables()).isEmpty();
    }

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
    void shouldSetToResultVariable() {
      // given
      var jobHandler = new ConnectorJobHandler((context) -> Map.of("hello", "world"));

      // when
      var result = JobBuilder.create().withResultVariableHeader("result").execute(jobHandler);

      // then
      assertThat(result.getVariables()).isEqualTo(Map.of("result", Map.of("hello", "world")));
    }

    @Test
    void shouldSetToResultExpression() {
      // given
      // Response from service -> {"callStatus":{"statusCode":"200 OK"}}
      var jobHandler =
          new ConnectorJobHandler(
              (context) -> Map.of("callStatus", Map.of("statusCode", "200 OK")));

      // FEEL expression -> {"processedOutput":response.callStatus}
      final String resultExpression = "{\"processedOutput\": response.callStatus }";

      // when
      var result =
          JobBuilder.create().withResultExpressionHeader(resultExpression).execute(jobHandler);

      // then
      assertThat(result.getVariables())
          .isEqualTo(Map.of("processedOutput", Map.of("statusCode", "200 OK")));
    }

    @Test
    void shouldSetToResultExpressionWhenPojoIsReturned() {
      // given
      // Response from service -> {"value": "response"}
      final String responseValue = "response";
      var jobHandler =
          new ConnectorJobHandler((context) -> new TestConnectorResponsePojo(responseValue));

      // FEEL expression -> {"processedOutput":response.callStatus}
      final String resultExpression = "{\"processedOutput\": response.value }";

      // when
      var result =
          JobBuilder.create().withResultExpressionHeader(resultExpression).execute(jobHandler);

      // then
      assertThat(result.getVariables()).isEqualTo(Map.of("processedOutput", responseValue));
    }

    @Test
    void shouldSetToResultExpressionWhenContainsNull() {
      // given
      // Response from service -> {"value": null}
      var jobHandler = new ConnectorJobHandler((context) -> new TestConnectorResponsePojo(null));

      // FEEL expression -> {"processedOutput":response.callStatus}
      final String resultExpression = "{\"processedOutput\": response.value }";

      // when
      var result =
          JobBuilder.create().withResultExpressionHeader(resultExpression).execute(jobHandler);

      // then
      assertThat(result.getVariables()).containsEntry("processedOutput", null);
    }

    @Test
    void shouldSetToResultExpressionWhenContainsUnknownObject() {
      // given
      var response = new HashMap<>();
      response.put("status", "COMPLETED");
      response.put("failure", new NonSerializable());
      var jobHandler = new ConnectorJobHandler((context) -> response);

      final String resultExpression =
          "{\"processedOutput\": response.status, \"ignoredOutput\": response.failure}";

      // when
      var result =
          JobBuilder.create().withResultExpressionHeader(resultExpression).execute(jobHandler);

      // then
      assertThat(result.getVariables())
          .isEqualTo(Map.of("processedOutput", "COMPLETED", "ignoredOutput", Map.of()));
    }

    @Test
    void shouldSetBothResultVariableAndExpression() {
      // given
      // Response from service -> {"callStatus":{"statusCode":"200 OK"}}
      var jobHandler =
          new ConnectorJobHandler(
              (context) -> Map.of("callStatus", Map.of("statusCode", "200 OK")));

      final String resultVariable = "result";

      // FEEL expression -> {"processedOutput":response.callStatus}
      final String resultExpression = "{\"processedOutput\": response.callStatus }";

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

    @Test
    void shouldSetResultVariableNullWhenCallReturnedNull() {
      // given
      final ConnectorJobHandler jobHandler = new ConnectorJobHandler((ctx) -> null);
      final String resultVariableName = "result";

      // when
      final JobBuilder.JobResult result =
          JobBuilder.create().withResultVariableHeader(resultVariableName).execute(jobHandler);

      // then
      assertThat(result.getVariables()).containsKey(resultVariableName);
      assertThat(result.getVariable(resultVariableName)).isNull();
      assertThat(result.getVariables().size()).isEqualTo(1);
    }

    @Test
    void shouldSetResultVariableEmptyWhenCallReturnedEmpty() {
      // given
      final ConnectorJobHandler jobHandler = new ConnectorJobHandler((ctx) -> new HashMap<>());
      final String resultVariableName = "result";

      // when
      final JobBuilder.JobResult result =
          JobBuilder.create().withResultVariableHeader(resultVariableName).execute(jobHandler);

      // then
      assertThat(result.getVariables()).containsKey(resultVariableName);
      assertThat(result.getVariable(resultVariableName)).isEqualTo(Collections.EMPTY_MAP);
      assertThat(result.getVariables().size()).isEqualTo(1);
    }

    @Test
    void shouldProduceFailCommandWhenResultExpressionIsDefinedAndCallReturnedNull() {
      // given
      // Response from service -> null
      var jobHandler = new ConnectorJobHandler((context) -> null);

      // FEEL expression -> {"processedOutput":response.callStatus}
      final String resultExpression = "{\"processedOutput\": response.callStatus }";

      // when & then
      JobBuilder.create()
          .withResultExpressionHeader(resultExpression)
          .execute(jobHandler, false, false);
    }

    @Test
    void shouldProduceFailCommandWhenResultExpressionIsDefinedAndCallReturnedEmpty() {
      // given
      // Response from service -> empty
      var jobHandler = new ConnectorJobHandler((context) -> new HashMap<>());

      // FEEL expression -> {"processedOutput":response.callStatus}
      final String resultExpression = "{\"processedOutput\": response.callStatus }";

      // when & then
      JobBuilder.create()
          .withResultExpressionHeader(resultExpression)
          .execute(jobHandler, false, false);
    }

    @Test
    void shouldProduceFailCommandWhenCallThrowsException() {
      // given
      var jobHandler =
          new ConnectorJobHandler(
              context -> {
                throw new NullPointerException("expected");
              });

      // when & then
      JobBuilder.create().execute(jobHandler, false, false);
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
          "if unknownFunction(error.code) then bpmnError(\"123\", \"\") else null",
          "bpmnError(123, \"\")"
        })
    void shouldNotCreateBpmnErrorWithExpression(String expression) {
      var jobHandler =
          new ConnectorJobHandler(
              context -> {
                // no error code provided
                throw new ConnectorException(null, "exception message");
              });
      // when & then
      JobBuilder.create().withErrorExpressionHeader(expression).execute(jobHandler, false, false);
    }

    @Test
    void shouldCreateBpmnError_UsingExceptionCodeAndRawContext() {
      // given
      final String errorExpression =
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
      final String errorExpression =
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
      final String errorExpression =
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
      final String errorExpression =
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
      final String errorExpression =
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
      final String errorExpression =
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
      final String errorExpression =
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
      final String errorExpression =
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
      final String errorExpression =
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
      final String errorExpression =
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
