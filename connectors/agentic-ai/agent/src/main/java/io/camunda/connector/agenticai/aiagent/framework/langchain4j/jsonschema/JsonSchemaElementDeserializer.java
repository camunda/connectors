/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema;

import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_ADDITIONAL_PROPERTIES;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_ANYOF;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_DEFINITIONS;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_ENUM;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_ITEMS;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_PROPERTIES;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_REF;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_REQUIRED;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_ARRAY;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_BOOLEAN;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_INTEGER;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_NULL;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_NUMBER;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_OBJECT;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_STRING;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonSchemaElementDeserializer extends JsonDeserializer<JsonSchemaElement> {

  @Override
  public JsonSchemaElement deserialize(JsonParser jp, DeserializationContext ctxt)
      throws IOException {
    ObjectMapper objectMapper = (ObjectMapper) jp.getCodec();
    JsonNode node = objectMapper.readTree(jp);

    if (node.has(PROPERTY_ENUM)) {
      return createEnumSchema(node);
    }

    if (node.has(PROPERTY_ANYOF)) {
      return createAnyOfSchema(node, objectMapper);
    }

    if (node.has(PROPERTY_REF)) {
      return createReferenceSchema(node);
    }

    String nodeType = node.has(PROPERTY_TYPE) ? node.get(PROPERTY_TYPE).asText() : null;

    return switch (nodeType) {
      case TYPE_OBJECT -> createObjectSchema(node, objectMapper);
      case TYPE_STRING -> createStringSchema(node);
      case TYPE_NUMBER -> createNumberSchema(node);
      case TYPE_INTEGER -> createIntegerSchema(node);
      case TYPE_BOOLEAN -> createBooleanSchema(node);
      case TYPE_ARRAY -> createArraySchema(node, objectMapper);
      case TYPE_NULL -> new JsonNullSchema();
      case null, default ->
          throw new IllegalArgumentException(
              "Unknown JSON schema element type '%s'".formatted(nodeType));
    };
  }

  private JsonObjectSchema createObjectSchema(JsonNode node, ObjectMapper objectMapper)
      throws JsonProcessingException {
    JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
    if (node.has(PROPERTY_DESCRIPTION)) {
      builder.description(node.get(PROPERTY_DESCRIPTION).asText());
    }

    if (node.has(PROPERTY_PROPERTIES)) {
      ObjectNode propertiesNode = (ObjectNode) node.get(PROPERTY_PROPERTIES);
      for (Map.Entry<String, JsonNode> property : propertiesNode.properties()) {
        builder.addProperty(
            property.getKey(),
            objectMapper.treeToValue(property.getValue(), JsonSchemaElement.class));
      }
    }

    if (node.has(PROPERTY_DEFINITIONS)) {
      ObjectNode definitionsNode = (ObjectNode) node.get(PROPERTY_DEFINITIONS);
      Map<String, JsonSchemaElement> definitions = new LinkedHashMap<>();
      for (Map.Entry<String, JsonNode> definition : definitionsNode.properties()) {
        definitions.put(
            definition.getKey(),
            objectMapper.treeToValue(definition.getValue(), JsonSchemaElement.class));
      }

      builder.definitions(definitions);
    }

    if (node.has(PROPERTY_REQUIRED)) {
      builder.required(toStringArray((ArrayNode) node.get(PROPERTY_REQUIRED)));
    }

    if (node.has(PROPERTY_ADDITIONAL_PROPERTIES)) {
      builder.additionalProperties(node.get(PROPERTY_ADDITIONAL_PROPERTIES).asBoolean(false));
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

  private JsonReferenceSchema createReferenceSchema(JsonNode node) {
    return JsonReferenceSchema.builder().reference(node.get(PROPERTY_REF).asText()).build();
  }

  private JsonArraySchema createArraySchema(JsonNode node, ObjectMapper objectMapper)
      throws JsonProcessingException {
    JsonArraySchema.Builder builder = JsonArraySchema.builder();
    if (node.has(PROPERTY_DESCRIPTION)) {
      builder.description(node.get(PROPERTY_DESCRIPTION).asText());
    }

    if (node.has(PROPERTY_ITEMS)) {
      builder.items(objectMapper.treeToValue(node.get(PROPERTY_ITEMS), JsonSchemaElement.class));
    }

    return builder.build();
  }

  private JsonAnyOfSchema createAnyOfSchema(JsonNode node, ObjectMapper objectMapper)
      throws JsonProcessingException {
    JsonAnyOfSchema.Builder builder = JsonAnyOfSchema.builder();
    if (node.has(PROPERTY_DESCRIPTION)) {
      builder.description(node.get(PROPERTY_DESCRIPTION).asText());
    }

    if (node.has(PROPERTY_ANYOF)) {
      ArrayNode itemsArray = (ArrayNode) node.get(PROPERTY_ANYOF);

      List<JsonSchemaElement> items = new ArrayList<>();
      for (JsonNode item : itemsArray) {
        items.add(objectMapper.treeToValue(item, JsonSchemaElement.class));
      }

      builder.anyOf(items);
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
