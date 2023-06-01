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
package io.camunda.connector.runtime.core.feel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeelEngineWrapperExpressionEvaluationTest {

  private FeelEngineWrapper objectUnderTest;

  @BeforeEach
  void beforeEach() {
    objectUnderTest = new FeelEngineWrapper();
  }

  @Test
  void evaluateToJson_ShouldSucceed_WhenHappyCase() {
    // given
    // FEEL expression -> {"processedOutput":response.callStatus}
    final var resultExpression = "{\"processedOutput\": response.callStatus }";
    // Response from service -> {"callStatus":{"statusCode":"200 OK"}}
    final var variables = Map.of("callStatus", Map.of("statusCode", "200 OK"));

    // when
    final var evaluatedResultAsJson = objectUnderTest.evaluateToJson(resultExpression, variables);

    // then
    assertThat(evaluatedResultAsJson)
        .isEqualTo("{\"processedOutput\":{\"statusCode\":\"200 OK\"}}");
  }

  @Test
  void evaluate_ShouldSucceed_WhenHappyCaseJavaType() {
    // given
    // FEEL expression -> {"processedOutput":response.callStatus}
    final var resultExpression = "{\"processedOutput\": response.callStatus }";
    // Response from service -> {"callStatus":{"statusCode":"200 OK"}}
    final var variables = Map.of("callStatus", Map.of("statusCode", "200 OK"));

    // when
    final var evaluatedResultAsMap = objectUnderTest.evaluate(resultExpression, variables);

    // then
    final var expectedResult = Map.of("processedOutput", Map.of("statusCode", "200 OK"));
    assertThat(evaluatedResultAsMap).isEqualTo(expectedResult);
  }

  @Test
  void evaluateToJson_ShouldSucceed_WhenHandlingPojo() {
    // given
    final var resultExpression = "= { value: response.value, response: response }";
    final var variables = new TestPojo("FOO");

    // when
    final var evaluatedResultAsJson = objectUnderTest.evaluateToJson(resultExpression, variables);

    // then
    assertThat(evaluatedResultAsJson)
        .isEqualTo("{\"response\":{\"value\":\"FOO\"},\"value\":\"FOO\"}");
  }

  @Test
  void evaluateToJson_ShouldSucceed_WhenExpressionStartsWithEqualsSign() {
    // given
    // FEEL expression -> ={"processedOutput":response.callStatus}
    final var resultExpression = "={\"processedOutput\": response.callStatus }";
    // Response from service -> {"callStatus":{"statusCode":"200 OK"}}
    final var variables = Map.of("callStatus", Map.of("statusCode", "200 OK"));

    // when
    final var evaluatedResultAsJson = objectUnderTest.evaluateToJson(resultExpression, variables);

    // then
    assertThat(evaluatedResultAsJson)
        .isEqualTo("{\"processedOutput\":{\"statusCode\":\"200 OK\"}}");
  }

  @Test
  void evaluateToJson_ShouldSucceed_WhenVariableNotFound() {
    // given
    // FEEL expression -> ={"processedOutput":response.doesnt-exist}
    final var resultExpression = "={\"processedOutput\": response.doesnt-exist }";
    // Response from service -> {"callStatus":{"statusCode":"200 OK"}}
    final var variables = Map.of("callStatus", Map.of("statusCode", "200 OK"));

    // when
    final var evaluatedResultAsJson = objectUnderTest.evaluateToJson(resultExpression, variables);

    // then
    assertThat(evaluatedResultAsJson).isEqualTo("{\"processedOutput\":null}");
  }

  @Test
  void evaluateToJson_ShouldSucceed_WhenUsedBuiltInFunction() {
    // given
    // FEEL expression -> {"processedOutput": upper case(response.callStatus)}
    final var resultExpression = "{\"processedOutput\": upper case(response.callStatus) }";
    // Response from service -> {"callStatus":"done"}
    final var variables = Map.of("callStatus", "done");

    // when
    final var evaluatedResultAsJson = objectUnderTest.evaluateToJson(resultExpression, variables);

    // then
    assertThat(evaluatedResultAsJson)
        .isEqualTo("{\"processedOutput\":\"DONE\"}"); // processedOutput in upper-case!
  }

  @Test
  void evaluateToJson_ShouldFail_WhenVariablesAreNull() {
    // given
    // FEEL expression -> {"processedOutput":response.callStatus}
    final var resultExpression = "{\"processedOutput\": response.callStatus }";

    // when & then
    final var exception =
        catchThrowable(() -> objectUnderTest.evaluateToJson(resultExpression, null));

    assertThat(exception)
        .isInstanceOf(FeelEngineWrapperException.class)
        .hasMessageContaining("Context is null");
  }

  @Test
  void evaluateToJson_ShouldFail_WhenVariablesAreNotMap() {
    // given
    // FEEL expression -> {"processedOutput":response.callStatus}
    final var resultExpression = "{\"processedOutput\": response.callStatus }";

    // when & then
    final var exception =
        catchThrowable(() -> objectUnderTest.evaluateToJson(resultExpression, "I am not a map"));

    assertThat(exception)
        .isInstanceOf(FeelEngineWrapperException.class)
        .hasMessageContaining("Unable to parse 'I am not a map' as context");
  }

  @Test
  void evaluateToJson_ShouldFail_WhenFeelEngineRaisesException() {
    // given
    // FEEL expression -> {"processedOutput": camel case(response.callStatus)}
    // camel case function does not exist in FEEL
    final var resultExpression = "{\"processedOutput\": camel case(response.callStatus) }";
    // Response from service -> {"callStatus":"done"}
    final var variables = Map.of("callStatus", "done");

    // when
    final var exception =
        catchThrowable(() -> objectUnderTest.evaluateToJson(resultExpression, variables));

    // then
    assertThat(exception)
        .isInstanceOf(FeelEngineWrapperException.class)
        .hasMessageContaining("no function found with name 'camel case'");
  }

  class TestPojo {

    private String value;

    public TestPojo(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }
}
