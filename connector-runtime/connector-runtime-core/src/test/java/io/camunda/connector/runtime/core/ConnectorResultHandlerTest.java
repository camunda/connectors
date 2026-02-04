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
package io.camunda.connector.runtime.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.runtime.core.outbound.ErrorExpressionJobContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectorResultHandlerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final ConnectorResultHandler connectorResultHandler =
      new ConnectorResultHandler(objectMapper);

  @Test
  void feelEngineWrapperTest() {
    final var jsonDeserialized2 =
        Map.of(
            "data",
            List.of(
                Map.of("date", LocalDate.of(2024, 1, 1), "attr", "value1"),
                Map.of("date", LocalDate.of(2024, 2, 1), "attr", "value2")));

    final var actual =
        connectorResultHandler.createOutputVariables(
            jsonDeserialized2,
            null,
            """
                ={
                	res1: data[item.attr = "value1"][1].date,
                	res2: "hallo" + res1,
                	res3: 1 + 2,
                	res4: data[item.date = "2024-02-01"][1].attr,
                	res5: data[date(item.date) = date("2024-02-01")][1].attr,
                	res6: today()
                }
                """);

    assertThat(actual)
        .contains(
            Map.entry("res1", "2024-01-01"),
            Map.entry("res2", "hallo2024-01-01"),
            Map.entry("res3", 3),
            Map.entry("res4", "value2"),
            Map.entry("res5", "value2"),
            Map.entry("res6", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)));
  }

  @Test
  void ensureCanNotProduceIntrinsicFunction() {
    final String resultExpression =
        """
        {
          "camunda.function.type": myfun,
          "params": ["test"]
        }
        """;
    final Map<String, String> context = Map.of("myfun", "test");
    final var exception =
        assertThrows(
            ConnectorInputException.class,
            () -> connectorResultHandler.createOutputVariables(context, null, resultExpression));

    assertThat(exception)
        .hasMessageContaining(
            "The connector result contains a forbidden literal 'camunda.function.type'");
  }

  @Test
  void shouldHandleEmptyResponseBody() {
    // given - simulates HTTP response with empty/null body
    final String resultExpression = "={\"status\": response.status}";
    final Object responseContent = null;

    // when - should not throw exception even though responseContent is null
    final var actual =
        connectorResultHandler.createOutputVariables(responseContent, null, resultExpression);

    // then - should evaluate successfully with null values
    assertThat(actual).containsEntry("status", null);
  }

  @Test
  void shouldProvideGoodErrorMessage_WhenResultExpressionReturnsArray() {
    // given - result expression that produces an array
    final String resultExpression = "= [1, 2, 3]";
    final Object responseContent = Map.of();

    // when - should throw exception with clear message
    final var exception =
        assertThrows(
            ConnectorInputException.class,
            () ->
                connectorResultHandler.createOutputVariables(
                    responseContent, null, resultExpression));

    // then - should indicate that an array was returned and JSON object is expected
    assertThat(exception.getMessage())
        .contains("Result expression must return a JSON object")
        .contains("array")
        .contains("[1,2,3]");
  }

  @Test
  void shouldProvideGoodErrorMessage_WhenResultExpressionReturnsString() {
    // given - result expression that produces a string
    final String resultExpression = "= \"hello\"";
    final Object responseContent = Map.of();

    // when - should throw exception with clear message
    final var exception =
        assertThrows(
            ConnectorInputException.class,
            () ->
                connectorResultHandler.createOutputVariables(
                    responseContent, null, resultExpression));

    // then - should indicate that a string was returned and JSON object is expected
    assertThat(exception.getMessage())
        .contains("Result expression must return a JSON object")
        .contains("string")
        .contains("\"hello\"");
  }

  @Test
  void shouldProvideGoodErrorMessage_WhenResultExpressionReturnsNumber() {
    // given - result expression that produces a number
    final String resultExpression = "= 42";
    final Object responseContent = Map.of();

    // when - should throw exception with clear message
    final var exception =
        assertThrows(
            ConnectorInputException.class,
            () ->
                connectorResultHandler.createOutputVariables(
                    responseContent, null, resultExpression));

    // then - should indicate that a number was returned and JSON object is expected
    assertThat(exception.getMessage())
        .contains("Result expression must return a JSON object")
        .contains("number")
        .contains("42");
  }

  @Test
  void shouldProvideGoodErrorMessage_WhenResultExpressionReturnsBoolean() {
    // given - result expression that produces a boolean
    final String resultExpression = "= true";
    final Object responseContent = Map.of();

    // when - should throw exception with clear message
    final var exception =
        assertThrows(
            ConnectorInputException.class,
            () ->
                connectorResultHandler.createOutputVariables(
                    responseContent, null, resultExpression));

    // then - should indicate that a boolean was returned and JSON object is expected
    assertThat(exception.getMessage())
        .contains("Result expression must return a JSON object")
        .contains("boolean")
        .contains("true");
  }

  @Test
  void shouldProvideGoodErrorMessage_WhenErrorExpressionReturnsArray() {
    // given - error expression that produces an array (invalid type)
    final Object responseContent = Map.of("status", "error");
    final Map<String, String> jobHeaders = Map.of(Keywords.ERROR_EXPRESSION_KEYWORD, "= [1, 2, 3]");
    // ErrorExpressionJobContext is required as context for FEEL evaluation;
    // the retries count (3) is a dummy value that doesn't affect error message validation
    final ErrorExpressionJobContext jobContext =
        new ErrorExpressionJobContext(new ErrorExpressionJobContext.ErrorExpressionJob(3));

    // when - should throw exception with clear message
    final var exception =
        assertThrows(
            ConnectorInputException.class,
            () ->
                connectorResultHandler.examineErrorExpression(
                    responseContent, jobHeaders, jobContext));

    // then - should indicate that an array was returned and "Error expression" is mentioned
    assertThat(exception.getMessage())
        .contains("Error expression must return a JSON object")
        .contains("array")
        .contains("[1,2,3]");
  }
}
