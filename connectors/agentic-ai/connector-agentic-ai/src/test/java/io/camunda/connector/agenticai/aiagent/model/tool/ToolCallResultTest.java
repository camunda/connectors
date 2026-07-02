/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolCallResultTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldRoundTripProperties() throws Exception {
    // given
    ToolCallResult original =
        ToolCallResult.builder()
            .id("call-1")
            .name("search")
            .content("Found 3 items")
            .properties(Map.of("interrupted", true, "custom", "value"))
            .build();

    // when
    String json = objectMapper.writeValueAsString(original);
    ToolCallResult deserialized = objectMapper.readValue(json, ToolCallResult.class);

    // then
    assertThat(deserialized.id()).isEqualTo("call-1");
    assertThat(deserialized.name()).isEqualTo("search");
    assertThat(deserialized.content()).isEqualTo("Found 3 items");
    assertThat(deserialized.properties()).isEqualTo(Map.of("interrupted", true, "custom", "value"));
  }

  @Test
  void shouldRoundTripWithoutProperties() throws Exception {
    // given
    ToolCallResult original =
        ToolCallResult.builder().id("call-1").name("search").content("result").build();

    // when
    String json = objectMapper.writeValueAsString(original);
    ToolCallResult deserialized = objectMapper.readValue(json, ToolCallResult.class);

    // then
    assertThat(deserialized.id()).isEqualTo("call-1");
    assertThat(deserialized.name()).isEqualTo("search");
    assertThat(deserialized.content()).isEqualTo("result");
    assertThat(deserialized.properties()).isEmpty();
  }

  @Test
  void shouldRoundTripCancelledToolCall() throws Exception {
    // given
    ToolCallResult original = ToolCallResult.forCancelledToolCall("call-1", "search");

    // when
    String json = objectMapper.writeValueAsString(original);
    ToolCallResult deserialized = objectMapper.readValue(json, ToolCallResult.class);

    // then
    assertThat(deserialized.id()).isEqualTo("call-1");
    assertThat(deserialized.name()).isEqualTo("search");
    assertThat(deserialized.content()).isEqualTo(ToolCallResult.CONTENT_CANCELLED);
    assertThat(deserialized.properties())
        .isEqualTo(Map.of(ToolCallResult.PROPERTY_INTERRUPTED, true));
  }

  @Test
  void shouldRoundTripCompletedAt() throws Exception {
    // given
    OffsetDateTime completedAt = OffsetDateTime.parse("2026-07-02T11:55:00.522622+02:00");
    ToolCallResult original =
        ToolCallResult.builder()
            .id("call-1")
            .name("search")
            .content("result")
            .completedAt(completedAt)
            .build();

    // when
    String json = objectMapper.writeValueAsString(original);
    ToolCallResult deserialized = objectMapper.readValue(json, ToolCallResult.class);

    // then
    assertThat(deserialized.completedAt()).isEqualTo(completedAt);
  }

  @Test
  void shouldRoundTripWithoutCompletedAt() throws Exception {
    // given
    ToolCallResult original =
        ToolCallResult.builder().id("call-1").name("search").content("result").build();

    // when
    String json = objectMapper.writeValueAsString(original);
    ToolCallResult deserialized = objectMapper.readValue(json, ToolCallResult.class);

    // then
    assertThat(deserialized.completedAt()).isNull();
  }

  @Test
  void shouldDeserializeCompletedAtFromEngineBracketedZoneForm() throws Exception {
    // given: the FEEL now() string form produced by the AHSP outputElement on a real broker
    // (offset + bracketed zone id), which java.time.OffsetDateTime.parse(text) cannot parse
    String json =
        """
        {
          "id": "call-1",
          "name": "search",
          "content": "result",
          "completedAt": "2026-07-02T11:55:00.522622+02:00[Europe/Berlin]"
        }
        """;

    // when
    ToolCallResult deserialized = objectMapper.readValue(json, ToolCallResult.class);

    // then
    assertThat(deserialized.completedAt())
        .isEqualTo(OffsetDateTime.parse("2026-07-02T11:55:00.522622+02:00"));
  }
}
