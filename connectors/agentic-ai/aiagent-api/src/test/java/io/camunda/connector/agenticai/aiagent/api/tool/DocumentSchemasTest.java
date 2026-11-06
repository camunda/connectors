/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentSchemasTest {

  private static Map<String, Object> handleSchema(String description) {
    final var expected = new LinkedHashMap<String, Object>();
    expected.put("type", "object");
    if (description != null) {
      expected.put("description", description);
    }
    expected.put(
        "properties",
        Map.of(
            "type",
            Map.of("type", "string", "enum", List.of("camunda.agenticai.document")),
            "id",
            Map.of("type", "string", "description", "The id of the document.")));
    expected.put("required", List.of("type", "id"));
    return expected;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> properties(Map<String, Object> schema) {
    return (Map<String, Object>) schema.get("properties");
  }

  @Test
  void aDocumentIsTheObjectSchemaOfAHandleWithTheDescription() {
    final var schema = DocumentSchemas.document("The file");

    assertThat(schema).isEqualTo(handleSchema("The file"));
    assertThat(List.copyOf(schema.keySet()))
        .containsExactly("type", "description", "properties", "required");
    assertThat(List.copyOf(properties(schema).keySet())).containsExactly("type", "id");
  }

  @Test
  void aDocumentWithoutAUsableDescriptionHasNone() {
    final var schema = DocumentSchemas.document(null);

    assertThat(schema).isEqualTo(handleSchema(null));
    assertThat(List.copyOf(schema.keySet())).containsExactly("type", "properties", "required");
    assertThat(DocumentSchemas.document("  ")).isEqualTo(handleSchema(null));
  }

  @Test
  void aDocumentSchemaHasNoFormatAndNoAdditionalPropertiesRule() {
    assertThat(DocumentSchemas.document("The file"))
        .doesNotContainKeys("format", "additionalProperties");
  }

  @Test
  void textOrADocumentIsAnAnyOfOfAPlainStringAndTheDocumentSchema() {
    final var schema = DocumentSchemas.textOrDocument("The body");

    assertThat(schema)
        .isEqualTo(
            Map.of(
                "description",
                "The body",
                "anyOf",
                List.of(Map.of("type", "string"), handleSchema(null))));
    assertThat(List.copyOf(schema.keySet())).containsExactly("description", "anyOf");
    assertThat(DocumentSchemas.textOrDocument(null))
        .isEqualTo(Map.of("anyOf", List.of(Map.of("type", "string"), handleSchema(null))));
  }

  @Test
  void theSchemasAreTheDocumentParametersOfAToolDefinition() {
    final var properties = new LinkedHashMap<String, Object>();
    properties.put("file", DocumentSchemas.document("The file"));
    properties.put("body", DocumentSchemas.textOrDocument("The body"));
    final var definition =
        ToolDefinition.runtime("upload", null, Map.of("type", "object", "properties", properties));

    assertThat(definition.documentParameters())
        .containsExactly(
            new DocumentParameter.Document(List.of(new DocumentParameter.Step.Property("file"))),
            new DocumentParameter.TextOrDocument(
                List.of(new DocumentParameter.Step.Property("body"))));
  }
}
