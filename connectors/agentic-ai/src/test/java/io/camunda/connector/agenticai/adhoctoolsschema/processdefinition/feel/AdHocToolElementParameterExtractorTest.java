/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.feel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElementParameter;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class AdHocToolElementParameterExtractorTest {

  private final AdHocToolElementParameterExtractor extractor =
      new AdHocToolElementParameterExtractorImpl();

  @ParameterizedTest
  @MethodSource("testFeelExpressionsWithExpectedParameters")
  void extractsAllParametersFromExpression(
      final AdHocToolElementParameterExtractionTestCase testCase) {
    final List<AdHocToolElementParameter> parameters =
        extractor.extractParameters(testCase.expression());

    if (testCase.expectedParameters.isEmpty()) {
      assertThat(parameters).isEmpty();
    } else {
      assertThat(parameters)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactlyElementsOf(testCase.expectedParameters());
    }
  }

  @ParameterizedTest
  @CsvSource({
    "\"toolCall.myVariable\",string 'toolCall.myVariable'",
    "10,10",
    "[],ConstList(List())"
  })
  void throwsExceptionWhenValueIsNotAReference(
      final String parameter, final String exceptionMessage) {
    assertThatThrownBy(() -> extractor.extractParameters("fromAi(%s)".formatted(parameter)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "Expected fromAi() parameter 'value' to be a reference (e.g. 'toolCall.customParameter'), but received "
                + exceptionMessage);
  }

  @ParameterizedTest
  @MethodSource("parameterTypeMismatches")
  void throwsExceptionOnParameterTypeMismatch(
      final String expression, final String exceptionMessage) {
    assertThatThrownBy(() -> extractor.extractParameters(expression))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith(exceptionMessage);
  }

  static List<AdHocToolElementParameterExtractionTestCase>
      testFeelExpressionsWithExpectedParameters() {
    return List.of(
        new AdHocToolElementParameterExtractionTestCase(
            "No parameters",
            """
                "hello"
                """),
        new AdHocToolElementParameterExtractionTestCase(
            "Only expression: Name",
            """
                fromAi(toolCall.aSimpleValue)
                """,
            new AdHocToolElementParameter("toolCall.aSimpleValue", null, null, null, null)),
        new AdHocToolElementParameterExtractionTestCase(
            "Only expression: Name + description",
            """
                fromAi(toolCall.aSimpleValue, "A simple value")
                """,
            new AdHocToolElementParameter(
                "toolCall.aSimpleValue", "A simple value", null, null, null)),
        new AdHocToolElementParameterExtractionTestCase(
            "Only expression: Name + description + type",
            """
                fromAi(toolCall.aSimpleValue, "A simple value", "string")
                """,
            new AdHocToolElementParameter(
                "toolCall.aSimpleValue", "A simple value", "string", null, null)),
        new AdHocToolElementParameterExtractionTestCase(
            "Only expression: Name + description + type + schema",
            """
                fromAi(toolCall.aSimpleValue, "A simple value", "string", { enum: ["A", "B", "C"] })
                """,
            new AdHocToolElementParameter(
                "toolCall.aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                null)),
        new AdHocToolElementParameterExtractionTestCase(
            "Only expression: Name + description + type + schema + options",
            """
                fromAi(toolCall.aSimpleValue, "A simple value", "string", { enum: ["A", "B", "C"] }, { optional: true })
                """,
            new AdHocToolElementParameter(
                "toolCall.aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                Map.of("optional", true))),
        new AdHocToolElementParameterExtractionTestCase(
            "Only expression: Name + description + type + schema + options (value not a child reference)",
            """
                fromAi(aSimpleValue, "A simple value", "string", { enum: ["A", "B", "C"] }, { optional: true })
                """,
            new AdHocToolElementParameter(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                Map.of("optional", true))),
        new AdHocToolElementParameterExtractionTestCase(
            "Only expression: Name (named params)",
            """
                fromAi(value: toolCall.aSimpleValue)
                """,
            new AdHocToolElementParameter("toolCall.aSimpleValue", null, null, null, null)),
        new AdHocToolElementParameterExtractionTestCase(
            "Only expression: Name + description (named params)",
            """
                fromAi(value: toolCall.aSimpleValue, description: "A simple value")
                """,
            new AdHocToolElementParameter(
                "toolCall.aSimpleValue", "A simple value", null, null, null)),
        new AdHocToolElementParameterExtractionTestCase(
            "Only expression: Name + description + type (named params)",
            """
                fromAi(value: toolCall.aSimpleValue, description: "A simple value", type: "string")
                """,
            new AdHocToolElementParameter(
                "toolCall.aSimpleValue", "A simple value", "string", null, null)),
        new AdHocToolElementParameterExtractionTestCase(
            "Only expression: Name + description + type + schema (named params)",
            """
                fromAi(value: toolCall.aSimpleValue, description: "A simple value", type: "string", schema: { enum: ["A", "B", "C"] })
                """,
            new AdHocToolElementParameter(
                "toolCall.aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                null)),
        new AdHocToolElementParameterExtractionTestCase(
            "Only expression: Name + description + type + schema + options (named params)",
            """
                fromAi(
                  value: toolCall.aSimpleValue,
                  description: "A simple value",
                  type: "string",
                  schema: { enum: ["A", "B", "C"] },
                  options: { optional: true }
                )
                """,
            new AdHocToolElementParameter(
                "toolCall.aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                Map.of("optional", true))),
        new AdHocToolElementParameterExtractionTestCase(
            "Only expression: Name + description + type + schema + options (named params, mixed order)",
            """
                fromAi(
                  description: "A simple value",
                  options: { optional: true },
                  schema: { enum: ["A", "B", "C"] },
                  type: "string",
                  value: toolCall.aSimpleValue
                )
                """,
            new AdHocToolElementParameter(
                "toolCall.aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                Map.of("optional", true))),
        new AdHocToolElementParameterExtractionTestCase(
            "Only expression: Name + description + type + schema + options (named params, mixed order, value not a child reference)",
            """
                fromAi(
                  description: "A simple value",
                  options: { optional: true },
                  schema: { enum: ["A", "B", "C"] },
                  type: "string",
                  value: aSimpleValue
                )
                """,
            new AdHocToolElementParameter(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                Map.of("optional", true))),
        new AdHocToolElementParameterExtractionTestCase(
            "Array schema with sub-schema",
            """
                fromAi(toolCall.multiValue, "Select a multi value", "array", {
                  "items": {
                    "type": "string",
                    "enum": ["foo", "bar", "baz"]
                  }
                })
                """,
            new AdHocToolElementParameter(
                "toolCall.multiValue",
                "Select a multi value",
                "array",
                Map.of("items", Map.of("type", "string", "enum", List.of("foo", "bar", "baz"))),
                null)),
        new AdHocToolElementParameterExtractionTestCase(
            "Part of operation (integer)",
            """
                1 + 2 + fromAi(toolCall.thirdValue, "The third value to add", "integer")
                """,
            new AdHocToolElementParameter(
                "toolCall.thirdValue", "The third value to add", "integer", null, null)),
        new AdHocToolElementParameterExtractionTestCase(
            "Part of operation with conversion (integer)",
            """
                1 + 2 + number(fromAi(toolCall.thirdValue, "The third value to add", "integer"))
                """,
            new AdHocToolElementParameter(
                "toolCall.thirdValue", "The third value to add", "integer", null, null)),
        new AdHocToolElementParameterExtractionTestCase(
            "Part of string concatenation",
            """
                "https://example.com/" + fromAi(toolCall.urlPath, "The URL path to use", "string")
                """,
            new AdHocToolElementParameter(
                "toolCall.urlPath", "The URL path to use", "string", null, null)),
        new AdHocToolElementParameterExtractionTestCase(
            "Part of string concatenation with conversion",
            """
                "https://example.com/" + string(fromAi(toolCall.urlPath, "The URL path to use", "string"))
                """,
            new AdHocToolElementParameter(
                "toolCall.urlPath", "The URL path to use", "string", null, null)),
        new AdHocToolElementParameterExtractionTestCase(
            "Multiple parameters, part of a context",
            """
                {
                  foo: "bar",
                  bar: fromAi(barValue, "A good bar value", "string"),
                  combined: string(fromAi(toolCall.firstOne, "The first value")) + fromAi(toolCall.secondOne, "The second value", "string")
                }
                """,
            new AdHocToolElementParameter("barValue", "A good bar value", "string", null, null),
            new AdHocToolElementParameter("toolCall.firstOne", "The first value", null, null, null),
            new AdHocToolElementParameter(
                "toolCall.secondOne", "The second value", "string", null, null)),
        new AdHocToolElementParameterExtractionTestCase(
            "Multiple parameters, part of a list",
            """
                ["something", fromAi(toolCall.firstValue, "The first value", "string"), fromAi(toolCall.secondValue, "The second value", "integer")]
                """,
            new AdHocToolElementParameter(
                "toolCall.firstValue", "The first value", "string", null, null),
            new AdHocToolElementParameter(
                "toolCall.secondValue", "The second value", "integer", null, null)),
        new AdHocToolElementParameterExtractionTestCase(
            "Multiple parameters, part of a context and list",
            """
                {
                  foo: [fromAi(toolCall.firstValue, "The first value", "string"), fromAi(toolCall.secondValue, "The second value", "integer")],
                  bar: {
                    baz: fromAi(toolCall.thirdValue, "The third value to add")
                  }
                }
                """,
            new AdHocToolElementParameter(
                "toolCall.firstValue", "The first value", "string", null, null),
            new AdHocToolElementParameter(
                "toolCall.secondValue", "The second value", "integer", null, null),
            new AdHocToolElementParameter(
                "toolCall.thirdValue", "The third value to add", null, null, null)),
        new AdHocToolElementParameterExtractionTestCase(
            "Multiple parameters, part of a context and list (named params)",
            """
                {
                  foo: [
                    fromAi(value: toolCall.firstValue, description: "The first value", type: "string"),
                    fromAi(description: "The second value", type: "integer", value: toolCall.secondValue)
                  ],
                  bar: {
                    baz: fromAi(value: toolCall.thirdValue, description: "The third value to add"),
                    qux: fromAi(value: toolCall.fourthValue, description: "The fourth value to add", type: "array", schema: {
                      "items": {
                        "type": "string",
                        "enum": ["foo", "bar", "baz"]
                      }
                    })
                  }
                }
                """,
            new AdHocToolElementParameter(
                "toolCall.firstValue", "The first value", "string", null, null),
            new AdHocToolElementParameter(
                "toolCall.secondValue", "The second value", "integer", null, null),
            new AdHocToolElementParameter(
                "toolCall.thirdValue", "The third value to add", null, null, null),
            new AdHocToolElementParameter(
                "toolCall.fourthValue",
                "The fourth value to add",
                "array",
                Map.of("items", Map.of("type", "string", "enum", List.of("foo", "bar", "baz"))),
                null)));
  }

  static Stream<Arguments> parameterTypeMismatches() {
    return Stream.of(
        arguments(
            "fromAi(value: toolCall.myVariable, description: 10)",
            "Expected fromAi() parameter 'description' to be a string, but received '10'."),
        arguments(
            "fromAi(value: toolCall.myVariable, description: string join([\"A\", \"simple\", \"value\"], \" \"))",
            "Expected fromAi() parameter 'description' to be a string, but received 'FunctionInvocation"),
        arguments(
            "fromAi(value: toolCall.myVariable, type: 10)",
            "Expected fromAi() parameter 'type' to be a string, but received '10'."),
        arguments(
            "fromAi(value: toolCall.myVariable, type: \"str\" + \"ing\")",
            "Expected fromAi() parameter 'type' to be a string, but received 'Addition"),
        arguments(
            "fromAi(value: toolCall.myVariable, schema: \"dummy\")",
            "Expected fromAi() parameter 'schema' to be a context (map), but received 'dummy'."),
        arguments(
            "fromAi(value: toolCall.myVariable, schema: context put({}, \"enum\", [\"A\", \"B\", \"C\"]))",
            "Expected fromAi() parameter 'schema' to be a context (map), but received 'FunctionInvocation"),
        arguments(
            "fromAi(value: toolCall.myVariable, options: \"dummy\")",
            "Expected fromAi() parameter 'options' to be a context (map), but received 'dummy'."),
        arguments(
            "fromAi(value: toolCall.myVariable, options: context put({}, \"required\", false))",
            "Expected fromAi() parameter 'options' to be a context (map), but received 'FunctionInvocation"));
  }

  record AdHocToolElementParameterExtractionTestCase(
      String description, String expression, List<AdHocToolElementParameter> expectedParameters) {

    AdHocToolElementParameterExtractionTestCase(
        final String description,
        final String expression,
        final AdHocToolElementParameter... expectedParameters) {
      this(description, expression, List.of(expectedParameters));
    }

    @Override
    public String toString() {
      return "%s: %s".formatted(description, expression);
    }
  }
}
