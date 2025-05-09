/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.jsonschema;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

class JsonSchemaElementModuleTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JsonSchemaElementModule());
  }

  @Test
  void shouldSerializeAndDeserializeSimpleObjectSchema() throws Exception {
    // Given
    String json =
        """
        {
          "type": "object",
          "properties": {
            "message": {
              "type": "string",
              "description": "Message to echo"
            }
          },
          "required": [
            "message"
          ],
          "additionalProperties": false
        }
        """;

    // When
    JsonSchemaElement element = objectMapper.readValue(json, JsonSchemaElement.class);
    String serialized = objectMapper.writeValueAsString(element);
    JsonSchemaElement deserialized = objectMapper.readValue(serialized, JsonSchemaElement.class);

    // Then
    assertThat(element).isInstanceOf(JsonObjectSchema.class);
    JsonObjectSchema schema = (JsonObjectSchema) element;
    assertThat(schema.properties()).containsKey("message");
    assertThat(schema.properties().get("message")).isInstanceOf(JsonStringSchema.class);
    assertThat(schema.required()).contains("message");
    assertThat(schema.additionalProperties()).isFalse();

    assertThat(deserialized).isInstanceOf(JsonObjectSchema.class);
    JsonObjectSchema deserializedSchema = (JsonObjectSchema) deserialized;
    assertThat(deserializedSchema.properties()).containsKey("message");
    assertThat(deserializedSchema.properties().get("message")).isInstanceOf(JsonStringSchema.class);
    assertThat(deserializedSchema.required()).contains("message");
    assertThat(deserializedSchema.additionalProperties()).isFalse();

    // Compare serialized JSON with original input
    JSONAssert.assertEquals(json, serialized, true);
  }

  @Test
  void shouldSerializeAndDeserializeComplexObjectSchema() throws Exception {
    // Given
    String json =
        """
        {
          "type": "object",
          "properties": {
            "a": {
              "type": "number",
              "description": "First number"
            },
            "b": {
              "type": "number",
              "description": "Second number"
            }
          },
          "required": [
            "a",
            "b"
          ],
          "additionalProperties": false
        }
        """;

    // When
    JsonSchemaElement element = objectMapper.readValue(json, JsonSchemaElement.class);
    String serialized = objectMapper.writeValueAsString(element);
    JsonSchemaElement deserialized = objectMapper.readValue(serialized, JsonSchemaElement.class);

    // Then
    assertThat(element).isInstanceOf(JsonObjectSchema.class);
    JsonObjectSchema schema = (JsonObjectSchema) element;
    assertThat(schema.properties()).containsKeys("a", "b");
    assertThat(schema.required()).contains("a", "b");
    assertThat(schema.additionalProperties()).isFalse();

    assertThat(deserialized).isInstanceOf(JsonObjectSchema.class);
    JsonObjectSchema deserializedSchema = (JsonObjectSchema) deserialized;
    assertThat(deserializedSchema.properties()).containsKeys("a", "b");
    assertThat(deserializedSchema.required()).contains("a", "b");
    assertThat(deserializedSchema.additionalProperties()).isFalse();

    // Compare serialized JSON with original input
    JSONAssert.assertEquals(json, serialized, true);
  }

  @Test
  void shouldSerializeAndDeserializeSchemaWithEnum() throws Exception {
    // Given
    String json =
        """
        {
          "type": "object",
          "properties": {
            "messageType": {
              "type": "string",
              "enum": [
                "error",
                "success",
                "debug"
              ],
              "description": "Type of message to demonstrate different annotation patterns"
            },
            "includeImage": {
              "type": "boolean",
              "default": false,
              "description": "Whether to include an example image"
            }
          },
          "required": [
            "messageType"
          ],
          "additionalProperties": false
        }
        """;

    // When
    JsonSchemaElement element = objectMapper.readValue(json, JsonSchemaElement.class);
    String serialized = objectMapper.writeValueAsString(element);
    JsonSchemaElement deserialized = objectMapper.readValue(serialized, JsonSchemaElement.class);

    // Then
    assertThat(element).isInstanceOf(JsonObjectSchema.class);
    JsonObjectSchema schema = (JsonObjectSchema) element;
    assertThat(schema.properties()).containsKeys("messageType", "includeImage");
    assertThat(schema.properties().get("messageType")).isInstanceOf(JsonEnumSchema.class);
    assertThat(schema.properties().get("includeImage")).isInstanceOf(JsonBooleanSchema.class);
    assertThat(schema.required()).contains("messageType");
    assertThat(schema.additionalProperties()).isFalse();

    JsonEnumSchema enumSchema = (JsonEnumSchema) schema.properties().get("messageType");
    assertThat(enumSchema.enumValues()).contains("error", "success", "debug");

    assertThat(deserialized).isInstanceOf(JsonObjectSchema.class);
    JsonObjectSchema deserializedSchema = (JsonObjectSchema) deserialized;
    assertThat(deserializedSchema.properties()).containsKeys("messageType", "includeImage");
    assertThat(deserializedSchema.properties().get("messageType"))
        .isInstanceOf(JsonEnumSchema.class);
    assertThat(deserializedSchema.properties().get("includeImage"))
        .isInstanceOf(JsonBooleanSchema.class);
    assertThat(deserializedSchema.required()).contains("messageType");
    assertThat(deserializedSchema.additionalProperties()).isFalse();

    JsonEnumSchema deserializedEnumSchema =
        (JsonEnumSchema) deserializedSchema.properties().get("messageType");
    assertThat(deserializedEnumSchema.enumValues()).contains("error", "success", "debug");

    // Expected JSON without default field (serializer doesn't include default values)
    String expectedJson =
        """
        {
          "type": "object",
          "properties": {
            "messageType": {
              "type": "string",
              "enum": [
                "error",
                "success",
                "debug"
              ],
              "description": "Type of message to demonstrate different annotation patterns"
            },
            "includeImage": {
              "type": "boolean",
              "description": "Whether to include an example image"
            }
          },
          "required": [
            "messageType"
          ],
          "additionalProperties": false
        }
        """;

    // Compare serialized JSON with expected JSON (without default field)
    JSONAssert.assertEquals(expectedJson, serialized, true);
  }

  @Test
  void shouldHandleEnumWithoutType() throws Exception {
    // Given
    String json =
        """
        {
          "enum": [
            "error",
            "success",
            "debug"
          ],
          "description": "Type of message"
        }
        """;

    // When
    JsonSchemaElement element = objectMapper.readValue(json, JsonSchemaElement.class);
    String serialized = objectMapper.writeValueAsString(element);
    JsonSchemaElement deserialized = objectMapper.readValue(serialized, JsonSchemaElement.class);

    // Then
    assertThat(element).isInstanceOf(JsonEnumSchema.class);
    JsonEnumSchema enumSchema = (JsonEnumSchema) element;
    assertThat(enumSchema.enumValues()).contains("error", "success", "debug");
    assertThat(enumSchema.description()).isEqualTo("Type of message");

    assertThat(deserialized).isInstanceOf(JsonEnumSchema.class);
    JsonEnumSchema deserializedEnumSchema = (JsonEnumSchema) deserialized;
    assertThat(deserializedEnumSchema.enumValues()).contains("error", "success", "debug");
    assertThat(deserializedEnumSchema.description()).isEqualTo("Type of message");

    // Expected JSON with type field (serializer adds type=string for enums)
    String expectedJson =
        """
        {
          "type": "string",
          "enum": [
            "error",
            "success",
            "debug"
          ],
          "description": "Type of message"
        }
        """;

    // Compare serialized JSON with expected JSON (with type field)
    JSONAssert.assertEquals(expectedJson, serialized, true);
  }

  @Test
  void shouldHandleArraySchema() throws Exception {
    // Given
    String json =
        """
        {
          "type": "array",
          "items": {
            "type": "string",
            "description": "Item description"
          },
          "description": "Array description"
        }
        """;

    // When
    JsonSchemaElement element = objectMapper.readValue(json, JsonSchemaElement.class);
    String serialized = objectMapper.writeValueAsString(element);
    JsonSchemaElement deserialized = objectMapper.readValue(serialized, JsonSchemaElement.class);

    // Then
    assertThat(element).isInstanceOf(JsonArraySchema.class);
    JsonArraySchema arraySchema = (JsonArraySchema) element;
    assertThat(arraySchema.items()).isInstanceOf(JsonStringSchema.class);
    assertThat(arraySchema.description()).isEqualTo("Array description");

    assertThat(deserialized).isInstanceOf(JsonArraySchema.class);
    JsonArraySchema deserializedArraySchema = (JsonArraySchema) deserialized;
    assertThat(deserializedArraySchema.items()).isInstanceOf(JsonStringSchema.class);
    assertThat(deserializedArraySchema.description()).isEqualTo("Array description");

    // Compare serialized JSON with original input
    JSONAssert.assertEquals(json, serialized, true);
  }

  @Test
  void shouldIgnoreUnknownProperties() throws Exception {
    // Given
    String json =
        """
        {
          "type": "object",
          "properties": {
            "message": {
              "type": "string",
              "description": "Message to echo",
              "unknownProperty": "value"
            }
          },
          "required": [
            "message"
          ],
          "additionalProperties": false,
          "anotherUnknownProperty": 123
        }
        """;

    // When
    JsonSchemaElement element = objectMapper.readValue(json, JsonSchemaElement.class);
    String serialized = objectMapper.writeValueAsString(element);

    // Then
    assertThat(element).isInstanceOf(JsonObjectSchema.class);
    JsonObjectSchema schema = (JsonObjectSchema) element;
    assertThat(schema.properties()).containsKey("message");
    assertThat(schema.properties().get("message")).isInstanceOf(JsonStringSchema.class);
    assertThat(schema.required()).contains("message");
    assertThat(schema.additionalProperties()).isFalse();

    // Expected JSON without unknown properties
    String expectedJson =
        """
        {
          "type": "object",
          "properties": {
            "message": {
              "type": "string",
              "description": "Message to echo"
            }
          },
          "required": [
            "message"
          ],
          "additionalProperties": false
        }
        """;

    // Compare serialized JSON with expected JSON (without unknown properties)
    JSONAssert.assertEquals(expectedJson, serialized, true);
  }
}
