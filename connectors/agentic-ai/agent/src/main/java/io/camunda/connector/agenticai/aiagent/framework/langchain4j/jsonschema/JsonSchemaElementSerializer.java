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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
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
import java.util.List;
import java.util.Map;

public class JsonSchemaElementSerializer extends JsonSerializer<JsonSchemaElement> {

  @Override
  public void serialize(JsonSchemaElement value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    switch (value) {
      case JsonObjectSchema objectSchema -> serializeObjectSchema(objectSchema, gen);
      case JsonEnumSchema enumSchema -> serializeEnumSchema(enumSchema, gen);
      case JsonStringSchema stringSchema -> serializeStringSchema(stringSchema, gen);
      case JsonNumberSchema numberSchema -> serializeNumberSchema(numberSchema, gen);
      case JsonIntegerSchema integerSchema -> serializeIntegerSchema(integerSchema, gen);
      case JsonBooleanSchema booleanSchema -> serializeBooleanSchema(booleanSchema, gen);
      case JsonNullSchema ignored -> serializeNullSchema(gen);
      case JsonReferenceSchema referenceSchema -> serializeReferenceSchema(referenceSchema, gen);
      case JsonArraySchema arraySchema -> serializeArraySchema(arraySchema, gen);
      case JsonAnyOfSchema anyOfSchema -> serializeAnyOfSchema(anyOfSchema, gen);
      default ->
          throw new IllegalArgumentException(
              "Unsupported JSON schema type '%s'".formatted(value.getClass().getSimpleName()));
    }
  }

  private void serializeObjectSchema(JsonObjectSchema schema, JsonGenerator gen)
      throws IOException {
    gen.writeStartObject();

    gen.writeStringField(PROPERTY_TYPE, TYPE_OBJECT);

    if (schema.description() != null) {
      gen.writeStringField(PROPERTY_DESCRIPTION, schema.description());
    }

    serializeMapOfSchemaElements(PROPERTY_PROPERTIES, schema.properties(), gen);
    serializeMapOfSchemaElements(PROPERTY_DEFINITIONS, schema.definitions(), gen);

    List<String> required = schema.required();
    if (required != null && !required.isEmpty()) {
      gen.writeArrayFieldStart(PROPERTY_REQUIRED);
      for (String req : required) {
        gen.writeString(req);
      }
      gen.writeEndArray();
    }

    if (schema.additionalProperties() != null) {
      gen.writeBooleanField(PROPERTY_ADDITIONAL_PROPERTIES, schema.additionalProperties());
    }

    gen.writeEndObject();
  }

  private void serializeMapOfSchemaElements(
      String fieldName, Map<String, JsonSchemaElement> elements, JsonGenerator gen)
      throws IOException {
    if (elements != null && !elements.isEmpty()) {
      gen.writeObjectFieldStart(fieldName);
      for (Map.Entry<String, JsonSchemaElement> entry : elements.entrySet()) {
        gen.writeObjectField(entry.getKey(), entry.getValue());
      }
      gen.writeEndObject();
    }
  }

  private void serializeEnumSchema(JsonEnumSchema schema, JsonGenerator gen) throws IOException {
    gen.writeStartObject();

    if (schema.description() != null) {
      gen.writeStringField(PROPERTY_DESCRIPTION, schema.description());
    }

    gen.writeArrayFieldStart(PROPERTY_ENUM);
    for (String value : schema.enumValues()) {
      gen.writeString(value);
    }
    gen.writeEndArray();

    gen.writeEndObject();
  }

  private void serializeStringSchema(JsonStringSchema schema, JsonGenerator gen)
      throws IOException {
    gen.writeStartObject();

    gen.writeStringField(PROPERTY_TYPE, TYPE_STRING);

    if (schema.description() != null) {
      gen.writeStringField(PROPERTY_DESCRIPTION, schema.description());
    }

    gen.writeEndObject();
  }

  private void serializeNumberSchema(JsonNumberSchema schema, JsonGenerator gen)
      throws IOException {
    gen.writeStartObject();

    gen.writeStringField(PROPERTY_TYPE, TYPE_NUMBER);

    if (schema.description() != null) {
      gen.writeStringField(PROPERTY_DESCRIPTION, schema.description());
    }

    gen.writeEndObject();
  }

  private void serializeIntegerSchema(JsonIntegerSchema schema, JsonGenerator gen)
      throws IOException {
    gen.writeStartObject();

    gen.writeStringField(PROPERTY_TYPE, TYPE_INTEGER);

    if (schema.description() != null) {
      gen.writeStringField(PROPERTY_DESCRIPTION, schema.description());
    }

    gen.writeEndObject();
  }

  private void serializeBooleanSchema(JsonBooleanSchema schema, JsonGenerator gen)
      throws IOException {
    gen.writeStartObject();

    gen.writeStringField(PROPERTY_TYPE, TYPE_BOOLEAN);

    if (schema.description() != null) {
      gen.writeStringField(PROPERTY_DESCRIPTION, schema.description());
    }

    gen.writeEndObject();
  }

  private void serializeNullSchema(JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    gen.writeStringField(PROPERTY_TYPE, TYPE_NULL);
    gen.writeEndObject();
  }

  private void serializeReferenceSchema(JsonReferenceSchema schema, JsonGenerator gen)
      throws IOException {
    gen.writeStartObject();

    gen.writeStringField(PROPERTY_REF, schema.reference());

    if (schema.description() != null) {
      gen.writeStringField(PROPERTY_DESCRIPTION, schema.description());
    }

    gen.writeEndObject();
  }

  private void serializeArraySchema(JsonArraySchema schema, JsonGenerator gen) throws IOException {
    gen.writeStartObject();

    gen.writeStringField(PROPERTY_TYPE, TYPE_ARRAY);

    if (schema.description() != null) {
      gen.writeStringField(PROPERTY_DESCRIPTION, schema.description());
    }

    if (schema.items() != null) {
      gen.writeObjectField(PROPERTY_ITEMS, schema.items());
    }

    gen.writeEndObject();
  }

  private void serializeAnyOfSchema(JsonAnyOfSchema schema, JsonGenerator gen) throws IOException {
    gen.writeStartObject();

    if (schema.description() != null) {
      gen.writeStringField(PROPERTY_DESCRIPTION, schema.description());
    }

    gen.writeArrayFieldStart(PROPERTY_ANYOF);
    for (JsonSchemaElement item : schema.anyOf()) {
      gen.writeObject(item);
    }
    gen.writeEndArray();

    gen.writeEndObject();
  }
}
