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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class FeelInputParamExtractorTest {

  private final FeelInputParamExtractor extractor = new FeelInputParamExtractorImpl();

  @ParameterizedTest
  @MethodSource("testFeelExpressionsWithExpectedInputParams")
  void extractsAllInputParametersFromExpression(FeelInputParamTestCase testCase) {
    List<AdHocToolElementParameter> inputParams =
        extractor.extractInputParams(testCase.expression());

    if (testCase.expectedInputParams.isEmpty()) {
      assertThat(inputParams).isEmpty();
    } else {
      assertThat(inputParams)
          .usingRecursiveFieldByFieldElementComparator()
          .containsExactlyElementsOf(testCase.expectedInputParams());
    }
  }

  @Test
  void throwsExceptionWhenExpressionIsNotParseable() {
    assertThatThrownBy(() -> extractor.extractInputParams("hello\""))
        .isInstanceOf(FeelInputParamExtractionException.class)
        .hasMessageStartingWith(
            "Failed to parse FEEL expression: failed to parse expression 'hello\"'");
  }

  @ParameterizedTest
  @CsvSource({
    "\"toolCall.myVariable\",string 'toolCall.myVariable'",
    "10,ConstNumber(10)",
    "[],ConstList(List())"
  })
  void throwsExceptionWhenValueIsNotAReference(String parameter, String exceptionMessage) {
    assertThatThrownBy(() -> extractor.extractInputParams("fromAi(%s)".formatted(parameter)))
        .isInstanceOf(FeelInputParamExtractionException.class)
        .hasMessageStartingWith(
            "Expected parameter 'value' to be a reference (e.g. 'toolCall.customParameter'), but received "
                + exceptionMessage);
  }

  @Test
  void throwsExceptionWhenDescriptionValueIsNotAString() {
    assertThatThrownBy(
            () ->
                extractor.extractInputParams("fromAi(value: toolCall.myVariable, description: 10)"))
        .isInstanceOf(FeelInputParamExtractionException.class)
        .hasMessageStartingWith(
            "Expected parameter 'description' to be a string, but received '10'");
  }

  @Test
  void throwsExceptionWhenTypeValueIsNotAString() {
    assertThatThrownBy(
            () -> extractor.extractInputParams("fromAi(value: toolCall.myVariable, type: 10)"))
        .isInstanceOf(FeelInputParamExtractionException.class)
        .hasMessageStartingWith("Expected parameter 'type' to be a string, but received '10'.");
  }

  @Test
  void throwsExceptionWhenSchemaValueIsNotAContext() {
    assertThatThrownBy(
            () ->
                extractor.extractInputParams(
                    "fromAi(value: toolCall.myVariable, schema: \"dummy\")"))
        .isInstanceOf(FeelInputParamExtractionException.class)
        .hasMessageStartingWith("Expected parameter 'schema' to be a map, but received 'dummy'.");
  }

  @Test
  void throwsExceptionWhenOptionsValueIsNotAContext() {
    assertThatThrownBy(
            () ->
                extractor.extractInputParams(
                    "fromAi(value: toolCall.myVariable, options: \"dummy\")"))
        .isInstanceOf(FeelInputParamExtractionException.class)
        .hasMessageStartingWith("Expected parameter 'options' to be a map, but received 'dummy'.");
  }

  static List<FeelInputParamTestCase> testFeelExpressionsWithExpectedInputParams() {
    return List.of(
        new FeelInputParamTestCase(
            "No parameters",
            """
            "hello"
            """),
        new FeelInputParamTestCase(
            "Only expression: Name",
            """
            fromAi(toolCall.aSimpleValue)
            """,
            new AdHocToolElementParameter("aSimpleValue")),
        new FeelInputParamTestCase(
            "Only expression: Name + description",
            """
            fromAi(toolCall.aSimpleValue, "A simple value")
            """,
            new AdHocToolElementParameter("aSimpleValue", "A simple value")),
        new FeelInputParamTestCase(
            "Only expression: Name + description + type",
            """
            fromAi(toolCall.aSimpleValue, "A simple value", "string")
            """,
            new AdHocToolElementParameter("aSimpleValue", "A simple value", "string")),
        new FeelInputParamTestCase(
            "Only expression: Name + description + type + schema",
            """
            fromAi(toolCall.aSimpleValue, "A simple value", "string", { enum: ["A", "B", "C"] })
            """,
            new AdHocToolElementParameter(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")))),
        new FeelInputParamTestCase(
            "Only expression: Name + description + type + schema + options",
            """
            fromAi(toolCall.aSimpleValue, "A simple value", "string", { enum: ["A", "B", "C"] }, { optional: true })
            """,
            new AdHocToolElementParameter(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                Map.of("optional", true))),
        new FeelInputParamTestCase(
            "Only expression: Name + description + type + schema + options (expressions to generate params)",
            """
            fromAi(toolCall.aSimpleValue, string join(["A", "simple", "value"], " "), "str" + "ing", context put({}, "enum", ["A", "B", "C"]), { optional: not(false) })
            """,
            new AdHocToolElementParameter(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                Map.of("optional", true))),
        new FeelInputParamTestCase(
            "Only expression: Name (named params)",
            """
            fromAi(value: toolCall.aSimpleValue)
            """,
            new AdHocToolElementParameter("aSimpleValue")),
        new FeelInputParamTestCase(
            "Only expression: Name + description (named params)",
            """
            fromAi(value: toolCall.aSimpleValue, description: "A simple value")
            """,
            new AdHocToolElementParameter("aSimpleValue", "A simple value")),
        new FeelInputParamTestCase(
            "Only expression: Name + description + type (named params)",
            """
            fromAi(value: toolCall.aSimpleValue, description: "A simple value", type: "string")
            """,
            new AdHocToolElementParameter("aSimpleValue", "A simple value", "string")),
        new FeelInputParamTestCase(
            "Only expression: Name + description + type + schema (named params)",
            """
            fromAi(value: toolCall.aSimpleValue, description: "A simple value", type: "string", schema: { enum: ["A", "B", "C"] })
            """,
            new AdHocToolElementParameter(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")))),
        new FeelInputParamTestCase(
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
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                Map.of("optional", true))),
        new FeelInputParamTestCase(
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
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                Map.of("optional", true))),
        new FeelInputParamTestCase(
            "Only expression: Name + description + type + schema + options (named params, mixed order, expressions to generate params)",
            """
            fromAi(
              description: string join(["A", "simple", "value"], " "),
              options: { optional: not(false) },
              schema: context put({}, "enum", ["A", "B", "C"]),
              type: "str" + "ing",
              value: toolCall.aSimpleValue
            )
            """,
            new AdHocToolElementParameter(
                "aSimpleValue",
                "A simple value",
                "string",
                Map.of("enum", List.of("A", "B", "C")),
                Map.of("optional", true))),
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
            new AdHocToolElementParameter(
                "multiValue",
                "Select a multi value",
                "array",
                Map.of("items", Map.of("type", "string", "enum", List.of("foo", "bar", "baz"))))),
        new FeelInputParamTestCase(
            "Part of operation (integer)",
            """
            1 + 2 + fromAi(toolCall.thirdValue, "The third value to add", "integer")
            """,
            new AdHocToolElementParameter("thirdValue", "The third value to add", "integer")),
        new FeelInputParamTestCase(
            "Part of string concatenation",
            """
            "https://example.com/" + fromAi(toolCall.urlPath, "The URL path to use", "string")
            """,
            new AdHocToolElementParameter("urlPath", "The URL path to use", "string")),
        new FeelInputParamTestCase(
            "Multiple parameters, part of a context",
            """
            {
              foo: "bar",
              bar: fromAi(toolCall.barValue, "A good bar value", "string"),
              combined: fromAi(toolCall.firstOne, "The first value") + fromAi(toolCall.secondOne, "The second value", "string")
            }
            """,
            new AdHocToolElementParameter("barValue", "A good bar value", "string"),
            new AdHocToolElementParameter("firstOne", "The first value"),
            new AdHocToolElementParameter("secondOne", "The second value", "string")),
        new FeelInputParamTestCase(
            "Multiple parameters, part of a list",
            """
            ["something", fromAi(toolCall.firstValue, "The first value", "string"), fromAi(toolCall.secondValue, "The second value", "integer")]
            """,
            new AdHocToolElementParameter("firstValue", "The first value", "string"),
            new AdHocToolElementParameter("secondValue", "The second value", "integer")),
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
            new AdHocToolElementParameter("firstValue", "The first value", "string"),
            new AdHocToolElementParameter("secondValue", "The second value", "integer"),
            new AdHocToolElementParameter("thirdValue", "The third value to add")),
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
            new AdHocToolElementParameter("firstValue", "The first value", "string"),
            new AdHocToolElementParameter("secondValue", "The second value", "integer"),
            new AdHocToolElementParameter("thirdValue", "The third value to add"),
            new AdHocToolElementParameter(
                "fourthValue",
                "The fourth value to add",
                "array",
                Map.of("items", Map.of("type", "string", "enum", List.of("foo", "bar", "baz"))))),
        new FeelInputParamTestCase(
            "Using camunda document reference data structure",
            """
            fromAi(toolCall.documents, "The documents to include", "array", {
              "items": {
                "type": "object",
                "properties": {
                  "storeId": {
                    "type": "string"
                  },
                  "documentId": {
                    "type": "string"
                  },
                  "camunda.document.type": {
                    "type": "string"
                  },
                  "contentHash": {
                    "type": "string"
                  }
                },
                "required": [
                  "storeId",
                  "documentId",
                  "camunda.document.type",
                  "contentHash"
                ]
              }
            })
            """,
            new AdHocToolElementParameter(
                "documents",
                "The documents to include",
                "array",
                Map.of(
                    "items",
                    Map.of(
                        "type",
                        "object",
                        "properties",
                        Map.of(
                            "storeId", Map.of("type", "string"),
                            "documentId", Map.of("type", "string"),
                            "camunda.document.type", Map.of("type", "string"),
                            "contentHash", Map.of("type", "string")),
                        "required",
                        List.of(
                            "storeId", "documentId", "camunda.document.type", "contentHash"))))));
  }

  record FeelInputParamTestCase(
      String description, String expression, List<AdHocToolElementParameter> expectedInputParams) {

    FeelInputParamTestCase(
        String description, String expression, AdHocToolElementParameter... expectedInputParams) {
      this(description, expression, List.of(expectedInputParams));
    }

    @Override
    public String toString() {
      return "%s: %s".formatted(description, expression);
    }
  }
}
