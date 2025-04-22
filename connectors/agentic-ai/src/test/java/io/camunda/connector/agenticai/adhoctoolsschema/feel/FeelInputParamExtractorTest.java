/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.feel;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.adhoctoolsschema.feel.FeelInputParamExtractor.FeelInputParam;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class FeelInputParamExtractorTest {

  private final FeelInputParamExtractor extractor = new FeelInputParamExtractor();

  @ParameterizedTest
  @MethodSource("testFeelExpressionsWithExpectedInputParams")
  void extractsAllInputParametersFromExpression(FeelInputParamTestCase testCase) throws Exception {
    List<FeelInputParam> inputParams = extractor.extractInputParams(testCase.expression());
    assertThat(inputParams)
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactlyElementsOf(testCase.expectedInputParams());
  }

  public static List<FeelInputParamTestCase> testFeelExpressionsWithExpectedInputParams() {
    return List.of(
        new FeelInputParamTestCase(
            "Only expression: Name",
            """
            fromAi(toolCall.aSimpleValue)
            """,
            new FeelInputParam("aSimpleValue", null, null, null)),
        new FeelInputParamTestCase(
            "Only expression: Name + description",
            """
            fromAi(toolCall.aSimpleValue, "A simple value")
            """,
            new FeelInputParam("aSimpleValue", "A simple value", null, null)),
        new FeelInputParamTestCase(
            "Only expression: Name + description + type",
            """
            fromAi(toolCall.aSimpleValue, "A simple value", "string")
            """,
            new FeelInputParam("aSimpleValue", "A simple value", "string", null)),
        new FeelInputParamTestCase(
            "Only expression: Name + description + type + schema",
            """
            fromAi(toolCall.aSimpleValue, "A simple value", "string", { enum: ["A", "B", "C"] })
            """,
            new FeelInputParam(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")))),
        new FeelInputParamTestCase(
            "Only expression: Name + description + type + schema (expressions to generate schema)",
            """
            fromAi(toolCall.aSimpleValue, "A simple value", "string", context put({}, "enum", ["A", "B", "C"]))
            """,
            new FeelInputParam(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")))),
        new FeelInputParamTestCase(
            "Only expression: Name (named params)",
            """
            fromAi(value: toolCall.aSimpleValue)
            """,
            new FeelInputParam("aSimpleValue", null, null, null)),
        new FeelInputParamTestCase(
            "Only expression: Name + description (named params)",
            """
            fromAi(value: toolCall.aSimpleValue, description: "A simple value")
            """,
            new FeelInputParam("aSimpleValue", "A simple value", null, null)),
        new FeelInputParamTestCase(
            "Only expression: Name + description + type (named params)",
            """
            fromAi(value: toolCall.aSimpleValue, description: "A simple value", type: "string")
            """,
            new FeelInputParam("aSimpleValue", "A simple value", "string", null)),
        new FeelInputParamTestCase(
            "Only expression: Name + description + type + schema (named params)",
            """
            fromAi(value: toolCall.aSimpleValue, description: "A simple value", type: "string", schema: { enum: ["A", "B", "C"] })
            """,
            new FeelInputParam(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")))),
        new FeelInputParamTestCase(
            "Only expression: Name + description + type + schema (named params, mixed order)",
            """
            fromAi(description: "A simple value", schema: { enum: ["A", "B", "C"] }, type: "string", value: toolCall.aSimpleValue)
            """,
            new FeelInputParam(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")))),
        new FeelInputParamTestCase(
            "Only expression: Name + description + type + schema (named params, mixed order, expression to generate schema)",
            """
            fromAi(description: "A simple value", schema: context put({}, "enum", ["A", "B", "C"]), type: "string", value: toolCall.aSimpleValue)
            """,
            new FeelInputParam(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")))),
        new FeelInputParamTestCase(
            "Array schema with sub-schema",
            """
            fromAi(toolCall.multiValue, "Select a multi value", "array", {
              "items": {
                "type": "string",
                "enum": ["foo", "bar", "baz"]
              }
            })
            """,
            new FeelInputParam(
                "multiValue",
                "Select a multi value",
                "array",
                Map.of("items", Map.of("type", "string", "enum", List.of("foo", "bar", "baz"))))),
        new FeelInputParamTestCase(
            "Part of operation (integer)",
            """
            1 + 2 + fromAi(toolCall.thirdValue, "The third value to add", "integer")
            """,
            new FeelInputParam("thirdValue", "The third value to add", "integer", null)),
        new FeelInputParamTestCase(
            "Part of string concatenation",
            """
            "https://example.com/" + fromAi(toolCall.urlPath, "The URL path to use", "string")
            """,
            new FeelInputParam("urlPath", "The URL path to use", "string", null)),
        new FeelInputParamTestCase(
            "Multiple parameters, part of a context",
            """
            {
              foo: "bar",
              bar: fromAi(toolCall.barValue, "A good bar value", "string"),
              combined: fromAi(toolCall.firstOne, "The first value") + fromAi(toolCall.secondOne, "The second value", "string")
            }
            """,
            new FeelInputParam("barValue", "A good bar value", "string", null),
            new FeelInputParam("firstOne", "The first value", null, null),
            new FeelInputParam("secondOne", "The second value", "string", null)),
        new FeelInputParamTestCase(
            "Multiple parameters, part of a list",
            """
            ["something", fromAi(toolCall.firstValue, "The first value", "string"), fromAi(toolCall.secondValue, "The second value", "integer")]
            """,
            new FeelInputParam("firstValue", "The first value", "string", null),
            new FeelInputParam("secondValue", "The second value", "integer", null)),
        new FeelInputParamTestCase(
            "Multiple parameters, part of a context and list",
            """
            {
              foo: [fromAi(toolCall.firstValue, "The first value", "string"), fromAi(toolCall.secondValue, "The second value", "integer")],
              bar: {
                baz: fromAi(toolCall.thirdValue, "The third value to add")
              }
            }
            """,
            new FeelInputParam("firstValue", "The first value", "string", null),
            new FeelInputParam("secondValue", "The second value", "integer", null),
            new FeelInputParam("thirdValue", "The third value to add", null, null)),
        new FeelInputParamTestCase(
            "Multiple parameters, part of a context and list (named params)",
            """
            {
              foo: [fromAi(value: toolCall.firstValue, description: "The first value", type: "string"), fromAi(description: "The second value", type: "integer", value: toolCall.secondValue)],
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
            new FeelInputParam("firstValue", "The first value", "string", null),
            new FeelInputParam("secondValue", "The second value", "integer", null),
            new FeelInputParam("thirdValue", "The third value to add", null, null),
            new FeelInputParam(
                "fourthValue",
                "The fourth value to add",
                "array",
                Map.of("items", Map.of("type", "string", "enum", List.of("foo", "bar", "baz"))))));
  }

  record FeelInputParamTestCase(
      String description, String expression, List<FeelInputParam> expectedInputParams) {

    FeelInputParamTestCase(
        String description, String expression, FeelInputParam... expectedInputParams) {
      this(description, expression, List.of(expectedInputParams));
    }

    @Override
    public String toString() {
      return "%s: %s".formatted(description, expression);
    }
  }
}
