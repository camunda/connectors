/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.kafka.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

public class GenericRecordConverterTest {

  private final GenericRecordConverter genericRecordConverter = new GenericRecordConverter();
  private final Schema schema;

  public GenericRecordConverterTest() throws IOException, URISyntaxException {
    this.schema =
        new Schema.Parser()
            .parse(
                Files.readString(
                    Paths.get(ClassLoader.getSystemResource("nested-avro-schema.json").toURI())));
  }

  @Test
  public void shouldEnvelopeMap_whenAllFieldAreSet() {
    // when
    Map<String, Object> value = new HashMap<>();
    value.put("name", "John Doe");
    value.put("age", 30);
    value.put("emails", List.of("test@camunda.com"));
    value.put("nickname", "JD");
    Map<String, Object> boss = new HashMap<>();
    boss.put("name", "Jane Doe");
    boss.put("position", "CEO");
    value.put("boss", boss);
    Map<String, Object> colleague = new HashMap<>();
    colleague.put("name", "Alice");
    colleague.put("age", 25);
    colleague.put("emails", List.of("alice@camunda.com"));
    value.put("colleagues", List.of(colleague));

    // when
    GenericRecord record = genericRecordConverter.toGenericRecord(schema, value);

    // then
    assertThat(record.get("name")).isEqualTo("John Doe");
    assertThat(record.get("age")).isEqualTo(30);
    assertThat(record.get("emails")).isEqualTo(List.of("test@camunda.com"));
    assertThat(record.get("nickname")).isEqualTo("JD");
    GenericRecord bossRecord = (GenericRecord) record.get("boss");
    assertThat(bossRecord.get("name")).isEqualTo("Jane Doe");
    assertThat(bossRecord.get("position")).isEqualTo("CEO");
    List<GenericRecord> colleagues = (List<GenericRecord>) record.get("colleagues");
    assertThat(colleagues).hasSize(1);
    GenericRecord colleagueRecord = colleagues.get(0);
    assertThat(colleagueRecord.get("name")).isEqualTo("Alice");
    assertThat(colleagueRecord.get("age")).isEqualTo(25);
    assertThat(colleagueRecord.get("emails")).isEqualTo(List.of("alice@camunda.com"));
  }

  @Test
  public void shouldEncoreRecord_whenAllFieldsAreSet() {
    // given
    GenericRecord record = new GenericData.Record(schema);
    record.put("name", "John Doe");
    record.put("age", 30);
    record.put("emails", List.of("test@camunda.com"));
    record.put("nickname", "JD");
    GenericRecord boss =
        new GenericData.Record(schema.getField("boss").schema().getTypes().getFirst());
    boss.put("name", "Jane Doe");
    boss.put("position", "CEO");
    record.put("boss", boss);
    GenericRecord colleague =
        new GenericData.Record(
            schema.getField("colleagues").schema().getTypes().getFirst().getElementType());
    colleague.put("name", "Alice");
    colleague.put("age", 25);
    colleague.put("emails", List.of("alice@camunda.com"));
    record.put("colleagues", List.of(colleague));

    // when
    ObjectNode jsonNode = genericRecordConverter.toObjectNode(record);

    // then
    assertThat(jsonNode.get("name").asText()).isEqualTo("John Doe");
    assertThat(jsonNode.get("age").asInt()).isEqualTo(30);
    assertThat(jsonNode.get("emails").get(0).asText()).isEqualTo("test@camunda.com");
    assertThat(jsonNode.get("nickname").asText()).isEqualTo("JD");
    assertThat(jsonNode.get("boss").get("name").asText()).isEqualTo("Jane Doe");
    assertThat(jsonNode.get("boss").get("position").asText()).isEqualTo("CEO");
    assertThat(jsonNode.get("colleagues").get(0).get("name").asText()).isEqualTo("Alice");
    assertThat(jsonNode.get("colleagues").get(0).get("age").asInt()).isEqualTo(25);
    assertThat(jsonNode.get("colleagues").get(0).get("emails").get(0).asText())
        .isEqualTo("alice@camunda.com");
  }
}
