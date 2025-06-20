/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.kafka.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ObjectNodeConverterTest {
  private final String schema;

  public ObjectNodeConverterTest() throws IOException, URISyntaxException {
    this.schema =
        Files.readString(
                Paths.get(ClassLoader.getSystemResource("nested-json-schema.json").toURI()))
            .replaceAll("\\s+", "");
    ;
  }

  @Test
  public void testDecode() throws IOException {
    // given
    ObjectNodeConverter objectNodeConverter = new ObjectNodeConverter();
    Map<String, Object> message =
        Map.of(
            "colleagues",
            List.of(
                Map.of("name", "Colleague1", "age", 30, "emails", List.of("test2@camunda.com"))),
            "name",
            "Test",
            "nickname",
            "theNickname",
            "age",
            40,
            "emails",
            List.of("test@camunda.com"),
            "boss",
            Map.of("name", "Boss", "position", "CEO"));

    // when
    ObjectNode result = objectNodeConverter.toObjectNode(schema, message);

    // then
    assertNotNull(result);
    assertTrue(result.has("schema"));
    assertTrue(result.has("payload"));
    var schema = result.get("schema");
    var payload = result.get("payload");
    // check payload fields
    assertThat(payload.get("name").asText()).isEqualTo("Test");
    assertThat(payload.get("nickname").asText()).isEqualTo("theNickname");
    assertThat(payload.get("age").asInt()).isEqualTo(40);
    assertThat(payload.get("emails").get(0).asText()).isEqualTo("test@camunda.com");
    // check nested fields
    var boss = payload.get("boss");
    assertThat(boss.get("name").asText()).isEqualTo("Boss");
    assertThat(boss.get("position").asText()).isEqualTo("CEO");
    // check array fields
    var colleagues = payload.get("colleagues");
    assertThat(colleagues.get(0).get("name").asText()).isEqualTo("Colleague1");
    assertThat(colleagues.get(0).get("age").asInt()).isEqualTo(30);
    assertThat(colleagues.get(0).get("emails").get(0).asText()).isEqualTo("test2@camunda.com");

    // assert schema
    var objectMapper = new ObjectMapper();
    assertThat(objectMapper.writeValueAsString(schema)).isEqualTo(this.schema);
  }
}
