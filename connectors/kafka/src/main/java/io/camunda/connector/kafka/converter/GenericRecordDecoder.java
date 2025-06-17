/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.kafka.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

public class GenericRecordDecoder {

  public GenericRecord envelope(Schema schema, Map<String, Object> data) {
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
          fieldValue = envelope(fieldSchema, (Map<String, Object>) fieldValue);
        } else if (fieldValue instanceof List<?>) {
          fieldValue = handleListType((List<?>) fieldValue, fieldSchema);
        }

        // Put the value into the GenericRecord
        record.put(fieldName, fieldValue);
      }
    }

    return record;
  }

  private Object handleUnionType(Schema unionSchema, Object value) {
    for (Schema schema : unionSchema.getTypes()) {
      if (schema.getType() == Schema.Type.RECORD && value instanceof Map) {
        return envelope(schema, (Map<String, Object>) value);
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
        list.add(envelope(elementSchema, (Map<String, Object>) item));
      } else {
        list.add(item);
      }
    }
    return list;
  }
}
