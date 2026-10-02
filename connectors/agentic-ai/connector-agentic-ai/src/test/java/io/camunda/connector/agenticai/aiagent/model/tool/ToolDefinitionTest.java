/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolDefinitionTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void omitsEmptyDocumentParametersFromJson() {
    final var toolDefinition =
        ToolDefinition.builder().name("tool").inputSchema(Map.of("type", "object")).build();

    final JsonNode json = objectMapper.valueToTree(toolDefinition);

    assertThat(json.has("documentParameters")).isFalse();
  }

  @Test
  void definitionWithoutDocumentParametersEqualsDefinitionReadFromLegacyJson() throws Exception {
    final var current =
        ToolDefinition.builder()
            .name("tool")
            .description("desc")
            .inputSchema(Map.of("type", "object"))
            .build();

    final var legacy =
        objectMapper.readValue(
            """
            {"name":"tool","description":"desc","inputSchema":{"type":"object"}}""",
            ToolDefinition.class);

    assertThat(legacy).isEqualTo(current);
    assertThat(legacy.documentParameters()).isEmpty();
  }

  @Test
  void roundTripsDocumentParameters() throws Exception {
    final var toolDefinition =
        ToolDefinition.builder()
            .name("tool")
            .inputSchema(Map.of("type", "object"))
            .documentParameters(List.of("/properties/doc", "/properties/docs/items"))
            .build();

    final var json = objectMapper.writeValueAsString(toolDefinition);
    final var restored = objectMapper.readValue(json, ToolDefinition.class);

    assertThat(json).contains("\"documentParameters\"");
    assertThat(restored).isEqualTo(toolDefinition);
  }
}
