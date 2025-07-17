/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.schema;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElementParameter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

class AdHocToolSchemaGeneratorTest {

  private static final String TOOL_ELEMENT_ID = "Test_Tool";

  private final AdHocToolSchemaGeneratorImpl generator = new AdHocToolSchemaGeneratorImpl();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void usesProvidedSchemaProperty() throws Exception {
    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter(
                    "param1",
                    null,
                    null,
                    Map.of("description", "A nice description", "type", "string"))));

    assertJsonSchema(
        element,
        """
        {
          "type": "object",
          "properties": {
            "param1": {
              "type": "string",
              "description": "A nice description"
            }
          },
          "required": ["param1"]
        }
        """);
  }

  @Test
  void overwritesTypeAndDescriptionFromDedicatedParams() throws Exception {
    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter(
                    "param1",
                    "Overridden description",
                    "number",
                    Map.of("description", "A nice description", "type", "string"))));

    assertJsonSchema(
        element,
        """
        {
          "type": "object",
          "properties": {
            "param1": {
              "type": "number",
              "description": "Overridden description"
            }
          },
          "required": ["param1"]
        }
        """);
  }

  @Test
  void defaultsToStringType() throws Exception {
    final var element =
        createToolElement(List.of(new AdHocToolElementParameter("param1", "A nice description")));

    assertJsonSchema(
        element,
        """
        {
          "type": "object",
          "properties": {
            "param1": {
              "type": "string",
              "description": "A nice description"
            }
          },
          "required": ["param1"]
        }
        """);
  }

  @Test
  void defaultsToStringTypeWithSchemaObject() throws Exception {
    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter(
                    "param1", "A nice description", null, Map.of("enum", List.of("A", "B", "C")))));

    assertJsonSchema(
        element,
        """
        {
          "type": "object",
          "properties": {
            "param1": {
              "type": "string",
              "description": "A nice description",
              "enum": ["A", "B", "C"]
            }
          },
          "required": ["param1"]
        }
        """);
  }

  @Test
  void supportsMultipleProperties() throws Exception {
    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter("param1", "The first param"),
                new AdHocToolElementParameter("param2", "The second param", "number"),
                new AdHocToolElementParameter(
                    "param3",
                    "The third param",
                    "string",
                    Map.of("enum", List.of("A", "B", "C")))));

    assertJsonSchema(
        element,
        """
        {
          "type": "object",
          "properties": {
            "param1": {
              "type": "string",
              "description": "The first param"
            },
            "param2": {
              "type": "number",
              "description": "The second param"
            },
            "param3": {
              "type": "string",
              "description": "The third param",
              "enum": ["A", "B", "C"]
            }
          },
          "required": ["param1", "param2", "param3"]
        }
        """);
  }

  @Test
  void throwsExceptionWhenRestrictedParamNameIsUsed() {
    final var element =
        createToolElement(List.of(new AdHocToolElementParameter("_meta", "string")));

    assertThatThrownBy(() -> generator.generateToolSchema(element))
        .isInstanceOf(AdHocToolSchemaGenerationException.class)
        .hasMessage(
            "Failed to generate ad-hoc tool schema for element 'Test_Tool'. Input parameter name '_meta' is restricted and cannot be used.");
  }

  @Test
  void throwsExceptionWhenDuplicateParamNameIsUsed() {
    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter("param1", "string"),
                new AdHocToolElementParameter("param1", "string")));

    assertThatThrownBy(() -> generator.generateToolSchema(element))
        .isInstanceOf(AdHocToolSchemaGenerationException.class)
        .hasMessage(
            "Failed to generate ad-hoc tool schema for element 'Test_Tool'. Duplicate input parameter name 'param1'.");
  }

  private void assertJsonSchema(AdHocToolElement element, String expectedJsonSchema)
      throws Exception {
    Map<String, Object> schema = generator.generateToolSchema(element);
    assertJsonSchema(schema, expectedJsonSchema);
  }

  private void assertJsonSchema(Map<String, Object> schema, String expectedJsonSchema)
      throws Exception {
    JSONAssert.assertEquals(expectedJsonSchema, objectMapper.writeValueAsString(schema), true);
  }

  private AdHocToolElement createToolElement(List<AdHocToolElementParameter> parameters) {
    return AdHocToolElement.builder().elementId(TOOL_ELEMENT_ID).parameters(parameters).build();
  }
}
