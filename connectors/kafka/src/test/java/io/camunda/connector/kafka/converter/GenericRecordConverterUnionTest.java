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
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class GenericRecordConverterUnionTest {

  private static final String CREATE_TASK =
      """
      {"type":"record","name":"CreateTask","namespace":"test",
       "fields":[{"name":"taskId","type":"string"},{"name":"title","type":"string"},
                 {"name":"note","type":["null","string"],"default":null}]}
      """;
  private static final String CANCEL_TASK =
      """
      {"type":"record","name":"CancelTask","namespace":"test",
       "fields":[{"name":"taskId","type":"string"},{"name":"reason","type":"string"}]}
      """;
  private static final String DELETE_TASK =
      """
      {"type":"record","name":"DeleteTask","namespace":"test",
       "fields":[{"name":"taskId","type":"string"}]}
      """;

  private static final Schema TASK_UNION =
      new Schema.Parser().parse("[" + CREATE_TASK + "," + CANCEL_TASK + "]");

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
  void topLevelUnion_wrappedInFullName() throws Exception {
    var record =
        converter.toGenericRecord(
            TASK_UNION, Map.of("test.CancelTask", Map.of("taskId", "1", "reason", "duplicate")));

    assertThat(record.getSchema().getFullName()).isEqualTo("test.CancelTask");
    assertThat(record.get("reason")).isEqualTo("duplicate");
    assertWritableAs(TASK_UNION, record);
  }

  @Test
  void topLevelUnion_wrappedInUniqueShortName() throws Exception {
    var record =
        converter.toGenericRecord(
            TASK_UNION, Map.of("CancelTask", Map.of("taskId", "1", "reason", "duplicate")));

    assertThat(record.getSchema().getFullName()).isEqualTo("test.CancelTask");
    assertWritableAs(TASK_UNION, record);
  }

  @Test
  void topLevelUnion_shortNameSharedByTwoMembersIsNotAWrapper() {
    var union =
        new Schema.Parser()
            .parse(
                "["
                    + CANCEL_TASK
                    + ","
                    + CANCEL_TASK.replace("\"namespace\":\"test\"", "\"namespace\":\"legacy\"")
                    + "]");

    assertThatThrownBy(() ->
            converter.toGenericRecord(
                union, Map.of("CancelTask", Map.of("taskId", "1", "reason", "duplicate"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("[test.CancelTask, legacy.CancelTask]");
  }

  @Test
  void topLevelUnion_inferredFromFields() throws Exception {
    var create =
        converter.toGenericRecord(TASK_UNION, Map.of("taskId", "1", "title", "Write tests"));
    var cancel =
        converter.toGenericRecord(TASK_UNION, Map.of("taskId", "1", "reason", "duplicate"));

    assertThat(create.getSchema().getFullName()).isEqualTo("test.CreateTask");
    assertThat(cancel.getSchema().getFullName()).isEqualTo("test.CancelTask");
    assertWritableAs(TASK_UNION, create);
    assertWritableAs(TASK_UNION, cancel);
  }

  @Test
  void topLevelUnion_optionalFieldsMayBeOmittedOrSet() throws Exception {
    var withNote =
        converter.toGenericRecord(
            TASK_UNION, Map.of("taskId", "1", "title", "Write tests", "note", "today"));

    assertThat(withNote.getSchema().getFullName()).isEqualTo("test.CreateTask");
    assertThat(withNote.get("note")).isEqualTo("today");
    assertWritableAs(TASK_UNION, withNote);
  }

  @Test
  void topLevelUnion_ambiguousValueIsRejected() {
    var ambiguous =
        new Schema.Parser()
            .parse(
                "[" + DELETE_TASK + "," + DELETE_TASK.replace("DeleteTask", "ArchiveTask") + "]");

    assertThatThrownBy(() -> converter.toGenericRecord(ambiguous, Map.of("taskId", "1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The value matches several types of the union [test.DeleteTask, test.ArchiveTask]."
                + " Wrap it with the type name as the only key, for example"
                + " {\"test.DeleteTask\": {...}}.");
  }

  @Test
  void topLevelUnion_valueMatchingNoMemberIsRejected() {
    assertThatThrownBy(() -> converter.toGenericRecord(TASK_UNION, Map.of("taskId", "1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The value matches none of the types of the union [test.CreateTask, test.CancelTask]."
                + " Wrap it with the type name as the only key, for example"
                + " {\"test.CreateTask\": {...}}.");
  }

  @Test
  void topLevelUnion_unknownFieldsAreIgnoredWhenNoMemberHasAllOfThem() throws Exception {
    var record =
        converter.toGenericRecord(
            TASK_UNION, Map.of("taskId", "1", "reason", "duplicate", "other", "x"));

    assertThat(record.getSchema().getFullName()).isEqualTo("test.CancelTask");
    assertWritableAs(TASK_UNION, record);
  }

  @Test
  void topLevelUnion_wrapperKeyWhoseValueDoesNotFitTheMemberIsInferred() throws Exception {
    var union =
        new Schema.Parser()
            .parse(
                """
                [{"type":"record","name":"A","namespace":"test",
                  "fields":[{"name":"x","type":"string"}]},
                 {"type":"record","name":"B","namespace":"test",
                  "fields":[{"name":"A","type":"string"}]}]
                """);

    var record = converter.toGenericRecord(union, Map.of("A", "v"));

    assertThat(record.getSchema().getFullName()).isEqualTo("test.B");
    assertThat(record.get("A")).isEqualTo("v");
    assertWritableAs(union, record);
  }

  @Test
  void topLevelUnion_missingFieldWithDefaultGetsTheDefault() throws Exception {
    var union =
        new Schema.Parser()
            .parse(
                """
                [{"type":"record","name":"Prioritize","namespace":"test",
                  "fields":[{"name":"id","type":"string"},
                            {"name":"prio","type":"int","default":0}]},
                 %s]
                """
                    .formatted(CANCEL_TASK));

    var record = converter.toGenericRecord(union, Map.of("id", "1"));

    assertThat(record.getSchema().getFullName()).isEqualTo("test.Prioritize");
    assertThat(record.get("prio")).isEqualTo(0);
    assertWritableAs(union, record);
  }

  @Test
  void topLevelUnion_wrappedNonRecordMemberIsRejected() {
    var union = new Schema.Parser().parse("[\"string\"," + CREATE_TASK + "]");

    assertThatThrownBy(() -> converter.toGenericRecord(union, Map.of("string", "text")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The message value must be one of the records of the union [string, test.CreateTask]."
                + " Wrap it with the type name as the only key, for example"
                + " {\"test.CreateTask\": {...}}.");
  }

  @Test
  void nestedUnion_picksTheMemberMatchingTheValue() throws Exception {
    var schema = envelopeWith("[" + CREATE_TASK + "," + CANCEL_TASK + "]");

    var record =
        converter.toGenericRecord(
            schema, Map.of("command", Map.of("taskId", "1", "reason", "duplicate")));

    var command = (GenericRecord) record.get("command");
    assertThat(command.getSchema().getFullName()).isEqualTo("test.CancelTask");
    assertThat(command.get("reason")).isEqualTo("duplicate");
    assertWritableAs(schema, record);
  }

  @Test
  void nestedUnion_wrappedValueIsUnwrapped() throws Exception {
    var schema = envelopeWith("[" + CREATE_TASK + "," + CANCEL_TASK + "]");

    var record =
        converter.toGenericRecord(
            schema,
            Map.of("command", Map.of("test.CreateTask", Map.of("taskId", "1", "title", "Write"))));

    var command = (GenericRecord) record.get("command");
    assertThat(command.getSchema().getFullName()).isEqualTo("test.CreateTask");
    assertWritableAs(schema, record);
  }

  @Test
  void nestedUnion_unknownFieldsAreIgnoredWhenNoMemberHasAllOfThem() throws Exception {
    var schema = envelopeWith("[\"null\"," + CREATE_TASK + "," + CANCEL_TASK + "]");

    var record =
        converter.toGenericRecord(
            schema, Map.of("command", Map.of("taskId", "1", "reason", "duplicate", "other", "x")));

    var command = (GenericRecord) record.get("command");
    assertThat(command.getSchema().getFullName()).isEqualTo("test.CancelTask");
    assertWritableAs(schema, record);
  }

  @Test
  void unionOfRecordsAndMap_recordIsPreferredDespiteUnknownFields() throws Exception {
    var schema =
        envelopeWith(
            "[" + CREATE_TASK + "," + CANCEL_TASK + ",{\"type\":\"map\",\"values\":\"string\"}]");

    var record =
        converter.toGenericRecord(
            schema, Map.of("command", Map.of("taskId", "1", "reason", "duplicate", "other", "x")));

    var command = (GenericRecord) record.get("command");
    assertThat(command.getSchema().getFullName()).isEqualTo("test.CancelTask");
    assertWritableAs(schema, record);
  }

  @Test
  void unionOfRecordsAndMap_valueMatchingNoRecordIsTheMap() throws Exception {
    var schema =
        envelopeWith(
            "[" + CREATE_TASK + "," + CANCEL_TASK + ",{\"type\":\"map\",\"values\":\"string\"}]");

    var record = converter.toGenericRecord(schema, Map.of("command", Map.of("color", "red")));

    assertThat(record.get("command")).isEqualTo(Map.of("color", "red"));
    assertWritableAs(schema, record);
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "map"})
  void nullableMap_keyNamedAfterAMemberIsAnEntry(String key) throws Exception {
    var schema = envelopeWith("[\"null\",{\"type\":\"map\",\"values\":\"string\"}]");

    var record = converter.toGenericRecord(schema, Map.of("command", Map.of(key, "v")));

    assertThat(record.get("command")).isEqualTo(Map.of(key, "v"));
    assertWritableAs(schema, record);
  }

  @Test
  void stringOrMap_keyNamedAfterThePrimitiveIsAMapEntry() throws Exception {
    var schema = envelopeWith("[\"string\",{\"type\":\"map\",\"values\":\"string\"}]");

    var record = converter.toGenericRecord(schema, Map.of("command", Map.of("string", "v")));

    assertThat(record.get("command")).isEqualTo(Map.of("string", "v"));
    assertWritableAs(schema, record);
  }

  @Test
  void nullableMapOfMaps_outerKeyNamedMapIsKept() throws Exception {
    var schema =
        envelopeWith(
            "[\"null\",{\"type\":\"map\",\"values\":{\"type\":\"map\",\"values\":\"string\"}}]");

    var record =
        converter.toGenericRecord(schema, Map.of("command", Map.of("map", Map.of("a", "b"))));

    assertThat(record.get("command")).isEqualTo(Map.of("map", Map.of("a", "b")));
    assertWritableAs(schema, record);
  }

  @Test
  void recordOrMap_valueWithoutTheRecordsRequiredFieldsIsTheMap() throws Exception {
    var schema = envelopeWith("[" + CREATE_TASK + ",{\"type\":\"map\",\"values\":\"string\"}]");

    var record = converter.toGenericRecord(schema, Map.of("command", Map.of("color", "red")));

    assertThat(record.get("command")).isEqualTo(Map.of("color", "red"));
    assertWritableAs(schema, record);
  }

  @Test
  void recordOrMap_valueWithTheRecordsFieldsIsTheRecord() throws Exception {
    var schema = envelopeWith("[" + CREATE_TASK + ",{\"type\":\"map\",\"values\":\"string\"}]");

    var record =
        converter.toGenericRecord(
            schema, Map.of("command", Map.of("taskId", "1", "title", "Write tests")));

    var command = (GenericRecord) record.get("command");
    assertThat(command.getSchema().getFullName()).isEqualTo("test.CreateTask");
    assertWritableAs(schema, record);
  }

  @Test
  void recordOrMap_wrappedRecordIsUnwrapped() throws Exception {
    var schema = envelopeWith("[" + CREATE_TASK + ",{\"type\":\"map\",\"values\":\"string\"}]");

    var record =
        converter.toGenericRecord(
            schema,
            Map.of(
                "command",
                Map.of("test.CreateTask", Map.of("taskId", "1", "title", "Write tests"))));

    var command = (GenericRecord) record.get("command");
    assertThat(command.getSchema().getFullName()).isEqualTo("test.CreateTask");
    assertThat(command.get("taskId")).hasToString("1");
    assertWritableAs(schema, record);
  }

  @Test
  void nestedUnion_wrappedNullIsNull() throws Exception {
    var schema = envelopeWith("[\"null\",\"string\"]");
    var wrappedNull = new HashMap<String, Object>();
    wrappedNull.put("null", null);

    var record = converter.toGenericRecord(schema, Map.of("command", wrappedNull));

    assertThat(record.get("command")).isNull();
    assertWritableAs(schema, record);
  }

  @Test
  void arrayOfNullableItems_wrappedNullItemIsNull() throws Exception {
    var schema = envelopeWith("{\"type\":\"array\",\"items\":[\"null\",\"string\"]}");
    var wrappedNull = new HashMap<String, Object>();
    wrappedNull.put("null", null);

    var record = converter.toGenericRecord(schema, Map.of("command", List.of(wrappedNull, "a")));

    assertThat((List<Object>) record.get("command")).containsExactly(null, "a");
    assertWritableAs(schema, record);
  }

  @Test
  void nestedUnion_wrappedLongIsWrittenAsLong() throws Exception {
    var schema = envelopeWith("[\"int\",\"long\"]");

    var record = converter.toGenericRecord(schema, Map.of("command", Map.of("long", 1)));

    assertThat(record.get("command")).isEqualTo(1L);
    assertThat(writeAndRead(schema, record).get("command")).isEqualTo(1L);
  }

  @Test
  void nullableLong_wrappedFractionIsRejected() {
    var schema = envelopeWith("[\"null\",\"long\"]");

    assertThatThrownBy(
            () -> converter.toGenericRecord(schema, Map.of("command", Map.of("long", 1.5))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The value 1.5 can't be written as the union type long: it must be a whole number"
                + " from -9223372036854775808 to 9223372036854775807.");
  }

  @Test
  void nullableInt_wrappedNumberOutOfRangeIsRejected() {
    var schema = envelopeWith("[\"null\",\"int\"]");

    assertThatThrownBy(() ->
            converter.toGenericRecord(schema, Map.of("command", Map.of("int", 3_000_000_000L))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The value 3000000000 can't be written as the union type int: it must be a whole"
                + " number from -2147483648 to 2147483647.");
  }

  @Test
  void nullableLong_wrappedNaNIsRejected() {
    var schema = envelopeWith("[\"null\",\"long\"]");

    assertThatThrownBy(
            () -> converter.toGenericRecord(schema, Map.of("command", Map.of("long", Double.NaN))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("The value NaN can't be written as the union type long");
  }

  @Test
  void nullableLong_wrappedWholeDoubleIsALong() throws Exception {
    var schema = envelopeWith("[\"null\",\"long\"]");

    var record = converter.toGenericRecord(schema, Map.of("command", Map.of("long", 2.0)));

    assertThat(record.get("command")).isEqualTo(2L);
    assertWritableAs(schema, record);
  }

  @Test
  void nullableLong_wrappedLargeWholeDoubleKeepsItsExactValue() throws Exception {
    var schema = envelopeWith("[\"null\",\"long\"]");

    var record =
        converter.toGenericRecord(schema, Map.of("command", Map.of("long", (double) (1L << 60))));

    assertThat(record.get("command")).isEqualTo(1L << 60);
    assertWritableAs(schema, record);
  }

  @Test
  void nullableLong_wrappedBigDecimalIsALong() throws Exception {
    var schema = envelopeWith("[\"null\",\"long\"]");

    var record =
        converter.toGenericRecord(
            schema, Map.of("command", Map.of("long", new BigDecimal("42.000"))));

    assertThat(record.get("command")).isEqualTo(42L);
    assertWritableAs(schema, record);
  }

  @Test
  void nullableFloat_wrappedNumberOutOfRangeIsRejected() {
    var schema = envelopeWith("[\"null\",\"float\"]");

    assertThatThrownBy(
            () -> converter.toGenericRecord(schema, Map.of("command", Map.of("float", 1e39))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The value 1.0E39 can't be written as the union type float: it is outside the range"
                + " of float.");
  }

  @Test
  void nullableDouble_wrappedBigDecimalOutOfRangeIsRejected() {
    var schema = envelopeWith("[\"null\",\"double\"]");

    assertThatThrownBy(() ->
            converter.toGenericRecord(
                schema, Map.of("command", Map.of("double", new BigDecimal("1e400")))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The value 1E+400 can't be written as the union type double: it is outside the range"
                + " of double.");
  }

  @Test
  void nullableFloat_wrappedDoubleIsRoundedToAFloat() throws Exception {
    var schema = envelopeWith("[\"null\",\"float\"]");

    var record = converter.toGenericRecord(schema, Map.of("command", Map.of("float", 0.1)));

    assertThat(record.get("command")).isEqualTo(0.1f);
    assertWritableAs(schema, record);
  }

  @Test
  void nullableDouble_wrappedNaNIsKept() throws Exception {
    var schema = envelopeWith("[\"null\",\"double\"]");

    var record = converter.toGenericRecord(schema, Map.of("command", Map.of("double", Double.NaN)));

    assertThat((Double) record.get("command")).isNaN();
    assertWritableAs(schema, record);
  }

  @Test
  void nullableLong_wrappedIntegerIsALong() throws Exception {
    var schema = envelopeWith("[\"null\",\"long\"]");

    var record = converter.toGenericRecord(schema, Map.of("command", Map.of("long", 1)));

    assertThat(record.get("command")).isEqualTo(1L);
    assertWritableAs(schema, record);
  }

  @Test
  void nullableEnum_wrappedSymbolIsAnEnumSymbol() throws Exception {
    var schema =
        envelopeWith(
            "[\"null\",{\"type\":\"enum\",\"name\":\"Color\",\"namespace\":\"test\","
                + "\"symbols\":[\"RED\",\"GREEN\"]}]");

    var record = converter.toGenericRecord(schema, Map.of("command", Map.of("test.Color", "RED")));

    assertThat(record.get("command")).isInstanceOf(GenericData.EnumSymbol.class).hasToString("RED");
    assertWritableAs(schema, record);
  }

  @Test
  void nestedUnion_wrapperWithValueOfTheWrongTypeIsNotUnwrapped() {
    var schema = envelopeWith("[\"null\",\"string\"]");

    assertThatThrownBy(
            () -> converter.toGenericRecord(schema, Map.of("command", Map.of("string", 5))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("The value matches none of the types of the union [null, string].");
  }

  @Test
  void arrayOfUnionItems_eachItemIsConvertedToItsMember() throws Exception {
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
            schema,
            Map.of(
                "commands",
                List.of(
                    Map.of("taskId", "1", "title", "Write tests"),
                    Map.of("taskId", "2", "reason", "duplicate"))));

    var commands = (List<?>) record.get("commands");
    assertThat(commands)
        .map(command -> ((GenericRecord) command).getSchema().getFullName())
        .containsExactly("test.CreateTask", "test.CancelTask");
    assertWritableAs(schema, record);
  }

  @Test
  void mapOfRecords_valuesAreConverted() throws Exception {
    var schema = envelopeWith("{\"type\":\"map\",\"values\":" + CANCEL_TASK + "}");

    var record =
        converter.toGenericRecord(
            schema,
            Map.of("command", Map.of("first", Map.of("taskId", "1", "reason", "duplicate"))));

    var commands = (Map<?, ?>) record.get("command");
    assertThat(commands.get("first")).isInstanceOf(GenericRecord.class);
    assertWritableAs(schema, record);
  }

  @Test
  void nullableRecord_isConvertedEvenWithUnknownFields() throws Exception {
    var schema = envelopeWith("[\"null\"," + CREATE_TASK + "]");

    var record =
        converter.toGenericRecord(
            schema,
            Map.of("command", Map.of("taskId", "1", "title", "Write tests", "other", "ignored")));

    var command = (GenericRecord) record.get("command");
    assertThat(command.getSchema().getFullName()).isEqualTo("test.CreateTask");
    assertWritableAs(schema, record);
  }

  @Test
  void nullableRecord_acceptsNull() throws Exception {
    var schema = envelopeWith("[\"null\"," + CREATE_TASK + "]");
    var value = new HashMap<String, Object>();
    value.put("command", null);

    var record = converter.toGenericRecord(schema, value);

    assertThat(record.get("command")).isNull();
    assertWritableAs(schema, record);
  }

  @Test
  void topLevelUnionOfNameReferences_failsToParse() {
    assertThatThrownBy(() -> new Schema.Parser().parse("[\"test.CreateTask\",\"test.CancelTask\"]"))
        .hasMessageContaining("Undefined schema");
  }

  private static void assertWritableAs(Schema schema, GenericRecord record) throws Exception {
    assertThat(GenericData.get().validate(schema, record)).isTrue();
    var read = writeAndRead(schema, record);
    assertThat(read.getSchema().getFullName()).isEqualTo(record.getSchema().getFullName());
  }

  private static GenericRecord writeAndRead(Schema schema, GenericRecord record) throws Exception {
    var out = new ByteArrayOutputStream();
    var encoder = EncoderFactory.get().binaryEncoder(out, null);
    new GenericDatumWriter<Object>(schema).write(record, encoder);
    encoder.flush();
    return new GenericDatumReader<GenericRecord>(schema)
        .read(null, DecoderFactory.get().binaryDecoder(out.toByteArray(), null));
  }
}
