/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.kafka.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.avro.AvroFactory;
import com.fasterxml.jackson.dataformat.avro.AvroSchema;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;

public class GenericRecordConverter {

  private static final ObjectMapper JACKSON_AVRO_MAPPER = new ObjectMapper(new AvroFactory());

  /**
   * For a union schema, the record type is taken from the Avro JSON wrapper key ({@code {"ns.Type":
   * {...}}}) if present, otherwise inferred from the fields of the value.
   */
  public GenericRecord toGenericRecord(Schema schema, Map<String, Object> data) {
    if (schema.getType() != Schema.Type.UNION) {
      return toRecord(schema, data);
    }
    return resolveUnionMember(schema, data)
        .filter(member ->
            member.schema().getType() == Schema.Type.RECORD && member.value() instanceof Map)
        .map(member -> toRecord(member.schema(), (Map<?, ?>) member.value()))
        .orElseThrow(() ->
            new IllegalArgumentException(
                "The message value must be one of the records of the union "
                    + memberNames(schema)
                    + ". "
                    + wrapperHint(schema, List.of())));
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

  private GenericRecord toRecord(Schema schema, Map<?, ?> data) {
    GenericRecord record = new GenericData.Record(schema);
    schema.getFields().stream()
        .filter(Schema.Field::hasDefaultValue)
        .forEach(field -> record.put(field.pos(), GenericData.get().getDefaultValue(field)));
    data.forEach((key, value) -> {
      Schema.Field field = schema.getField(String.valueOf(key));
      if (field != null) {
        record.put(field.pos(), convert(field.schema(), value));
      }
    });
    return record;
  }

  private Object convert(Schema schema, Object value) {
    return switch (schema.getType()) {
      case RECORD -> value instanceof Map<?, ?> map ? toRecord(schema, map) : value;
      case UNION ->
          resolveUnionMember(schema, value)
              .map(member -> convert(member.schema(), member.value()))
              .orElse(value);
      case ARRAY -> {
        if (!(value instanceof List<?> items)) {
          yield value;
        }
        List<Object> converted = new ArrayList<>(items.size());
        items.forEach(item -> converted.add(convert(schema.getElementType(), item)));
        yield converted;
      }
      case MAP -> {
        if (!(value instanceof Map<?, ?> entries)) {
          yield value;
        }
        Map<Object, Object> converted = new LinkedHashMap<>();
        entries.forEach((key, item) -> converted.put(key, convert(schema.getValueType(), item)));
        yield converted;
      }
      default -> value;
    };
  }

  /** Empty if the value is left as it is, for the writer to match it against the union. */
  private Optional<UnionMember> resolveUnionMember(Schema union, Object value) {
    if (value == null) {
      return memberOfType(union, Schema.Type.NULL).map(member -> new UnionMember(member, null));
    }
    if (value instanceof Map<?, ?> map) {
      var wrapped = unwrap(union, map);
      if (wrapped.isPresent()) {
        return wrapped;
      }
      return Optional.of(new UnionMember(inferMember(union, map), value));
    }
    if (value instanceof List<?>) {
      return memberOfType(union, Schema.Type.ARRAY).map(member -> new UnionMember(member, value));
    }
    return Optional.empty();
  }

  private Optional<UnionMember> unwrap(Schema union, Map<?, ?> map) {
    if (map.size() != 1) {
      return Optional.empty();
    }
    var entry = map.entrySet().iterator().next();
    var key = String.valueOf(entry.getKey());
    // with a map member, {"string": "v"} or {"map": {...}} is an ordinary map entry
    var targets =
        memberOfType(union, Schema.Type.MAP).isPresent()
            ? union.getTypes().stream()
                .filter(member -> member.getType() == Schema.Type.RECORD)
                .toList()
            : union.getTypes();
    var byFullName = targets.stream().filter(member -> member.getFullName().equals(key)).toList();
    var byShortName = targets.stream().filter(member -> member.getName().equals(key)).toList();
    var matches = byFullName.isEmpty() ? byShortName : byFullName;
    return matches.size() == 1 && fitsType(matches.getFirst(), entry.getValue())
        ? Optional.of(new UnionMember(matches.getFirst(), entry.getValue()))
        : Optional.empty();
  }

  private static boolean fitsType(Schema member, Object value) {
    return switch (member.getType()) {
      case RECORD, MAP -> value instanceof Map;
      case ARRAY -> value instanceof List;
      case NULL -> value == null;
      default -> value != null && !(value instanceof Map) && !(value instanceof List);
    };
  }

  private Schema inferMember(Schema union, Map<?, ?> map) {
    var records =
        union.getTypes().stream().filter(member -> member.getType() == Schema.Type.RECORD).toList();
    if (records.size() == 1
        && (memberOfType(union, Schema.Type.MAP).isEmpty()
            || hasRequiredFieldsOf(records.getFirst(), map))) {
      return records.getFirst();
    }
    var candidates =
        records.stream()
            .filter(record -> hasOnlyFieldsOf(record, map) && hasRequiredFieldsOf(record, map))
            .toList();
    if (candidates.isEmpty()) {
      candidates = records.stream().filter(record -> hasRequiredFieldsOf(record, map)).toList();
    }
    if (candidates.size() == 1) {
      return candidates.getFirst();
    }
    if (candidates.size() > 1) {
      throw new IllegalArgumentException(
          "The value matches several types of the union "
              + memberNames(union)
              + ". "
              + wrapperHint(union, candidates));
    }
    return memberOfType(union, Schema.Type.MAP)
        .orElseThrow(() ->
            new IllegalArgumentException(
                "The value matches none of the types of the union "
                    + memberNames(union)
                    + ". "
                    + wrapperHint(union, records)));
  }

  private static boolean hasOnlyFieldsOf(Schema record, Map<?, ?> map) {
    return map.keySet().stream().allMatch(key -> record.getField(String.valueOf(key)) != null);
  }

  private static boolean hasRequiredFieldsOf(Schema record, Map<?, ?> map) {
    return record.getFields().stream()
        .filter(field -> !field.hasDefaultValue() && !field.schema().isNullable())
        .allMatch(field -> map.containsKey(field.name()));
  }

  private static Optional<Schema> memberOfType(Schema union, Schema.Type type) {
    return union.getTypes().stream().filter(member -> member.getType() == type).findFirst();
  }

  private static String memberNames(Schema union) {
    return union.getTypes().stream()
        .map(Schema::getFullName)
        .collect(Collectors.joining(", ", "[", "]"));
  }

  private static String wrapperHint(Schema union, List<Schema> preferred) {
    var example =
        preferred.stream()
            .findFirst()
            .or(() -> memberOfType(union, Schema.Type.RECORD))
            .orElse(union.getTypes().getFirst())
            .getFullName();
    return "Wrap it with the type name as the only key, for example {\"" + example + "\": {...}}.";
  }

  private record UnionMember(Schema schema, Object value) {}
}
