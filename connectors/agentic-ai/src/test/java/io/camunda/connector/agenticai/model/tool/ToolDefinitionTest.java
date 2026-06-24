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

class ToolDefinitionTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldRoundTripWithMetadata() throws Exception {
    // given
    final var original =
        ToolDefinition.builder()
            .name("my_tool")
            .description("A test tool")
            .inputSchema(Map.of("type", "object"))
            .metadata(Map.of("sandbox", true, "elementId", "Sandbox_1"))
            .build();

    // when
    final var json = objectMapper.writeValueAsString(original);
    final var deserialized = objectMapper.readValue(json, ToolDefinition.class);

    // then
    assertThat(deserialized.name()).isEqualTo("my_tool");
    assertThat(deserialized.description()).isEqualTo("A test tool");
    assertThat(deserialized.inputSchema()).isEqualTo(Map.of("type", "object"));
    assertThat(deserialized.metadata())
        .containsEntry("sandbox", true)
        .containsEntry("elementId", "Sandbox_1");
  }

  @Test
  void shouldOmitMetadataFromJsonWhenEmpty() throws Exception {
    // given
    final var original =
        ToolDefinition.builder()
            .name("plain_tool")
            .description("A plain tool")
            .inputSchema(Map.of("type", "object"))
            .build();

    // when
    final var json = objectMapper.writeValueAsString(original);

    // then — the "metadata" JSON key must not appear when metadata is empty (NON_EMPTY)
    assertThat(json).doesNotContain("\"metadata\"");
  }

  @Test
  void shouldDeserializeWithoutMetadataFieldToEmptyMap() throws Exception {
    // given — JSON produced by an older version without a metadata field
    final var json =
        "{\"name\":\"legacy_tool\",\"description\":\"Legacy\",\"inputSchema\":{\"type\":\"object\"}}";

    // when
    final var deserialized = objectMapper.readValue(json, ToolDefinition.class);

    // then
    assertThat(deserialized.name()).isEqualTo("legacy_tool");
    assertThat(deserialized.metadata()).isEmpty();
  }

  @Test
  void gatewayTypeReturnsSandboxWhenMetadataKeySet() {
    final var def =
        ToolDefinition.builder()
            .name("sandbox_bash")
            .inputSchema(Map.of())
            .metadata(Map.of(ToolDefinition.METADATA_GATEWAY_TYPE, "sandbox"))
            .build();
    assertThat(def.gatewayType()).isEqualTo("sandbox");
  }

  @Test
  void gatewayTypeReturnsNullWhenMetadataEmpty() {
    final var def = ToolDefinition.builder().name("regular_tool").inputSchema(Map.of()).build();
    assertThat(def.gatewayType()).isNull();
  }

  @Test
  void gatewayTypeReturnsNullWhenKeyAbsent() {
    final var def =
        ToolDefinition.builder()
            .name("not_sandbox")
            .inputSchema(Map.of())
            .metadata(Map.of("someOtherKey", "someValue"))
            .build();
    assertThat(def.gatewayType()).isNull();
  }
}
