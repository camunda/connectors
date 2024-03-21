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
package io.camunda.connector.feel;

import static io.camunda.connector.feel.FeelEngineWrapperUtil.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

class FeelEngineWrapperExpressionEvaluationTest {

  private FeelEngineWrapper objectUnderTest;

  @BeforeEach
  void beforeEach() {
    objectUnderTest = new FeelEngineWrapper();
  }

  @Test
  void evaluateToJson_ShouldSucceed_WhenHappyCase() throws JSONException {
    // given
    // FEEL expression -> {"processedOutput":response.callStatus}
    final var resultExpression = "{\"processedOutput\": response.callStatus }";
    // Response from service -> {"callStatus":{"statusCode":"200 OK"}}
    final var variables = Map.of("callStatus", Map.of("statusCode", "200 OK"));

    // when
    final var evaluatedResultAsJson =
        objectUnderTest.evaluateToJson(resultExpression, variables, wrapResponse(variables));

    // then
    JSONAssert.assertEquals(
        "{\"processedOutput\":{\"statusCode\":\"200 OK\"}}",
        evaluatedResultAsJson,
        JSONCompareMode.STRICT);
  }

  @Test
  void evaluate_ShouldSucceed_WhenHappyCaseJavaType() {
    // given
    // FEEL expression -> {"processedOutput":response.callStatus}
    final var resultExpression = "{\"processedOutput\": response.callStatus }";
    // Response from service -> {"callStatus":{"statusCode":"200 OK"}}
    final var variables = Map.of("callStatus", Map.of("statusCode", "200 OK"));

    // when
    final var evaluatedResultAsMap =
        objectUnderTest.evaluate(resultExpression, variables, wrapResponse(variables));

    // then
    final var expectedResult = Map.of("processedOutput", Map.of("statusCode", "200 OK"));
    assertThat(evaluatedResultAsMap).isEqualTo(expectedResult);
  }

  @Test
  void evaluateToJson_ShouldSucceed_WhenHandlingPojo() throws JSONException {
    // given
    final var resultExpression = "= { value: response.value, response: response }";
    final var variables = new TestPojo("FOO");

    // when
    final var evaluatedResultAsJson =
        objectUnderTest.evaluateToJson(resultExpression, variables, wrapResponse(variables));

    // then
    JSONAssert.assertEquals(
        "{\"response\":{\"value\":\"FOO\"},\"value\":\"FOO\"}",
        evaluatedResultAsJson,
        JSONCompareMode.STRICT);
  }

  @Test
  void evaluateToJson_ShouldSucceed_WhenExpressionStartsWithEqualsSign() throws JSONException {
    // given
    // FEEL expression -> ={"processedOutput":response.callStatus}
    final var resultExpression = "={\"processedOutput\": response.callStatus }";
    // Response from service -> {"callStatus":{"statusCode":"200 OK"}}
    final var variables = Map.of("callStatus", Map.of("statusCode", "200 OK"));

    // when
    final var evaluatedResultAsJson =
        objectUnderTest.evaluateToJson(resultExpression, variables, wrapResponse(variables));

    // then
    JSONAssert.assertEquals(
        "{\"processedOutput\":{\"statusCode\":\"200 OK\"}}",
        evaluatedResultAsJson,
        JSONCompareMode.STRICT);
  }

  @Test
  void evaluateToJson_ShouldSucceed_WhenContextWithContextFromHttpResponse() throws JSONException {
    // given
    // FEEL expression which is a bit useless, but it proves 2 things
    // 1. status is wrapped to response
    // 2. job is not
    final var errorExpression =
        "if response.status = 204 then {retries: job.retries, responseRetries: response.job.retries} else null";
    final var variables = Map.of("status", 204, "body", "", "headers", Map.of());
    final var jobContent = Map.of("job", Map.of("retries", 3));
    final var evaluatedResultAsJson =
        objectUnderTest.evaluateToJson(
            errorExpression, variables, Map.of("response", variables), jobContent);
    // then
    JSONAssert.assertEquals(
        "{\"retries\":3,\"responseRetries\":null}", evaluatedResultAsJson, JSONCompareMode.STRICT);
  }

  @Test
  void evaluateToJson_ShouldSucceed_WhenVariableNotFound() throws JSONException {
    // given
    // FEEL expression -> ={"processedOutput":response.doesnt-exist}
    final var resultExpression = "={\"processedOutput\": response.doesnt-exist }";
    // Response from service -> {"callStatus":{"statusCode":"200 OK"}}
    final var variables = Map.of("callStatus", Map.of("statusCode", "200 OK"));

    // when
    final var evaluatedResultAsJson = objectUnderTest.evaluateToJson(resultExpression, variables);

    // then
    JSONAssert.assertEquals(
        "{\"processedOutput\":null}", evaluatedResultAsJson, JSONCompareMode.STRICT);
  }

  @Test
  void evaluateToJson_ShouldSucceed_WhenUsedBuiltInFunction() throws JSONException {
    // given
    // FEEL expression -> {"processedOutput": upper case(response.callStatus)}
    final var resultExpression = "{\"processedOutput\": upper case(response.callStatus) }";
    // Response from service -> {"callStatus":"done"}
    final var variables = Map.of("callStatus", "done");

    // when
    final var evaluatedResultAsJson =
        objectUnderTest.evaluateToJson(resultExpression, variables, wrapResponse(variables));

    // then
    JSONAssert.assertEquals(
        "{\"processedOutput\":\"DONE\"}", evaluatedResultAsJson, JSONCompareMode.STRICT);
    // processedOutput in upper-case!
  }

  @Test
  void evaluateToJson_ShouldFail_WhenVariablesAreNull() {
    // given
    // FEEL expression -> {"processedOutput":response.callStatus}
    final var resultExpression = "{\"processedOutput\": response.callStatus }";

    // when & then
    final var exception =
        Assertions.catchThrowable(() -> objectUnderTest.evaluateToJson(resultExpression, null));

    Assertions.assertThat(exception)
        .isInstanceOf(FeelEngineWrapperException.class)
        .hasMessageContaining("Context is null");
  }

  @Test
  void evaluateToJson_ShouldNotFail_WhenVariablesAreNotMap() throws JSONException {
    // given
    // FEEL expression -> {"processedOutput":response.callStatus}
    final var resultExpression = "{\"processedOutput\": response.callStatus }";

    // when & then
    final var evaluatedResultAsJson =
        objectUnderTest.evaluateToJson(resultExpression, "I am not a map");

    JSONAssert.assertEquals(
        "{\"processedOutput\": null}", evaluatedResultAsJson, JSONCompareMode.STRICT);
  }

  @Test
  void evaluateToJson_ShouldNotFail_WhenCallingNonExistingFunction() {
    // given
    // FEEL expression -> {"processedOutput": camel case(response.callStatus)}
    // camel case function does not exist in FEEL
    final var resultExpression = "{\"processedOutput\": camel case(response.callStatus) }";
    // Response from service -> {"callStatus":"done"}
    final var variables = Map.of("callStatus", "done");
    Assertions.assertThatNoException()
        .isThrownBy(() -> objectUnderTest.evaluateToJson(resultExpression, variables));
  }

  @Test
  void evaluateToJson_ShouldNotFail_WhenCallingNonProperty() {
    final var resultExpression = "{\"processedOutput\": my.non.existing.property }";
    final var variables = Map.of("callStatus", "done");
    Assertions.assertThatNoException()
        .isThrownBy(() -> objectUnderTest.evaluateToJson(resultExpression, variables));
  }

  @Test
  void shouldSanitizeScalaMapOutput() {
    // given
    final var expression = "={\"processedOutput\": response.callStatus, \"response\": response }";
    final var variables = Map.of("callStatus", "200 OK");

    // when
    final var result =
        objectUnderTest.evaluate(expression, Object.class, variables, wrapResponse(variables));

    // then
    // result is not a scala map
    assertThat(result).isNotInstanceOf(scala.collection.Map.class);
    assertThat(result).isEqualTo(Map.of("processedOutput", "200 OK", "response", variables));
  }

  @Test
  void bpmnErrorFunction() {
    // given
    final var resultExpression = "=bpmnError(\"test\", \"test message\")";
    final var variables = Map.of("code", "TestCode", "message", "TestMessage");
    // when
    Map<String, Object> result = objectUnderTest.evaluate(resultExpression, variables);
    assertEquals("test", result.get("code"));
    assertEquals("test message", result.get("message"));
    assertNull(result.get("variables"));
  }

  @Test
  void bpmnErrorFunctionWithVars() {
    // given
    final var resultExpression = "=bpmnError(\"test\", \"test message\", errorVariables)";
    final var errorVariables = Map.of("errorVariable", "test");
    final var variables =
        Map.of("code", "TestCode", "message", "TestMessage", "errorVariables", errorVariables);
    // when
    Map<String, Object> result = objectUnderTest.evaluate(resultExpression, variables);
    assertEquals("test", result.get("code"));
    assertEquals("test message", result.get("message"));
    Map<String, Object> resultErrorVariables = (Map<String, Object>) result.get("variables");
    assertEquals(errorVariables.get("errorVariable"), resultErrorVariables.get("errorVariable"));
  }

  @Test
  void bpmnErrorFunctionWithCodeOnly() {
    // given
    final var resultExpression = "=bpmnError(\"test\")";
    final var variables = Map.of("code", "TestCode");
    // when
    final var result = objectUnderTest.evaluate(resultExpression, variables);
    assertNull(result);
  }

  @Test
  void failJobFunctionWithAllParameters() {
    // given
    final var resultExpression = "=jobError(message, {}, job.retries - 1, @\"PT1M\")";
    final var variables = Map.of("message", "some Message", "job", Map.of("retries", 3));
    // when
    final Map<String, Object> result = objectUnderTest.evaluate(resultExpression, variables);
    assertThat(result)
        .containsEntry("retries", 2)
        .containsEntry("retryBackoff", Duration.ofMinutes(1))
        .containsEntry("variables", Collections.emptyMap())
        .containsEntry("errorType", "jobError")
        .containsEntry("message", "some Message");
  }

  @Test
  void failJobFunctionWithoutRetryBackoff() {
    // given
    final var resultExpression = "=jobError(message, {}, 2)";
    final var variables = Map.of("message", "some Message");
    // when
    final Map<String, Object> result = objectUnderTest.evaluate(resultExpression, variables);
    assertThat(result)
        .containsEntry("retries", 2)
        .containsEntry("retryBackoff", Duration.ZERO)
        .containsEntry("variables", Collections.emptyMap())
        .containsEntry("errorType", "jobError")
        .containsEntry("message", "some Message");
  }

  @Test
  void failJobFunctionWithoutRetries() {
    // given
    final var resultExpression = "=jobError(message, {})";
    final var variables = Map.of("message", "some Message");
    // when
    final Map<String, Object> result = objectUnderTest.evaluate(resultExpression, variables);
    assertThat(result)
        .containsEntry("retries", 0)
        .containsEntry("retryBackoff", Duration.ZERO)
        .containsEntry("variables", Collections.emptyMap())
        .containsEntry("errorType", "jobError")
        .containsEntry("message", "some Message");
  }

  @Test
  void failJobFunctionWithoutVariables() {
    // given
    final var resultExpression = "=jobError(message)";
    final var variables = Map.of("message", "some Message");
    // when
    final Map<String, Object> result = objectUnderTest.evaluate(resultExpression, variables);
    assertThat(result)
        .containsEntry("retries", 0)
        .containsEntry("retryBackoff", Duration.ZERO)
        .containsEntry("variables", Collections.emptyMap())
        .containsEntry("errorType", "jobError")
        .containsEntry("message", "some Message");
  }

  @Test
  void bpmnErrorFunctionWithVarsButWrongDatatype() {
    // given
    final var resultExpression = "=bpmnError(\"test\", \"test\", \"test\")";
    final var variables = Map.of("code", "TestCode");
    // when
    assertThrowsExactly(
        FeelEngineWrapperException.class,
        () -> objectUnderTest.evaluate(resultExpression, variables));
  }

  record TestPojo(String value) {}
}
