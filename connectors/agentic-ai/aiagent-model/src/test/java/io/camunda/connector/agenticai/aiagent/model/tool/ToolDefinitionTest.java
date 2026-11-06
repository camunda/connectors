/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
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
              "type", Map.of("type", "string", "enum", List.of("camunda.agenticai.document")),
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
            "type": {"type": "string", "enum": ["camunda.agenticai.document"]},
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
            new DocumentParameter(List.of(new DocumentParameter.Step.Property("doc"))),
            new DocumentParameter(
                List.of(
                    new DocumentParameter.Step.Property("docs"),
                    new DocumentParameter.Step.Items())));
  }

  @Test
  void roundTripsTheDocumentDeliveryAndOmitsItWhenAbsent() throws Exception {
    final var content =
        ToolDefinition.builder()
            .name("tool")
            .inputSchema(Map.of("type", "object"))
            .documentDelivery(DocumentDelivery.CONTENT)
            .build();
    final var byReference =
        ToolDefinition.builder().name("tool").inputSchema(Map.of("type", "object")).build();

    final var contentJson = objectMapper.writeValueAsString(content);
    final var referenceJson = objectMapper.writeValueAsString(byReference);

    assertThat(contentJson).contains("\"documentDelivery\":\"content\"");
    assertThat(objectMapper.readValue(contentJson, ToolDefinition.class)).isEqualTo(content);
    assertThat(referenceJson).doesNotContain("documentDelivery");
    assertThat(objectMapper.readValue(referenceJson, ToolDefinition.class).documentDelivery())
        .isNull();
  }

  @Test
  void readsAStoredDefinitionWithoutADocumentDeliveryAsByReference() throws Exception {
    final var restored =
        objectMapper.readValue(
            "{\"name\":\"tool\",\"inputSchema\":{\"type\":\"object\"}}", ToolDefinition.class);

    assertThat(restored.documentDelivery()).isNull();
  }

  @Test
  void withoutDocumentDeliveryDropsTheDeliveryAndKeepsTheRest() {
    final var content =
        ToolDefinition.runtime("tool", "does it", Map.of("type", "object"))
            .withDocumentDelivery(DocumentDelivery.CONTENT);

    final var stripped = content.withoutDocumentDelivery();

    assertThat(stripped.documentDelivery()).isNull();
    assertThat(stripped).isEqualTo(content.withDocumentDelivery(null));
    assertThat(stripped.kind()).isEqualTo(ToolKind.RUNTIME);
    assertThat(stripped.withoutDocumentDelivery()).isSameAs(stripped);
  }
}
