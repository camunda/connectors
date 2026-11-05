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

  @Test
  void aDocumentIsAStringWithTheDocumentFormatAndTheDescription() {
    final var schema = DocumentSchemas.document("The file");

    assertThat(schema)
        .isEqualTo(
            Map.of("type", "string", "format", "camunda-document-id", "description", "The file"));
    assertThat(List.copyOf(schema.keySet())).containsExactly("type", "format", "description");
  }

  @Test
  void aDocumentWithoutAUsableDescriptionHasNone() {
    final var expected = Map.of("type", "string", "format", "camunda-document-id");

    assertThat(DocumentSchemas.document(null)).isEqualTo(expected);
    assertThat(DocumentSchemas.document("  ")).isEqualTo(expected);
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
                List.of(
                    Map.of("type", "string"),
                    Map.of("type", "string", "format", "camunda-document-id"))));
    assertThat(List.copyOf(schema.keySet())).containsExactly("description", "anyOf");
    assertThat(DocumentSchemas.textOrDocument(null))
        .isEqualTo(
            Map.of(
                "anyOf",
                List.of(
                    Map.of("type", "string"),
                    Map.of("type", "string", "format", "camunda-document-id"))));
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
