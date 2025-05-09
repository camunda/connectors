/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.jsonschema;

import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_ADDITIONAL_PROPERTIES;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_ENUM;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_ITEMS;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_PROPERTIES;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_REQUIRED;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_ARRAY;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_BOOLEAN;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_INTEGER;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_NUMBER;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_OBJECT;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_STRING;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/**
 * Custom deserializer for JsonSchemaElement and its subclasses. This deserializer handles the
 * polymorphism of JsonSchemaElement based on the "type" property, with special handling for the
 * "enum" case which could be type "string" or without "type" but with an "enum" property.
 */
public class JsonSchemaElementDeserializer extends JsonDeserializer<JsonSchemaElement> {

  @Override
  public JsonSchemaElement deserialize(JsonParser jp, DeserializationContext ctxt)
      throws IOException {
    ObjectMapper mapper = (ObjectMapper) jp.getCodec();
    JsonNode node = mapper.readTree(jp);

    return deserializeJsonNode(node, mapper);
  }

  private JsonSchemaElement deserializeJsonNode(JsonNode node, ObjectMapper mapper) {
    // Special case for enum without type
    if (node.has(PROPERTY_ENUM) && !node.has(PROPERTY_TYPE)) {
      return createEnumSchema(node);
    }

    // If no type is specified, default to object type
    String nodeType = node.has(PROPERTY_TYPE) ? node.get(PROPERTY_TYPE).asText() : TYPE_OBJECT;

    return switch (nodeType) {
      case TYPE_OBJECT -> createObjectSchema(node, mapper);
      case TYPE_STRING ->
          node.has(PROPERTY_ENUM) ? createEnumSchema(node) : createStringSchema(node);
      case TYPE_NUMBER -> createNumberSchema(node);
      case TYPE_INTEGER -> createIntegerSchema(node);
      case TYPE_BOOLEAN -> createBooleanSchema(node);
      case TYPE_ARRAY -> createArraySchema(node, mapper);
      default -> throw new IllegalArgumentException("Unknown element type: " + nodeType);
    };
  }

  private JsonObjectSchema createObjectSchema(JsonNode node, ObjectMapper mapper) {
    JsonObjectSchema.Builder builder = JsonObjectSchema.builder();

    if (node.has(PROPERTY_DESCRIPTION)) {
      builder.description(node.get(PROPERTY_DESCRIPTION).asText());
    }

    if (node.has(PROPERTY_REQUIRED)) {
      builder.required(toStringArray((ArrayNode) node.get(PROPERTY_REQUIRED)));
    }

    if (node.has(PROPERTY_ADDITIONAL_PROPERTIES)) {
      builder.additionalProperties(node.get(PROPERTY_ADDITIONAL_PROPERTIES).asBoolean(false));
    }

    if (node.has(PROPERTY_PROPERTIES)) {
      ObjectNode propertiesNode = (ObjectNode) node.get(PROPERTY_PROPERTIES);
      Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();

      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        builder.addProperty(field.getKey(), deserializeJsonNode(field.getValue(), mapper));
      }
    }

    return builder.build();
  }

  private JsonEnumSchema createEnumSchema(JsonNode node) {
    JsonEnumSchema.Builder builder = JsonEnumSchema.builder();

    if (node.has(PROPERTY_DESCRIPTION)) {
      builder.description(node.get(PROPERTY_DESCRIPTION).asText());
    }

    builder.enumValues(toStringArray((ArrayNode) node.get(PROPERTY_ENUM)));

    return builder.build();
  }

  private JsonStringSchema createStringSchema(JsonNode node) {
    JsonStringSchema.Builder builder = JsonStringSchema.builder();

    if (node.has(PROPERTY_DESCRIPTION)) {
      builder.description(node.get(PROPERTY_DESCRIPTION).asText());
    }

    return builder.build();
  }

  private JsonNumberSchema createNumberSchema(JsonNode node) {
    JsonNumberSchema.Builder builder = JsonNumberSchema.builder();

    if (node.has(PROPERTY_DESCRIPTION)) {
      builder.description(node.get(PROPERTY_DESCRIPTION).asText());
    }

    return builder.build();
  }

  private JsonIntegerSchema createIntegerSchema(JsonNode node) {
    JsonIntegerSchema.Builder builder = JsonIntegerSchema.builder();

    if (node.has(PROPERTY_DESCRIPTION)) {
      builder.description(node.get(PROPERTY_DESCRIPTION).asText());
    }

    return builder.build();
  }

  private JsonBooleanSchema createBooleanSchema(JsonNode node) {
    JsonBooleanSchema.Builder builder = JsonBooleanSchema.builder();

    if (node.has(PROPERTY_DESCRIPTION)) {
      builder.description(node.get(PROPERTY_DESCRIPTION).asText());
    }

    return builder.build();
  }

  private JsonArraySchema createArraySchema(JsonNode node, ObjectMapper mapper) {
    JsonArraySchema.Builder builder = JsonArraySchema.builder();

    if (node.has(PROPERTY_DESCRIPTION)) {
      builder.description(node.get(PROPERTY_DESCRIPTION).asText());
    }

    if (node.has(PROPERTY_ITEMS)) {
      builder.items(deserializeJsonNode(node.get(PROPERTY_ITEMS), mapper));
    }

    return builder.build();
  }

  private String[] toStringArray(ArrayNode jsonArray) {
    String[] result = new String[jsonArray.size()];

    for (int i = 0; i < jsonArray.size(); ++i) {
      result[i] = jsonArray.get(i).asText();
    }

    return result;
  }
}
