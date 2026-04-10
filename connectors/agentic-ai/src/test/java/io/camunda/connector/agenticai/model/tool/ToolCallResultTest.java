/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    assertThat(deserialized.properties())
        .containsEntry("interrupted", true)
        .containsEntry("custom", "value");
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
    assertThat(deserialized.properties()).containsEntry(ToolCallResult.PROPERTY_INTERRUPTED, true);
  }
}
