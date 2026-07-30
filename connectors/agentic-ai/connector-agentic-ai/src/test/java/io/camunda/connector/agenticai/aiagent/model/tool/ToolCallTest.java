/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolCallTest {

  private final ObjectMapper objectMapper = TestObjectMapperSupplier.INSTANCE;

  @Test
  void deserializesPreMetadataJsonWithEmptyMetadata() throws Exception {
    // pre-metadata persisted JSON (no "metadata" key at all) must still deserialize; the
    // generated Jackson builder defaults the missing field to an empty map, not literal null
    // (unlike the 3-arg constructor below), but NON_EMPTY treats both the same on the wire
    final String json =
        "{\"id\": \"toolu_1\", \"name\": \"get_weather\", \"arguments\": {\"city\": \"Berlin\"}}";

    final ToolCall toolCall = objectMapper.readValue(json, ToolCall.class);

    assertThat(toolCall.id()).isEqualTo("toolu_1");
    assertThat(toolCall.name()).isEqualTo("get_weather");
    assertThat(toolCall.arguments()).containsEntry("city", "Berlin");
    assertThat(toolCall.metadata()).isNullOrEmpty();
  }

  @Test
  void threeArgConstructorDefaultsMetadataToNull() {
    assertThat(new ToolCall("id", "name", Map.of()).metadata()).isNull();
  }

  @Test
  void serializingNullMetadataOmitsTheKey() throws Exception {
    final String json =
        objectMapper.writeValueAsString(new ToolCall("toolu_1", "get_weather", Map.of()));

    assertThat(json).doesNotContain("metadata");
  }

  @Test
  void roundTripsWithMetadataPopulated() throws Exception {
    final ToolCall toolCall =
        ToolCall.builder()
            .id("toolu_1")
            .name("get_weather")
            .arguments(Map.of("city", "Berlin"))
            .metadata(Map.of("anthropic", Map.of("caller", Map.of("type", "direct"))))
            .build();

    final String json = objectMapper.writeValueAsString(toolCall);
    final ToolCall roundTripped = objectMapper.readValue(json, ToolCall.class);

    assertThat(roundTripped).isEqualTo(toolCall);
  }
}
