/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema.Step;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DocumentParameterSchemaTest {

  private static final String HINT = DocumentParameterSchema.HINT;

  private static DocumentParameter document(Step... path) {
    return new DocumentParameter(List.of(path));
  }

  @Test
  void findsTopLevelArrayAndNestedDocumentParameters() {
    final var schema =
        object(
            "doc",
                Map.of(
                    "type", "string", "format", "camunda-document-id", "description", "A document"),
            "docs",
                Map.of(
                    "type",
                    "array",
                    "items",
                    Map.of("type", "string", "format", "camunda-document-id")),
            "req",
                object(
                    "file",
                    Map.of("type", "string", "format", "camunda-document-id"),
                    "note",
                    Map.of("type", "string")),
            "rows",
                Map.of(
                    "type",
                    "array",
                    "items",
                    object("file", Map.of("type", "string", "format", "camunda-document-id"))));

    assertThat(DocumentParameterSchema.hasDocumentParameters(schema)).isTrue();
    assertThat(DocumentParameterSchema.documentParameters(schema))
        .containsExactly(
            document(new Step.Property("doc")),
            document(new Step.Property("docs"), new Step.Items()),
            document(new Step.Property("req"), new Step.Property("file")),
            document(new Step.Property("rows"), new Step.Items(), new Step.Property("file")));
  }

  @Test
  void findsNoDocumentParameterInASchemaWithoutDocumentFormat() {
    final var schema =
        object(
            "text", Map.of("type", "string"),
            "type", Map.of("type", "string", "description", "a property named type"));

    assertThat(DocumentParameterSchema.hasDocumentParameters(schema)).isFalse();
    assertThat(DocumentParameterSchema.documentParameters(schema)).isEmpty();
  }

  @Test
  void rewritesDocumentNodesToStringSchemasWithoutTheFormat() {
    final var definition =
        new ToolDefinition(
            "upload",
            "Uploads",
            object(
                "doc",
                    Map.of(
                        "type",
                        "string",
                        "format",
                        "camunda-document-id",
                        "description",
                        "A document"),
                "bare", Map.of("type", "string", "format", "camunda-document-id"),
                "docs", Map.of("type", "array", "description", "Many", "items", doc()),
                "req", object("file", doc(), "note", Map.of("type", "string"))));

    final var rewritten =
        DocumentParameterSchema.toProviderToolDefinitions(List.of(definition)).getFirst();

    assertThat(rewritten.name()).isEqualTo("upload");
    assertThat(rewritten.description()).isEqualTo("Uploads");
    assertThat(rewritten.inputSchema())
        .isEqualTo(
            object(
                "doc", Map.of("type", "string", "description", "A document. " + HINT),
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
  void doesNotModifyTheStoredDefinitionAndKeepsTheOrderOfTheSchema() {
    final var authored =
        new ToolDefinition(
            "upload",
            null,
            object("b", doc(), "a", Map.of("type", "string"), "c", Map.of("type", "string")));
    final var authoredSchema = authored.inputSchema();

    final var rewritten =
        DocumentParameterSchema.toProviderToolDefinitions(List.of(authored)).getFirst();

    assertThat(authored.inputSchema()).isSameAs(authoredSchema);
    assertThat(authored.inputSchema().toString()).contains("camunda-document-id");
    assertThat(propertyNames(rewritten.inputSchema())).containsExactly("b", "a", "c");
  }

  @Test
  void returnsDefinitionsWithoutDocumentParametersUnchanged() {
    final var plain = new ToolDefinition("plain", null, object("text", string()));

    final var result = DocumentParameterSchema.toProviderToolDefinitions(List.of(plain));

    assertThat(result.getFirst()).isSameAs(plain);
  }

  @Test
  void aTypeDocumentNodeIsNotADocumentParameter() {
    assertThat(
            DocumentParameterSchema.hasDocumentParameters(
                object("doc", Map.of("type", "document"))))
        .isFalse();
  }

  @Test
  void withHintEndsTheDescriptionOfEveryDocumentParameterWithTheHint() {
    final var schema =
        object(
            "doc",
                Map.of(
                    "type", "string", "format", "camunda-document-id", "description", "A document"),
            "bare", doc(),
            "docs", Map.of("type", "array", "description", "Many", "items", doc()),
            "req", object("file", doc(), "note", string()),
            "rows", Map.of("type", "array", "items", object("file", doc())));

    assertThat(DocumentParameterSchema.withHint(schema))
        .isEqualTo(
            object(
                "doc", described("A document. " + HINT),
                "bare", described(HINT),
                "docs", Map.of("type", "array", "description", "Many", "items", described(HINT)),
                "req", object("file", described(HINT), "note", string()),
                "rows", Map.of("type", "array", "items", object("file", described(HINT)))));
  }

  static Stream<Arguments> descriptionsOfADocumentParameter() {
    return Stream.of(
        Arguments.of("A document.", "A document. " + HINT),
        Arguments.of("A document!", "A document! " + HINT),
        Arguments.of("A document?", "A document? " + HINT),
        Arguments.of("A document.  \n", "A document. " + HINT),
        Arguments.of("A document", "A document. " + HINT),
        Arguments.of("A document \n", "A document. " + HINT),
        Arguments.of("", HINT),
        Arguments.of("   ", HINT));
  }

  @ParameterizedTest
  @MethodSource("descriptionsOfADocumentParameter")
  void withHintEndsEveryDescriptionWithTheHintOnce(String description, String expected) {
    final var once = DocumentParameterSchema.withHint(object("doc", described(description)));

    assertThat(once).isEqualTo(object("doc", described(expected)));
    assertThat(once.toString()).containsOnlyOnce(HINT);
    assertThat(DocumentParameterSchema.withHint(once)).isEqualTo(once);
  }

  @Test
  void withHintReturnsASchemaWithoutDocumentParametersAsIs() {
    final var schema = object("text", string());

    assertThat(DocumentParameterSchema.withHint(schema)).isSameAs(schema);
  }

  @Test
  void hintAsksForTheTagOfAKnownDocument() {
    assertThat(HINT)
        .isEqualTo(
            "Pass the tag of a known document exactly as you read it, for example <doc id=\"...\" />.");
  }

  @Test
  void normalizesTheShorthandTypeToTheDocumentFormat() {
    final var schema =
        object(
            "doc", Map.of("type", "document", "description", "A document"),
            "docs", Map.of("type", "array", "items", Map.of("type", "document")),
            "req", object("file", Map.of("type", "document")));

    assertThat(DocumentParameterSchema.normalizeAuthoredSchema(schema))
        .isEqualTo(
            object(
                "doc",
                    Map.of(
                        "type",
                        "string",
                        "format",
                        "camunda-document-id",
                        "description",
                        "A document"),
                "docs", Map.of("type", "array", "items", doc()),
                "req", object("file", doc())));
  }

  @Test
  void neverLeavesADocumentFormatInTheRewrittenSchema() {
    final var definition =
        new ToolDefinition(
            "upload",
            null,
            object("docs", Map.of("type", "array", "items", object("file", doc())), "doc", doc()));

    final var rewritten =
        DocumentParameterSchema.toProviderToolDefinitions(List.of(definition)).getFirst();

    assertThat(DocumentParameterSchema.hasDocumentParameters(rewritten.inputSchema())).isFalse();
    assertThat(rewritten.inputSchema().toString()).doesNotContain("camunda-document-id");
  }

  private static Map<String, Object> doc() {
    return Map.of("type", "string", "format", "camunda-document-id");
  }

  private static Map<String, Object> described(String description) {
    return Map.of("type", "string", "format", "camunda-document-id", "description", description);
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
