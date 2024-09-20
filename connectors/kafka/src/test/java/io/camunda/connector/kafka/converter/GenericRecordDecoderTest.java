/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.kafka.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

public class GenericRecordDecoderTest {

  private final GenericRecordDecoder genericRecordDecoder = new GenericRecordDecoder();
  private final Schema schema;

  public GenericRecordDecoderTest() throws IOException, URISyntaxException {
    this.schema =
        new Schema.Parser()
            .parse(
                Files.readString(
                    Paths.get(ClassLoader.getSystemResource("nested-avro-schema.json").toURI())));
  }

  @Test
  public void shouldDecodeMap_whenAllFieldAreSet() {
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
    GenericRecord record = genericRecordDecoder.decode(schema, value);

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
}
