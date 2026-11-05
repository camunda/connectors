/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolDefinitionTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void roundTripsDocumentParametersWithTheDocumentFormat() throws Exception {
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
                        Map.of(
                            "type",
                            "string",
                            "format",
                            "camunda-document-id",
                            "description",
                            "A document"),
                        "docs",
                        Map.of(
                            "type",
                            "array",
                            "items",
                            Map.of("type", "string", "format", "camunda-document-id")))))
            .build();

    final var json = objectMapper.writeValueAsString(toolDefinition);
    final var restored = objectMapper.readValue(json, ToolDefinition.class);

    assertThat(objectMapper.readTree(json))
        .isEqualTo(
            objectMapper.readTree(
                """
                {
                  "name": "tool",
                  "inputSchema": {
                    "type": "object",
                    "properties": {
                      "doc": {
                        "type": "string",
                        "format": "camunda-document-id",
                        "description": "A document"
                      },
                      "docs": {
                        "type": "array",
                        "items": {"type": "string", "format": "camunda-document-id"}
                      }
                    }
                  }
                }
                """));
    assertThat(restored).isEqualTo(toolDefinition);
    assertThat(restored.documentParameters()).isEqualTo(toolDefinition.documentParameters());
  }
}
