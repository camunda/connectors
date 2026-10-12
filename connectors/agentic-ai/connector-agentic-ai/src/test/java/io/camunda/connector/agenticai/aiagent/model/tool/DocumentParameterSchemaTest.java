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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentParameterSchemaTest {

  private static final String HINT = DocumentParameterSchema.HINT;

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
    assertThat(DocumentParameterSchema.documentParameterPaths(schema))
        .containsExactly(
            List.of(new Step.Property("doc")),
            List.of(new Step.Property("docs"), new Step.Items()),
            List.of(new Step.Property("req"), new Step.Property("file")),
            List.of(new Step.Property("rows"), new Step.Items(), new Step.Property("file")));
  }

  @Test
  void findsNoDocumentParameterInASchemaWithoutDocumentFormat() {
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
        object(
            "doc",
            Map.of(
                "anyOf",
                List.of(
                    Map.of("type", "string", "format", "camunda-document-id"),
                    Map.of("type", "null"))));

    assertThat(DocumentParameterSchema.hasDocumentParameters(schema)).isFalse();
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
  void leavesTheDocumentDeliveryOutOfWhatTheProviderGets() {
    final var plain =
        new ToolDefinition("plain", null, object("text", string()), DocumentDelivery.INLINE);
    final var withDocument =
        new ToolDefinition(
            "withDocument",
            null,
            object("doc", Map.of("type", "string", "format", "camunda-document-id")),
            DocumentDelivery.INLINE);

    final var result =
        DocumentParameterSchema.toProviderToolDefinitions(List.of(plain, withDocument));

    assertThat(result).extracting(ToolDefinition::documentDelivery).containsExactly(null, null);
    assertThat(result.getFirst().inputSchema()).isEqualTo(plain.inputSchema());
    // the stored definitions keep it
    assertThat(plain.documentDelivery()).isEqualTo(DocumentDelivery.INLINE);
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

  @Test
  void withHintAppliesTheHintOnlyOnce() {
    final var schema = object("doc", doc());

    final var once = DocumentParameterSchema.withHint(schema);
    final var twice = DocumentParameterSchema.withHint(once);

    assertThat(twice).isEqualTo(once);
    assertThat(once.toString()).containsOnlyOnce(HINT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"A document.", "A document!", "A document?", "A document.  \n"})
  void withHintSeparatesTheHintWithASpaceWhenTheDescriptionEndsInSentencePunctuation(
      String description) {
    final var result = DocumentParameterSchema.withHint(object("doc", described(description)));

    assertThat(result)
        .isEqualTo(object("doc", described(description.stripTrailing() + " " + HINT)));
  }

  @Test
  void withHintAddsAPeriodWhenTheDescriptionHasNoFinalPunctuation() {
    final var result = DocumentParameterSchema.withHint(object("doc", described("A document")));

    assertThat(result).isEqualTo(object("doc", described("A document. " + HINT)));
  }

  @Test
  void withHintIgnoresTrailingWhitespaceOfTheDescription() {
    final var result = DocumentParameterSchema.withHint(object("doc", described("A document \n")));

    assertThat(result).isEqualTo(object("doc", described("A document. " + HINT)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void withHintUsesOnlyTheHintForABlankDescription(String description) {
    final var result = DocumentParameterSchema.withHint(object("doc", described(description)));

    assertThat(result).isEqualTo(object("doc", described(HINT)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"A document", "A document.", "A document!"})
  void withHintIsIdempotentForEveryKindOfDescription(String description) {
    final var once = DocumentParameterSchema.withHint(object("doc", described(description)));

    assertThat(DocumentParameterSchema.withHint(once)).isEqualTo(once);
  }

  @Test
  void withHintReturnsASchemaWithoutDocumentParametersAsIs() {
    final var schema = object("text", string());

    assertThat(DocumentParameterSchema.withHint(schema)).isSameAs(schema);
  }

  @Test
  void aToolDefinitionStoresTheHintInTheDescriptionOfItsDocumentParameters() {
    final var definition = new ToolDefinition("upload", null, object("doc", doc()));

    assertThat(definition.inputSchema()).isEqualTo(object("doc", described(HINT)));
    assertThat(new ToolDefinition("upload", null, definition.inputSchema())).isEqualTo(definition);
  }

  @Test
  void hintAsksForTheIdOfAKnownDocument() {
    assertThat(HINT)
        .isEqualTo(
            "Pass the id of a known document (the id attribute of its <doc /> tag) or the tag itself.");
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
