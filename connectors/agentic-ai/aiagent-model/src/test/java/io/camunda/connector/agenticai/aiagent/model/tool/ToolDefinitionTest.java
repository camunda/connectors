/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolDefinitionTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final Map<String, Object> HANDLE_SCHEMA =
      Map.of(
          "type",
          "object",
          "properties",
          Map.of(
              "type", Map.of("type", "string", "enum", List.of("camunda.agent_document")),
              "id", Map.of("type", "string")),
          "required",
          List.of("type", "id"));

  @Test
  void roundTripsDocumentParametersWithTheHandleSchema() throws Exception {
    final var toolDefinition =
        ToolDefinition.builder()
            .name("tool")
            .inputSchema(
                Map.of(
                    "type",
                    "object",
                    "properties",
                    Map.of(
                        "doc",
                        HANDLE_SCHEMA,
                        "docs",
                        Map.of("type", "array", "items", HANDLE_SCHEMA))))
            .build();

    final var json = objectMapper.writeValueAsString(toolDefinition);
    final var restored = objectMapper.readValue(json, ToolDefinition.class);

    final var handle =
        """
        {
          "type": "object",
          "properties": {
            "type": {"type": "string", "enum": ["camunda.agent_document"]},
            "id": {"type": "string"}
          },
          "required": ["type", "id"]
        }
        """;
    assertThat(objectMapper.readTree(json))
        .isEqualTo(
            objectMapper.readTree(
                """
                {
                  "name": "tool",
                  "inputSchema": {
                    "type": "object",
                    "properties": {
                      "doc": %s,
                      "docs": {"type": "array", "items": %s}
                    }
                  }
                }
                """
                    .formatted(handle, handle)));
    assertThat(restored).isEqualTo(toolDefinition);
    assertThat(restored.documentParameters())
        .containsExactlyInAnyOrder(
            new DocumentParameter.Document(List.of(new DocumentParameter.Step.Property("doc"))),
            new DocumentParameter.Document(
                List.of(
                    new DocumentParameter.Step.Property("docs"),
                    new DocumentParameter.Step.Items())));
  }
}
