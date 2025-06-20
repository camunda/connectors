/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.avro.AvroFactory;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import io.confluent.kafka.schemaregistry.json.JsonSchemaUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;

public class GenericRecordConverter {

  private static final ObjectMapper JACKSON_AVRO_MAPPER = new ObjectMapper(new AvroFactory());
  private final ObjectMapper objectMapper = new ObjectMapper();

  public GenericRecord toGenericRecord(Schema schema, Map<String, Object> data) {
    GenericRecord record = new GenericData.Record(schema);

    for (Map.Entry<String, Object> entry : data.entrySet()) {
      String fieldName = entry.getKey();
      Object fieldValue = entry.getValue();

      // Find the schema field
      Schema.Field field = schema.getField(fieldName);
      if (field != null) {
        Schema fieldSchema = field.schema();

        // If the field is a union, find the appropriate schema type
        if (fieldSchema.getType() == Schema.Type.UNION) {
          fieldValue = handleUnionType(fieldSchema, fieldValue);
        }
        // If the field value is a nested Map, convert it to GenericRecord
        else if (fieldValue instanceof Map && fieldSchema.getType() == Schema.Type.RECORD) {
          fieldValue = toGenericRecord(fieldSchema, (Map<String, Object>) fieldValue);
        } else if (fieldValue instanceof List<?>) {
          fieldValue = handleListType((List<?>) fieldValue, fieldSchema);
        }

        // Put the value into the GenericRecord
        record.put(fieldName, fieldValue);
      }
    }

    return record;
  }

  public ObjectNode toObjectNode(GenericRecord record) {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); ) {
      BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
      DatumWriter<GenericRecord> writer = new GenericDatumWriter<>(record.getSchema());
      writer.write(record, encoder);
      encoder.flush();
      return JACKSON_AVRO_MAPPER
          .readerFor(ObjectNode.class)
          .with(new AvroSchema(record.getSchema()))
          .readValue(outputStream.toByteArray());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public ObjectNode toObjectNode(String schema, Map<String, Object> payload) {
    JsonSchema jsonSchema = new JsonSchema(schema);
    JsonNode jsonPayload = objectMapper.convertValue(payload, ObjectNode.class);

    return JsonSchemaUtils.envelope(jsonSchema, jsonPayload);
  }

  private Object handleUnionType(Schema unionSchema, Object value) {
    for (Schema schema : unionSchema.getTypes()) {
      if (schema.getType() == Schema.Type.RECORD && value instanceof Map) {
        return toGenericRecord(schema, (Map<String, Object>) value);
      } else if (schema.getType() == Schema.Type.ARRAY && value instanceof List) {
        return handleListType((List<?>) value, schema);
      }
    }
    return value;
  }

  private List<Object> handleListType(List<?> value, Schema schema) {
    List<Object> list = new ArrayList<>();
    Schema elementSchema = schema.getElementType();

    for (Object item : value) {
      if (item instanceof Map && elementSchema.getType() == Schema.Type.RECORD) {
        list.add(toGenericRecord(elementSchema, (Map<String, Object>) item));
      } else {
        list.add(item);
      }
    }
    return list;
  }
}
