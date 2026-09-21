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
  void resultVariableRejectsAForbiddenLiteralInTheResponseContent() {
    // SFD-069 route C: with resultVariable alone (no resultExpression), verifyNoForbiddenLiterals
    // previously never ran at all, so a webhook payload shaped like an intrinsic-function call
    // was written straight into a process variable, unchecked. This is the branch the finding's
    // remediation hint #3 asks to close.
    Object responseContent =
        Map.of(
            "probe",
            Map.of(
                "camunda.function.type",
                "createLink",
                "params",
                List.of(Map.of("camunda.document.type", "camunda"), "PT1H")));

    final var exception =
        assertThrows(
            ConnectorInputException.class,
            () ->
                connectorResultHandler.createOutputVariables(responseContent, "hookResult", null));

    assertThat(exception)
        .hasMessageContaining(
            "The connector result contains a forbidden literal 'camunda.function.type'");
  }

  @Test
  void resultVariableAllowsOrdinaryResponseDataWithoutALiteral() {
    Object responseContent = Map.of("status", "ok", "count", 3);

    Map<String, Object> result =
        connectorResultHandler.createOutputVariables(responseContent, "hookResult", null);

    assertThat(result).containsEntry("hookResult", responseContent);
  }

  @Test
  void resultVariableAllowsABenignStringValueThatHappensToMatchTheForbiddenLiteral() {
    // security-testing-findings#275, T10: the literal appears only as a plain STRING VALUE here,
    // never as an object key, so it can never reach the intrinsic-function executor (which only
    // ever looks for the discriminator as a key). The previous substring-over-serialized-JSON
    // check flagged this anyway, rejecting an entirely benign HTTP response body.
    Object responseContent = Map.of("message", "camunda.function.type");

    Map<String, Object> result =
        connectorResultHandler.createOutputVariables(responseContent, "hookResult", null);

    assertThat(result).containsEntry("hookResult", responseContent);
  }

  @Test
  void resultVariableAllowsADocumentReferenceWithoutTreatingItAsForbidden() {
    // A plain document reference must not be mistaken for the forbidden intrinsic-function
    // literal — only IntrinsicFunctionModel.DISCRIMINATOR_KEY is blocked, not document references.
    Object responseContent =
        Map.of(
            "camunda.document.type", "camunda",
            "storeId", "s",
            "documentId", "d");

    Map<String, Object> result =
        connectorResultHandler.createOutputVariables(responseContent, "hookResult", null);

    assertThat(result).containsEntry("hookResult", responseContent);
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
}
