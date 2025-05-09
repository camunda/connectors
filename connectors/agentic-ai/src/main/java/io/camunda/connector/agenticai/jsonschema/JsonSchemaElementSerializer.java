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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Custom serializer for JsonSchemaElement and its subclasses. This serializer handles the
 * polymorphism of JsonSchemaElement based on its concrete type.
 */
public class JsonSchemaElementSerializer extends JsonSerializer<JsonSchemaElement> {

  @Override
  public void serialize(JsonSchemaElement value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    if (value instanceof JsonObjectSchema objectSchema) {
      serializeObjectSchema(objectSchema, gen);
    } else if (value instanceof JsonEnumSchema enumSchema) {
      serializeEnumSchema(enumSchema, gen);
    } else if (value instanceof JsonStringSchema stringSchema) {
      serializeStringSchema(stringSchema, gen);
    } else if (value instanceof JsonNumberSchema numberSchema) {
      serializeNumberSchema(numberSchema, gen);
    } else if (value instanceof JsonIntegerSchema integerSchema) {
      serializeIntegerSchema(integerSchema, gen);
    } else if (value instanceof JsonBooleanSchema booleanSchema) {
      serializeBooleanSchema(booleanSchema, gen);
    } else if (value instanceof JsonArraySchema arraySchema) {
      serializeArraySchema(arraySchema, gen);
    } else {
      throw new IllegalArgumentException("Unknown schema type: " + value.getClass().getName());
    }
  }

  private void serializeObjectSchema(JsonObjectSchema schema, JsonGenerator gen)
      throws IOException {
    gen.writeStartObject();

    gen.writeStringField(PROPERTY_TYPE, TYPE_OBJECT);

    if (schema.description() != null) {
      gen.writeStringField(PROPERTY_DESCRIPTION, schema.description());
    }

    List<String> required = schema.required();
    if (required != null && !required.isEmpty()) {
      gen.writeArrayFieldStart(PROPERTY_REQUIRED);
      for (String req : required) {
        gen.writeString(req);
      }
      gen.writeEndArray();
    }

    gen.writeBooleanField(PROPERTY_ADDITIONAL_PROPERTIES, schema.additionalProperties());

    if (schema.properties() != null && !schema.properties().isEmpty()) {
      gen.writeObjectFieldStart(PROPERTY_PROPERTIES);
      for (Map.Entry<String, JsonSchemaElement> entry : schema.properties().entrySet()) {
        gen.writeFieldName(entry.getKey());
        serialize(entry.getValue(), gen, null);
      }
      gen.writeEndObject();
    }

    gen.writeEndObject();
  }

  private void serializeEnumSchema(JsonEnumSchema schema, JsonGenerator gen) throws IOException {
    gen.writeStartObject();

    gen.writeStringField(PROPERTY_TYPE, TYPE_STRING);

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

  private void serializeArraySchema(JsonArraySchema schema, JsonGenerator gen) throws IOException {
    gen.writeStartObject();

    gen.writeStringField(PROPERTY_TYPE, TYPE_ARRAY);

    if (schema.description() != null) {
      gen.writeStringField(PROPERTY_DESCRIPTION, schema.description());
    }

    if (schema.items() != null) {
      gen.writeFieldName(PROPERTY_ITEMS);
      serialize(schema.items(), gen, null);
    }

    gen.writeEndObject();
  }
}
