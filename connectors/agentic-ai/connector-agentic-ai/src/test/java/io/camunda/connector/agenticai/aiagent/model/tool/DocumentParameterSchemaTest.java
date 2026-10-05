/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema.Step;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentParameterSchemaTest {

  private static final String HINT = DocumentParameterSchema.PROVIDER_HINT;

  @Test
  void findsTopLevelArrayAndNestedDocumentParameters() {
    final var schema =
        object(
            "doc", Map.of("type", "document", "description", "A document"),
            "docs", Map.of("type", "array", "items", Map.of("type", "document")),
            "req", object("file", Map.of("type", "document"), "note", Map.of("type", "string")),
            "rows", Map.of("type", "array", "items", object("file", Map.of("type", "document"))));

    assertThat(DocumentParameterSchema.hasDocumentParameters(schema)).isTrue();
    assertThat(DocumentParameterSchema.documentParameterPaths(schema))
        .containsExactly(
            List.of(new Step.Property("doc")),
            List.of(new Step.Property("docs"), new Step.Items()),
            List.of(new Step.Property("req"), new Step.Property("file")),
            List.of(new Step.Property("rows"), new Step.Items(), new Step.Property("file")));
  }

  @Test
  void findsNoDocumentParameterInASchemaWithoutDocumentType() {
    final var schema =
        object(
            "text", Map.of("type", "string"),
            "type", Map.of("type", "string", "description", "a property named type"));

    assertThat(DocumentParameterSchema.hasDocumentParameters(schema)).isFalse();
    assertThat(DocumentParameterSchema.documentParameterPaths(schema)).isEmpty();
  }

  @Test
  void doesNotLookIntoAnyOf() {
    final var schema =
        object("doc", Map.of("anyOf", List.of(Map.of("type", "document"), Map.of("type", "null"))));

    assertThat(DocumentParameterSchema.hasDocumentParameters(schema)).isFalse();
  }

  @Test
  void rewritesDocumentNodesToStringSchemasWithTheHint() {
    final var definition =
        new ToolDefinition(
            "upload",
            "Uploads",
            object(
                "doc", Map.of("type", "document", "description", "A document"),
                "bare", Map.of("type", "document"),
                "docs", Map.of("type", "array", "description", "Many", "items", doc()),
                "req", object("file", doc(), "note", Map.of("type", "string"))));

    final var rewritten =
        DocumentParameterSchema.toProviderToolDefinitions(List.of(definition)).getFirst();

    assertThat(rewritten.name()).isEqualTo("upload");
    assertThat(rewritten.description()).isEqualTo("Uploads");
    assertThat(rewritten.inputSchema())
        .isEqualTo(
            object(
                "doc", Map.of("type", "string", "description", "A document " + HINT),
                "bare", Map.of("type", "string", "description", HINT),
                "docs",
                    Map.of(
                        "type",
                        "array",
                        "description",
                        "Many",
                        "items",
                        Map.of("type", "string", "description", HINT)),
                "req",
                    object(
                        "file", Map.of("type", "string", "description", HINT),
                        "note", Map.of("type", "string"))));
  }

  @Test
  void doesNotModifyTheAuthoredDefinitionAndKeepsTheOrderOfTheSchema() {
    final var authored =
        new ToolDefinition(
            "upload",
            null,
            object("b", doc(), "a", Map.of("type", "string"), "c", Map.of("type", "string")));

    final var rewritten =
        DocumentParameterSchema.toProviderToolDefinitions(List.of(authored)).getFirst();

    assertThat(authored.inputSchema()).isEqualTo(object("b", doc(), "a", string(), "c", string()));
    assertThat(propertyNames(rewritten.inputSchema())).containsExactly("b", "a", "c");
  }

  @Test
  void returnsDefinitionsWithoutDocumentParametersUnchanged() {
    final var plain = new ToolDefinition("plain", null, object("text", string()));

    final var result = DocumentParameterSchema.toProviderToolDefinitions(List.of(plain));

    assertThat(result.getFirst()).isSameAs(plain);
  }

  @Test
  void neverLeavesADocumentTypeInTheRewrittenSchema() {
    final var definition =
        new ToolDefinition(
            "upload",
            null,
            object("docs", Map.of("type", "array", "items", object("file", doc())), "doc", doc()));

    final var rewritten =
        DocumentParameterSchema.toProviderToolDefinitions(List.of(definition)).getFirst();

    assertThat(DocumentParameterSchema.hasDocumentParameters(rewritten.inputSchema())).isFalse();
    assertThat(rewritten.inputSchema().toString()).doesNotContain("type=document");
  }

  private static Map<String, Object> doc() {
    return Map.of("type", "document");
  }

  private static Map<String, Object> string() {
    return Map.of("type", "string");
  }

  @SuppressWarnings("unchecked")
  private static List<String> propertyNames(Map<String, Object> schema) {
    return List.copyOf(((Map<String, Object>) schema.get("properties")).keySet());
  }

  /** An object schema from alternating property names and schemas, in declaration order. */
  private static Map<String, Object> object(Object... namesAndSchemas) {
    final var properties = new LinkedHashMap<String, Object>();
    for (int i = 0; i < namesAndSchemas.length; i += 2) {
      properties.put((String) namesAndSchemas[i], namesAndSchemas[i + 1]);
    }
    final var schema = new LinkedHashMap<String, Object>();
    schema.put("type", "object");
    schema.put("properties", properties);
    return schema;
  }
}
