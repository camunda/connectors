/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.feel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElementParameter;
import io.camunda.zeebe.feel.tagged.impl.TaggedParameterExtractor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdHocToolElementParameterExtractorTest {

  private final AdHocToolElementParameterExtractorImpl parameterExtractor =
      new AdHocToolElementParameterExtractorImpl(new TaggedParameterExtractor());

  @Test
  void returnsTaggedParametersFromExpression() {
    final var expression =
        """
        {
          foo: [
            fromAi(value: toolCall.firstValue, description: "The first value", type: "string"),
            fromAi(description: "The second value", type: "integer", value: toolCall.secondValue)
          ],
          bar: {
            baz: fromAi(value: toolCall.thirdValue, description: "The third value to add", options: { required: false }),
            qux: fromAi(value: toolCall.fourthValue, description: "The fourth value to add", type: "array", schema: {
              "items": {
                "type": "string",
                "enum": ["foo", "bar", "baz"]
              }
            })
          }
        }
        """;

    final var parameters = parameterExtractor.extractParameters(expression);

    assertThat(parameters)
        .containsExactly(
            new AdHocToolElementParameter(
                "toolCall.firstValue", "The first value", "string", Map.of(), Map.of()),
            new AdHocToolElementParameter(
                "toolCall.secondValue", "The second value", "integer", Map.of(), Map.of()),
            new AdHocToolElementParameter(
                "toolCall.thirdValue",
                "The third value to add",
                null,
                Map.of(),
                Map.of("required", false)),
            new AdHocToolElementParameter(
                "toolCall.fourthValue",
                "The fourth value to add",
                "array",
                Map.of("items", Map.of("type", "string", "enum", List.of("foo", "bar", "baz"))),
                Map.of()));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "1 + 1",
        "\"no parameters here\"",
        "true",
        "someVariable + someOtherVariable",
        "if this then that else other"
      })
  void returnsEmptyListWhenNoParametersAreFound(String expression) {
    assertThat(parameterExtractor.extractParameters(expression)).isEmpty();
  }

  @Test
  void throwsExceptionWhenExpressionIsInvalid() {
    assertThatThrownBy(() -> parameterExtractor.extractParameters("invalid expression"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "Failed to parse FEEL expression: failed to parse expression 'invalid expression':");
  }
}
