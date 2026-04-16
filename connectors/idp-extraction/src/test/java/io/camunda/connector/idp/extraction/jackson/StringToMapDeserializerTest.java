/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.jackson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StringToMapDeserializerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  record TestRecord(
      @JsonDeserialize(using = StringToMapDeserializer.class) Map<String, String> headers) {}

  @Test
  void shouldDeserializeJsonObject() throws JsonProcessingException {
    var json =
        """
        {"headers": {"Authorization": "Bearer token123", "Content-Type": "application/json"}}
        """;

    var result = objectMapper.readValue(json, TestRecord.class);

    assertThat(result.headers())
        .containsEntry("Authorization", "Bearer token123")
        .containsEntry("Content-Type", "application/json")
        .hasSize(2);
  }

  @Test
  void shouldDeserializeJsonString() throws JsonProcessingException {
    var json =
        """
        {"headers": "{\\"Authorization\\": \\"Bearer token123\\", \\"Content-Type\\": \\"application/json\\"}"}
        """;

    var result = objectMapper.readValue(json, TestRecord.class);

    assertThat(result.headers())
        .containsEntry("Authorization", "Bearer token123")
        .containsEntry("Content-Type", "application/json")
        .hasSize(2);
  }

  @Test
  void shouldDeserializeSingleEntryString() throws JsonProcessingException {
    var json =
        """
        {"headers": "{\\"Authorization\\": \\"Bearer sk-proj-abc123\\"}"}
        """;

    var result = objectMapper.readValue(json, TestRecord.class);

    assertThat(result.headers()).containsEntry("Authorization", "Bearer sk-proj-abc123").hasSize(1);
  }

  @Test
  void shouldFailOnInvalidJsonString() {
    var json =
        """
        {"headers": "not-valid-json"}
        """;

    assertThatThrownBy(() -> objectMapper.readValue(json, TestRecord.class))
        .isInstanceOf(JsonProcessingException.class);
  }

  @Test
  void shouldFailOnArrayInput() {
    var json =
        """
        {"headers": ["a", "b"]}
        """;

    assertThatThrownBy(() -> objectMapper.readValue(json, TestRecord.class))
        .isInstanceOf(JsonProcessingException.class);
  }
}
