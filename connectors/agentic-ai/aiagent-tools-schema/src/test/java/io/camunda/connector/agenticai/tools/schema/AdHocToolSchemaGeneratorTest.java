/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.tools.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.tool.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.model.tool.AdHocToolElementParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
                    "toolCall.param1",
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
                    "toolCall.param1",
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
        createToolElement(
            List.of(new AdHocToolElementParameter("toolCall.param1", "A nice description")));

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
                    "toolCall.param1",
                    "A nice description",
                    null,
                    Map.of("enum", List.of("A", "B", "C")))));

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
                new AdHocToolElementParameter("toolCall.param1", "The first param"),
                new AdHocToolElementParameter("toolCall.param2", "The second param", "number"),
                new AdHocToolElementParameter(
                    "toolCall.param3",
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
        createToolElement(List.of(new AdHocToolElementParameter("toolCall._meta", "string")));

    assertThatThrownBy(() -> generator.generateToolSchema(element))
        .isInstanceOf(AdHocToolSchemaGenerationException.class)
        .hasMessage(
            "Failed to generate ad-hoc tool schema for element 'Test_Tool'. Parameter name 'toolCall._meta' is restricted and cannot be used.");
  }

  @Test
  void throwsExceptionWhenDuplicateParamNameIsUsed() {
    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter("toolCall.param1", "string"),
                new AdHocToolElementParameter("toolCall.param1", "string")));

    assertThatThrownBy(() -> generator.generateToolSchema(element))
        .isInstanceOf(AdHocToolSchemaGenerationException.class)
        .hasMessage(
            "Failed to generate ad-hoc tool schema for element 'Test_Tool'. Duplicate parameter name 'toolCall.param1'.");
  }

  static Stream<Map<String, Object>> schemasWithTheDocumentTypeWhereNoParameterCanSit() {
    final var document = Map.<String, Object>of("type", "document");
    final var string = Map.<String, Object>of("type", "string");
    final var itemsNextToProperties = new LinkedHashMap<String, Object>();
    itemsNextToProperties.put("type", "object");
    itemsNextToProperties.put("properties", Map.of("file", document));
    itemsNextToProperties.put("items", document);
    return Stream.of(
        Map.of("anyOf", List.of(string, document)),
        Map.of("oneOf", List.of(Map.of("type", "number"), document)),
        Map.of("anyOf", List.of(document)),
        Map.of(
            "anyOf", List.of(Map.of("type", "number"), Map.of("oneOf", List.of(string, document)))),
        Map.of("anyOf", List.of(string, object("file", document))),
        Map.of("type", "array", "items", Map.of("anyOf", List.of(string, document))),
        object("file", Map.of("anyOf", List.of(document, string))),
        Map.of("type", List.of("string", "document")),
        Map.of("type", List.of("document")),
        Map.of("type", List.of("string", "document"), "anyOf", List.of(string)),
        Map.of("type", "array", "items", Map.of("type", List.of("string", "document"))),
        Map.of("allOf", List.of(document)),
        Map.of("allOf", List.of(Map.of("type", List.of("string", "document")))),
        Map.of("not", document),
        Map.of("type", "object", "additionalProperties", document),
        Map.of("type", "array", "prefixItems", List.of(document)),
        Map.of("$defs", Map.of("file", document)),
        itemsNextToProperties,
        object("default", Map.of("anyOf", List.of(string, document))),
        object("enum", Map.of("type", List.of("string", "document"))),
        Map.of("$defs", Map.of("examples", document)),
        Map.of("type", "object", "dependencies", Map.of("type", document)));
  }

  @ParameterizedTest
  @MethodSource("schemasWithTheDocumentTypeWhereNoParameterCanSit")
  void throwsExceptionWhenTheDocumentTypeIsUsedWhereNoDocumentParameterCanSit(
      Map<String, Object> schema) {
    final var element =
        createToolElement(
            List.of(new AdHocToolElementParameter("toolCall.body", "The body", null, schema)));

    assertThatThrownBy(() -> generator.generateToolSchema(element))
        .isInstanceOf(AdHocToolSchemaGenerationException.class)
        .hasMessage(
            "Failed to generate ad-hoc tool schema for element 'Test_Tool'. Parameter 'toolCall.body': The type 'document' can only be the type of a parameter, of the items of an array or of an object property. Use a separate parameter for a document.");
  }

  @Test
  void throwsExceptionWhenParameterNameDoesNotStartWithExpectedNamespace() {
    final var element =
        createToolElement(List.of(new AdHocToolElementParameter("param1", "string")));

    assertThatThrownBy(() -> generator.generateToolSchema(element))
        .isInstanceOf(AdHocToolSchemaGenerationException.class)
        .hasMessage(
            "Failed to generate ad-hoc tool schema for element 'Test_Tool'. Parameter name 'param1' is not part of expected namespace 'toolCall.'.");
  }

  @Test
  void throwsExceptionWhenParameterNameIsEmptyAfterRemovingNamespace() {
    final var element =
        createToolElement(List.of(new AdHocToolElementParameter("toolCall.", "string")));

    assertThatThrownBy(() -> generator.generateToolSchema(element))
        .isInstanceOf(AdHocToolSchemaGenerationException.class)
        .hasMessage(
            "Failed to generate ad-hoc tool schema for element 'Test_Tool'. Parameter name 'toolCall.' is empty after removing the expected namespace 'toolCall.'.");
  }

  @Test
  void throwsExceptionWhenParameterNameContainsDotsAfterRemovingNamespace() {
    final var element =
        createToolElement(
            List.of(new AdHocToolElementParameter("toolCall.param.subparam", "string")));

    assertThatThrownBy(() -> generator.generateToolSchema(element))
        .isInstanceOf(AdHocToolSchemaGenerationException.class)
        .hasMessage(
            "Failed to generate ad-hoc tool schema for element 'Test_Tool'. Parameter name 'param.subparam' with removed namespace 'toolCall.' is not a leaf reference (must not contain dots).");
  }

  @Test
  void excludesParameterFromRequiredListWhenOptionsRequiredIsFalse() throws Exception {
    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter(
                    "toolCall.param1", "Required param", "string", null, null),
                new AdHocToolElementParameter(
                    "toolCall.param2",
                    "Optional param",
                    "string",
                    null,
                    Map.of("required", false))));

    assertJsonSchema(
        element,
        """
        {
          "type": "object",
          "properties": {
            "param1": {
              "type": "string",
              "description": "Required param"
            },
            "param2": {
              "type": "string",
              "description": "Optional param"
            }
          },
          "required": ["param1"]
        }
        """);
  }

  @Test
  void includesParameterInRequiredListWhenOptionsRequiredIsTrue() throws Exception {
    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter(
                    "toolCall.param1",
                    "Required param",
                    "string",
                    null,
                    Map.of("required", true))));

    assertJsonSchema(
        element,
        """
        {
          "type": "object",
          "properties": {
            "param1": {
              "type": "string",
              "description": "Required param"
            }
          },
          "required": ["param1"]
        }
        """);
  }

  @Test
  void throwsExceptionWhenOptionsRequiredIsStringValue() {
    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter(
                    "toolCall.param1",
                    "Required param",
                    "string",
                    null,
                    Map.of("required", "true"))));

    assertThatThrownBy(() -> generator.generateToolSchema(element))
        .isInstanceOf(AdHocToolSchemaGenerationException.class)
        .hasMessage(
            "Failed to generate ad-hoc tool schema for element 'Test_Tool'. Parameter 'toolCall.param1' 'required' option must be a boolean value, but was: String");
  }

  @Test
  void throwsExceptionWhenOptionsRequiredIsIntegerValue() {
    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter(
                    "toolCall.param1", "Required param", "string", null, Map.of("required", 1))));

    assertThatThrownBy(() -> generator.generateToolSchema(element))
        .isInstanceOf(AdHocToolSchemaGenerationException.class)
        .hasMessage(
            "Failed to generate ad-hoc tool schema for element 'Test_Tool'. Parameter 'toolCall.param1' 'required' option must be a boolean value, but was: Integer");
  }

  @Test
  void treatsParameterAsRequiredWhenOptionsRequiredIsExplicitlyNull() throws Exception {
    Map<String, Object> options = new HashMap<>();
    options.put("required", null);

    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter(
                    "toolCall.param1", "Required param", "string", null, options)));

    assertJsonSchema(
        element,
        """
        {
          "type": "object",
          "properties": {
            "param1": {
              "type": "string",
              "description": "Required param"
            }
          },
          "required": ["param1"]
        }
        """);
  }

  @Test
  void handlesRequiredAndOptionalParametersTogether() throws Exception {
    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter(
                    "toolCall.requiredParam",
                    "A required parameter",
                    "string",
                    null,
                    Map.of("required", true)),
                new AdHocToolElementParameter(
                    "toolCall.optionalParam",
                    "An optional parameter",
                    "number",
                    null,
                    Map.of("required", false)),
                new AdHocToolElementParameter(
                    "toolCall.defaultRequiredParam",
                    "A parameter with default required behavior",
                    "string",
                    null,
                    null)));

    assertJsonSchema(
        element,
        """
        {
          "type": "object",
          "properties": {
            "requiredParam": {
              "type": "string",
              "description": "A required parameter"
            },
            "optionalParam": {
              "type": "number",
              "description": "An optional parameter"
            },
            "defaultRequiredParam": {
              "type": "string",
              "description": "A parameter with default required behavior"
            }
          },
          "required": ["requiredParam", "defaultRequiredParam"]
        }
        """);
  }

  @Test
  void mapsDocumentTypeToTheDocumentFormat() throws Exception {
    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter("toolCall.doc", "The invoice", "document"),
                new AdHocToolElementParameter("toolCall.note", "A note", "string")));

    assertJsonSchema(
        element,
        """
        {
          "type": "object",
          "properties": {
            "doc": {
              "type": "object",
              "description": "The invoice",
              "properties": {
                "type": {"type": "string", "enum": ["camunda.agenticai.document"]},
                "id": {"type": "string", "description": "The id of the document."}
              },
              "required": ["type", "id"]
            },
            "note": {
              "type": "string",
              "description": "A note"
            }
          },
          "required": ["doc", "note"]
        }
        """);
  }

  @Test
  void mapsTheDocumentTypeOfItemsAndNestedPropertiesToTheDocumentFormat() throws Exception {
    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter(
                    "toolCall.docs",
                    "The files",
                    null,
                    Map.of("type", "array", "items", Map.of("type", "document"))),
                new AdHocToolElementParameter(
                    "toolCall.message",
                    "The message",
                    null,
                    Map.of(
                        "type",
                        "object",
                        "properties",
                        Map.of("file", Map.of("type", "document"))))));

    assertJsonSchema(
        element,
        """
        {
          "type": "object",
          "properties": {
            "docs": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "type": {"type": "string", "enum": ["camunda.agenticai.document"]},
                  "id": {"type": "string", "description": "The id of the document."}
                },
                "required": ["type", "id"]
              },
              "description": "The files"
            },
            "message": {
              "type": "object",
              "properties": {
                "file": {
                  "type": "object",
                  "properties": {
                    "type": {"type": "string", "enum": ["camunda.agenticai.document"]},
                    "id": {"type": "string", "description": "The id of the document."}
                  },
                  "required": ["type", "id"]
                }
              },
              "description": "The message"
            }
          },
          "required": ["docs", "message"]
        }
        """);
  }

  @Test
  void mapsTheDocumentTypeAtAnyDepthToTheDocumentFormat() {
    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter(
                    "toolCall.rows",
                    null,
                    null,
                    Map.of(
                        "type",
                        "array",
                        "items",
                        object(
                            "file", Map.of("type", "document"), "note", Map.of("type", "string")))),
                new AdHocToolElementParameter(
                    "toolCall.nested",
                    null,
                    null,
                    object("inner", object("file", Map.of("type", "document"))))));

    assertThat(generator.generateToolSchema(element).get("properties"))
        .isEqualTo(
            Map.of(
                "rows",
                Map.of(
                    "type",
                    "array",
                    "items",
                    object("file", document(), "note", Map.of("type", "string"))),
                "nested",
                object("inner", object("file", document()))));
  }

  @Test
  void mapsTheDocumentTypeOfAPropertyNamedLikeADataKeyword() {
    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter(
                    "toolCall.request",
                    null,
                    null,
                    object("default", Map.of("type", "document")))));

    assertThat(generator.generateToolSchema(element).get("properties"))
        .isEqualTo(Map.of("request", object("default", document())));
  }

  @Test
  void leavesPropertyNamesInDependenciesAsTheyAre() {
    final var dependentRequired = new LinkedHashMap<String, Object>();
    dependentRequired.put("type", "object");
    dependentRequired.put("dependentRequired", Map.of("type", List.of("document")));
    final var dependencies = new LinkedHashMap<String, Object>();
    dependencies.put("type", "object");
    dependencies.put("dependencies", Map.of("type", List.of("document")));
    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter("toolCall.first", null, null, dependentRequired),
                new AdHocToolElementParameter("toolCall.second", null, null, dependencies)));

    final var schema = generator.generateToolSchema(element);

    assertThat(propertyOf(schema, "first")).isEqualTo(dependentRequired);
    assertThat(propertyOf(schema, "second")).isEqualTo(dependencies);
  }

  @Test
  void keepsTheOtherAuthoredEntriesOfTheDocumentTypeAfterTheDocumentFormat() {
    final var authored = new LinkedHashMap<String, Object>();
    authored.put("title", "Doc");
    authored.put("type", "document");
    authored.put("description", "A document");
    final var element =
        createToolElement(
            List.of(new AdHocToolElementParameter("toolCall.doc", null, null, authored)));

    final var doc = propertyOf(generator.generateToolSchema(element), "doc");

    final var expected = new LinkedHashMap<>(DocumentParameter.documentSchema("A document"));
    expected.put("title", "Doc");
    assertThat(doc).isEqualTo(expected);
    assertThat(List.copyOf(doc.keySet()))
        .containsExactly("type", "description", "properties", "required", "title");
  }

  @Test
  void leavesATypeArrayWithoutTheDocumentTypeAsItIs() throws Exception {
    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter(
                    "toolCall.value", null, null, Map.of("type", List.of("string", "null")))));

    assertJsonSchema(
        element,
        """
        {
          "type": "object",
          "properties": {
            "value": {"type": ["string", "null"]}
          },
          "required": ["value"]
        }
        """);
  }

  @Test
  void leavesTheDocumentTypeInDataKeywordsAsItIs() throws Exception {
    final var document = Map.<String, Object>of("type", "document");
    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter(
                    "toolCall.kind",
                    null,
                    null,
                    Map.of("type", "string", "enum", List.of("a", document))),
                new AdHocToolElementParameter(
                    "toolCall.payload", null, null, Map.of("type", "object", "default", document)),
                new AdHocToolElementParameter(
                    "toolCall.fixed", null, null, Map.of("type", "object", "const", document)),
                new AdHocToolElementParameter(
                    "toolCall.sample",
                    null,
                    null,
                    Map.of("type", "object", "examples", List.of(document)))));

    assertJsonSchema(
        element,
        """
        {
          "type": "object",
          "properties": {
            "kind": {"type": "string", "enum": ["a", {"type": "document"}]},
            "payload": {"type": "object", "default": {"type": "document"}},
            "fixed": {"type": "object", "const": {"type": "document"}},
            "sample": {"type": "object", "examples": [{"type": "document"}]}
          },
          "required": ["kind", "payload", "fixed", "sample"]
        }
        """);
  }

  @Test
  void defaultsTheTypeOfASchemaWithAlternativesToString() throws Exception {
    final var element =
        createToolElement(
            List.of(
                new AdHocToolElementParameter(
                    "toolCall.value",
                    null,
                    null,
                    Map.of("anyOf", List.of(Map.of("type", "number"), Map.of("type", "string"))))));

    assertJsonSchema(
        element,
        """
        {
          "type": "object",
          "properties": {
            "value": {
              "anyOf": [
                {"type": "number"},
                {"type": "string"}
              ],
              "type": "string"
            }
          },
          "required": ["value"]
        }
        """);
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

  private static Map<String, Object> document() {
    return DocumentParameter.documentSchema(null);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> propertyOf(Map<String, Object> schema, String property) {
    return (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get(property);
  }

  /** An object schema from alternating property names and schemas, in declaration order. */
  private static Map<String, Object> object(Object... namesAndSchemas) {
    final var properties = new LinkedHashMap<String, Object>();
    for (int i = 0; i + 1 < namesAndSchemas.length; i += 2) {
      properties.put((String) namesAndSchemas[i], namesAndSchemas[i + 1]);
    }
    final var schema = new LinkedHashMap<String, Object>();
    schema.put("type", "object");
    schema.put("properties", properties);
    return schema;
  }
}
