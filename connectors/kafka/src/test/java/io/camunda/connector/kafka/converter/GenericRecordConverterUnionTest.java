/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.kafka.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.EncoderFactory;
import org.junit.jupiter.api.Test;

public class GenericRecordConverterUnionTest {

  private static final String CREATE_TASK =
      """
      {"type":"record","name":"CreateTask","namespace":"test",
       "fields":[{"name":"taskId","type":"string"},{"name":"title","type":"string"}]}
      """;
  private static final String CANCEL_TASK =
      """
      {"type":"record","name":"CancelTask","namespace":"test",
       "fields":[{"name":"taskId","type":"string"},{"name":"reason","type":"string"}]}
      """;

  private final GenericRecordConverter converter = new GenericRecordConverter();

  private static Schema envelopeWith(String fieldType) {
    return new Schema.Parser()
        .parse(
            """
            {"type":"record","name":"Envelope","namespace":"test",
             "fields":[{"name":"command","type":%s}]}
            """
                .formatted(fieldType));
  }

  @Test
  void nullableRecord_isConverted() {
    var schema = envelopeWith("[\"null\"," + CREATE_TASK + "]");

    var record =
        converter.toGenericRecord(
            schema, Map.of("command", Map.of("taskId", "1", "title", "Write tests")));

    var command = (GenericRecord) record.get("command");
    assertThat(command.getSchema().getFullName()).isEqualTo("test.CreateTask");
    assertThat(GenericData.get().validate(schema, record)).isTrue();
  }

  @Test
  void unionOfSeveralRecords_firstMemberIsUsedWhateverTheValueLooksLike() {
    var schema = envelopeWith("[" + CREATE_TASK + "," + CANCEL_TASK + "]");

    var record =
        converter.toGenericRecord(
            schema, Map.of("command", Map.of("taskId", "1", "reason", "duplicate")));

    var command = (GenericRecord) record.get("command");
    assertThat(command.getSchema().getFullName()).isEqualTo("test.CreateTask");
    assertThat(command.get("title")).isNull();
    assertThat(command.getSchema().getField("reason")).isNull();
    assertThat(GenericData.get().validate(schema, record)).isFalse();
  }

  @Test
  void unionOfSeveralRecords_wrongMemberFailsAtSerialization() {
    var schema = envelopeWith("[" + CREATE_TASK + "," + CANCEL_TASK + "]");
    var record =
        converter.toGenericRecord(
            schema, Map.of("command", Map.of("taskId", "1", "reason", "duplicate")));

    assertThatThrownBy(() -> serialize(record)).isInstanceOf(RuntimeException.class);
  }

  @Test
  void arrayOfUnionElements_objectsAreNotConverted() {
    var schema =
        new Schema.Parser()
            .parse(
                """
                {"type":"record","name":"Batch","namespace":"test",
                 "fields":[{"name":"commands","type":{"type":"array","items":[%s,%s]}}]}
                """
                    .formatted(CREATE_TASK, CANCEL_TASK));

    var record =
        converter.toGenericRecord(
            schema, Map.of("commands", List.of(Map.of("taskId", "1", "title", "Write tests"))));

    assertThat(((List<?>) record.get("commands")).get(0)).isInstanceOf(Map.class);
    assertThat(GenericData.get().validate(schema, record)).isFalse();
  }

  @Test
  void topLevelUnion_isRejected() {
    var schema = new Schema.Parser().parse("[" + CREATE_TASK + "," + CANCEL_TASK + "]");

    assertThatThrownBy(
            () -> converter.toGenericRecord(schema, Map.of("taskId", "1", "title", "Write tests")))
        .isInstanceOf(AvroRuntimeException.class)
        .hasMessageContaining("Not a record schema");
  }

  @Test
  void topLevelUnionOfNameReferences_failsToParse() {
    assertThatThrownBy(() -> new Schema.Parser().parse("[\"test.CreateTask\",\"test.CancelTask\"]"))
        .hasMessageContaining("Undefined schema");
  }

  private static byte[] serialize(GenericRecord record) throws Exception {
    var out = new ByteArrayOutputStream();
    var encoder = EncoderFactory.get().binaryEncoder(out, null);
    new GenericDatumWriter<GenericRecord>(record.getSchema()).write(record, encoder);
    encoder.flush();
    return out.toByteArray();
  }
}
