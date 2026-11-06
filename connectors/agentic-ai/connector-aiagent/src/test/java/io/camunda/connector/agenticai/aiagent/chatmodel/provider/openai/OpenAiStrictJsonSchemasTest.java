/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.api.tool.DocumentSchemas;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

class OpenAiStrictJsonSchemasTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final String HANDLE =
      """
      {
        "type": "object",
        "additionalProperties": false,
        "properties": {
          "type": {"type": "string", "enum": ["camunda.agenticai.document"]},
          "id": {"type": "string", "description": "The id of the document."}
        },
        "required": ["type", "id"]
      }
      """;

  @Test
  void keepsTheHandleSchemaOfADocumentParameterAndClosesItsObject() throws Exception {
    final var properties = new LinkedHashMap<String, Object>();
    properties.put("file", DocumentSchemas.document("The file"));
    final Map<String, Object> schema =
        Map.of("type", "object", "properties", properties, "required", List.of("file"));

    final var strict = OpenAiStrictJsonSchemas.forStrictMode(schema, objectMapper);

    JSONAssert.assertEquals(
        """
        {
          "type": "object",
          "additionalProperties": false,
          "properties": {
            "file": {
              "type": "object",
              "description": "The file",
              "additionalProperties": false,
              "properties": {
                "type": {"type": "string", "enum": ["camunda.agenticai.document"]},
                "id": {"type": "string", "description": "The id of the document."}
              },
              "required": ["type", "id"]
            }
          },
          "required": ["file"]
        }
        """,
        strict.toString(),
        true);
  }

  @Test
  void closesTheHandleObjectInsideTheAnyOfOfAUnionParameter() throws Exception {
    final var properties = new LinkedHashMap<String, Object>();
    properties.put("body", TestDocuments.textOrDocument("The body"));
    final Map<String, Object> schema =
        Map.of("type", "object", "properties", properties, "required", List.of("body"));

    final var strict = OpenAiStrictJsonSchemas.forStrictMode(schema, objectMapper);

    JSONAssert.assertEquals(
        """
        {
          "type": "object",
          "additionalProperties": false,
          "properties": {
            "body": {
              "description": "The body",
              "anyOf": [{"type": "string"}, %s]
            }
          },
          "required": ["body"]
        }
        """
            .formatted(HANDLE),
        strict.toString(),
        true);
  }

  @Test
  void closesTheHandleObjectInsideAnObjectAlternativeOfAUnionParameter() throws Exception {
    final var alternative = new LinkedHashMap<String, Object>();
    alternative.put("type", "object");
    alternative.put(
        "properties",
        new LinkedHashMap<>(
            Map.of("file", DocumentSchemas.document(null), "caption", Map.of("type", "string"))));
    final Map<String, Object> schema =
        Map.of(
            "type",
            "object",
            "properties",
            Map.of("message", Map.of("anyOf", List.of(Map.of("type", "string"), alternative))));

    final var strict = OpenAiStrictJsonSchemas.forStrictMode(schema, objectMapper);

    final var objectAlternative = strict.at("/properties/message/anyOf/1");
    assertThat(objectAlternative.path("additionalProperties").asBoolean(true)).isFalse();
    final var file = objectAlternative.at("/properties/file");
    assertThat(file.path("additionalProperties").asBoolean(true)).isFalse();
    assertThat(file.path("required").toString()).isEqualTo("[\"type\",\"id\"]");
    assertThat(file.at("/properties/type/enum").toString())
        .isEqualTo("[\"camunda.agenticai.document\"]");
  }

  @Test
  void closesTheHandleObjectOfADocumentParameterInArrayItems() throws Exception {
    final Map<String, Object> schema =
        Map.of(
            "type",
            "object",
            "properties",
            Map.of("files", Map.of("type", "array", "items", DocumentSchemas.document(null))));

    final var strict = OpenAiStrictJsonSchemas.forStrictMode(schema, objectMapper);

    assertThat(strict.at("/properties/files/items/additionalProperties").asBoolean(true)).isFalse();
    assertThat(strict.at("/properties/files/items/required").toString())
        .isEqualTo("[\"type\",\"id\"]");
  }
}
