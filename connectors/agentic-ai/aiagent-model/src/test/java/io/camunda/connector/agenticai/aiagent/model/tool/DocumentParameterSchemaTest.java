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

  private static final String UNION_HINT = DocumentParameterSchema.UNION_HINT;

  private static DocumentParameter document(Step... path) {
    return new DocumentParameter(List.of(path), false);
  }

  private static DocumentParameter union(Step... path) {
    return new DocumentParameter(List.of(path), true);
  }

  /** An object schema with the property {@code body}: a union with the given description. */
  private static Map<String, Object> union(String description) {
    return object("body", Map.of("anyOf", List.of(string(), doc()), "description", description));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> valueOf(Map<String, Object> schema, String property) {
    return (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get(property);
  }

  private static Map<String, Object> valueOf(ToolDefinition definition, String property) {
    return valueOf(definition.inputSchema(), property);
  }

  private static Map<String, Object> bodyOf(ToolDefinition definition) {
    return valueOf(definition, "body");
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
  void findsUnionParametersAtAnyDepth() {
    final var schema =
        object(
            "text", Map.of("anyOf", List.of(string(), doc())),
            "either", Map.of("oneOf", List.of(Map.of("type", "number"), doc(), string())),
            "items", Map.of("type", "array", "items", Map.of("anyOf", List.of(doc(), string()))),
            "req", object("file", Map.of("anyOf", List.of(string(), doc())), "plain", string()),
            "rows",
                Map.of(
                    "type",
                    "array",
                    "items",
                    object("file", Map.of("oneOf", List.of(string(), doc())))),
            "pure", doc());

    assertThat(DocumentParameterSchema.hasDocumentParameters(schema)).isTrue();
    assertThat(DocumentParameterSchema.documentParameters(schema))
        .containsExactly(
            union(new Step.Property("text")),
            union(new Step.Property("either")),
            union(new Step.Property("items"), new Step.Items()),
            union(new Step.Property("req"), new Step.Property("file")),
            union(new Step.Property("rows"), new Step.Items(), new Step.Property("file")),
            document(new Step.Property("pure")));
  }

  @Test
  void aNodeWithAlternativesIsAUnionParameterOnlyWithADocumentAlternativeAndAnotherOne() {
    assertThat(DocumentParameterSchema.isUnionParameter(Map.of("anyOf", List.of(string(), doc()))))
        .isTrue();
    assertThat(
            DocumentParameterSchema.isUnionParameter(
                Map.of("oneOf", List.of(doc(), Map.of("type", "null")))))
        .isTrue();
    // no document alternative
    assertThat(
            DocumentParameterSchema.isUnionParameter(
                Map.of("anyOf", List.of(string(), Map.of("type", "number")))))
        .isFalse();
    // no other alternative
    assertThat(DocumentParameterSchema.isUnionParameter(Map.of("anyOf", List.of(doc())))).isFalse();
    // a document node is not a union
    assertThat(DocumentParameterSchema.isUnionParameter(doc())).isFalse();
    assertThat(
            DocumentParameterSchema.hasDocumentParameters(
                object("doc", Map.of("anyOf", List.of(doc())))))
        .isFalse();
  }

  @Test
  void doesNotLookIntoTheAlternativesOfAUnionParameter() {
    final var schema =
        object(
            "doc",
            Map.of(
                "anyOf",
                List.of(string(), Map.of("type", "array", "items", doc()), object("file", doc()))));

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
        new ToolDefinition("plain", null, object("text", string()), DocumentDelivery.CONTENT);
    final var withDocument =
        new ToolDefinition(
            "withDocument",
            null,
            object("doc", Map.of("type", "string", "format", "camunda-document-id")),
            DocumentDelivery.CONTENT);

    final var result =
        DocumentParameterSchema.toProviderToolDefinitions(List.of(plain, withDocument));

    assertThat(result).extracting(ToolDefinition::documentDelivery).containsExactly(null, null);
    assertThat(result.getFirst().inputSchema()).isEqualTo(plain.inputSchema());
    // the stored definitions keep it
    assertThat(plain.documentDelivery()).isEqualTo(DocumentDelivery.CONTENT);
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

  // --- union parameters: provider schema

  @Test
  void aUnionOfTextAndADocumentIsAPlainStringForTheProviderWithTheDescriptionOfTheNode() {
    final var definition =
        new ToolDefinition(
            "send",
            null,
            object("body", Map.of("anyOf", List.of(string(), doc()), "description", "The body")));

    final var provider =
        DocumentParameterSchema.toProviderToolDefinitions(List.of(definition)).getFirst();

    assertThat(provider.inputSchema())
        .isEqualTo(
            object("body", Map.of("type", "string", "description", "The body. " + UNION_HINT)));
    assertThat(bodyOf(provider)).containsOnlyKeys("type", "description");
    assertThat(DocumentParameterSchema.hasDocumentParameters(provider.inputSchema())).isFalse();
  }

  @Test
  void aUnionNodeWithATypeKeepsItsOwnTypeAndTheOrderOfItsEntries() {
    final var body = new LinkedHashMap<String, Object>();
    body.put("description", "The body");
    body.put("anyOf", List.of(string(), doc()));
    body.put("type", "string");
    body.put("title", "Body");
    final var definition = new ToolDefinition("send", null, object("body", body));

    final var provider =
        DocumentParameterSchema.toProviderToolDefinitions(List.of(definition)).getFirst();

    assertThat(List.copyOf(bodyOf(provider).keySet()))
        .containsExactly("description", "type", "title");
    assertThat(bodyOf(provider)).containsEntry("type", "string").containsEntry("title", "Body");
  }

  @Test
  void theEntriesOfTheRemainingAlternativeTakeThePlaceOfTheKeywordAndTheNodeWins() {
    final var body = new LinkedHashMap<String, Object>();
    body.put("title", "Body");
    body.put(
        "oneOf",
        List.of(
            ordered("type", "string", "maxLength", 9),
            ordered("type", "string", "format", "camunda-document-id", "maxLength", 9)));
    body.put("description", "The body");
    final var definition = new ToolDefinition("send", null, object("body", body));

    final var provider =
        DocumentParameterSchema.toProviderToolDefinitions(List.of(definition)).getFirst();

    assertThat(bodyOf(provider)).doesNotContainKey("oneOf");
    assertThat(List.copyOf(bodyOf(provider).keySet()))
        .containsExactly("title", "type", "maxLength", "description");
    assertThat(bodyOf(provider))
        .containsEntry("type", "string")
        .containsEntry("maxLength", 9)
        .containsEntry("description", "The body. " + UNION_HINT);
  }

  @Test
  void alternativesThatDifferAfterTheRewriteAreKept() {
    final var body = new LinkedHashMap<String, Object>();
    body.put("anyOf", List.of(Map.of("type", "string", "maxLength", 9), doc()));
    final var definition = new ToolDefinition("send", null, object("body", body));

    final var provider =
        DocumentParameterSchema.toProviderToolDefinitions(List.of(definition)).getFirst();

    assertThat(bodyOf(provider).get("anyOf"))
        .isEqualTo(List.of(Map.of("type", "string", "maxLength", 9), string()));
  }

  @Test
  void identicalAlternativesAreMergedAndASingleOneIsUnwrappedInPlaceOfTheKeyword() {
    final var body = new LinkedHashMap<String, Object>();
    body.put("title", "Body");
    body.put("anyOf", List.of(string(), doc(), string()));
    body.put("description", "The body");
    final var definition = new ToolDefinition("send", null, object("body", body));

    final var provider =
        DocumentParameterSchema.toProviderToolDefinitions(List.of(definition)).getFirst();

    assertThat(bodyOf(provider)).doesNotContainKey("anyOf");
    assertThat(List.copyOf(bodyOf(provider).keySet()))
        .containsExactly("title", "type", "description");
    assertThat(bodyOf(provider))
        .containsEntry("type", "string")
        .containsEntry("description", "The body. " + UNION_HINT);
  }

  @Test
  void aUnionWithOtherTypesKeepsTheKeywordAndTheOrderOfItsAlternatives() {
    final var definition =
        new ToolDefinition(
            "send",
            null,
            object(
                "value", Map.of("anyOf", List.of(Map.of("type", "number"), doc(), string())),
                "other", Map.of("oneOf", List.of(doc(), Map.of("type", "null")))));

    final var provider =
        DocumentParameterSchema.toProviderToolDefinitions(List.of(definition)).getFirst();

    assertThat(valueOf(provider, "value").get("anyOf"))
        .isEqualTo(List.of(Map.of("type", "number"), string()));
    assertThat(valueOf(provider, "other").get("oneOf"))
        .isEqualTo(List.of(string(), Map.of("type", "null")));
    assertThat(provider.inputSchema().toString()).doesNotContain("camunda-document-id");
  }

  @Test
  void unionParametersInArraysAndObjectsAreRewrittenAndNothingStoredChanges() {
    final var definition =
        new ToolDefinition(
            "send",
            null,
            object(
                "b", Map.of("type", "array", "items", Map.of("anyOf", List.of(string(), doc()))),
                "a", object("file", Map.of("anyOf", List.of(doc(), string())), "pure", doc()),
                "c", string()));
    final var storedSchema = definition.inputSchema();

    final var provider =
        DocumentParameterSchema.toProviderToolDefinitions(List.of(definition)).getFirst();

    assertThat(definition.inputSchema()).isSameAs(storedSchema);
    assertThat(storedSchema.toString()).contains("camunda-document-id");
    assertThat(propertyNames(provider.inputSchema())).containsExactly("b", "a", "c");
    assertThat(provider.inputSchema())
        .isEqualTo(
            object(
                "b",
                    Map.of(
                        "type",
                        "array",
                        "items",
                        Map.of("type", "string", "description", UNION_HINT)),
                "a",
                    object(
                        "file",
                        Map.of("type", "string", "description", UNION_HINT),
                        "pure",
                        Map.of("type", "string", "description", HINT)),
                "c", string()));
  }

  @Test
  void aDefinitionWithoutDocumentOrUnionParametersIsPassedOnAsTheSameInstance() {
    final var noDocument =
        new ToolDefinition(
            "plain",
            null,
            object("value", Map.of("anyOf", List.of(string(), Map.of("type", "number")))));

    assertThat(DocumentParameterSchema.toProviderToolDefinitions(List.of(noDocument)).getFirst())
        .isSameAs(noDocument);
  }

  // --- union parameters: hint

  @Test
  void unionHintTellsTheModelHowToPassADocumentAndThatOtherTextStaysText() {
    assertThat(UNION_HINT)
        .isEqualTo(
            "This parameter takes text or a document. To pass a document, give its tag exactly as"
                + " you read it, for example <doc id=\"...\" />. Any other text is passed as text.");
    assertThat(UNION_HINT).doesNotContain(HINT);
    assertThat(HINT).doesNotContain(UNION_HINT);
  }

  @Test
  void withHintEndsTheDescriptionOfAUnionParameterWithTheUnionHintOnly() {
    final var schema =
        object(
            "body", Map.of("anyOf", List.of(string(), doc()), "description", "The body"),
            "items", Map.of("type", "array", "items", Map.of("oneOf", List.of(doc(), string()))),
            "req", object("note", Map.of("anyOf", List.of(string(), doc())), "plain", string()));

    final var result = DocumentParameterSchema.withHint(schema);

    assertThat(result)
        .isEqualTo(
            object(
                "body",
                    Map.of(
                        "anyOf",
                        List.of(string(), doc()),
                        "description",
                        "The body. " + UNION_HINT),
                "items",
                    Map.of(
                        "type",
                        "array",
                        "items",
                        Map.of("oneOf", List.of(doc(), string()), "description", UNION_HINT)),
                "req",
                    object(
                        "note",
                        Map.of("anyOf", List.of(string(), doc()), "description", UNION_HINT),
                        "plain",
                        string())));
    // the alternatives get no hint of their own
    assertThat(result.toString()).doesNotContain(HINT);
  }

  static Stream<Arguments> descriptionsOfAUnionParameter() {
    return Stream.of(
        Arguments.of("The body.", "The body. " + UNION_HINT),
        Arguments.of("The body!", "The body! " + UNION_HINT),
        Arguments.of("The body?", "The body? " + UNION_HINT),
        Arguments.of("The body.  \n", "The body. " + UNION_HINT),
        Arguments.of("The body", "The body. " + UNION_HINT),
        Arguments.of("", UNION_HINT),
        Arguments.of("   ", UNION_HINT));
  }

  @ParameterizedTest
  @MethodSource("descriptionsOfAUnionParameter")
  void withHintEndsEveryDescriptionOfAUnionParameterWithTheUnionHintOnce(
      String description, String expected) {
    final var once = DocumentParameterSchema.withHint(union(description));

    assertThat(once).isEqualTo(union(expected));
    assertThat(once.toString()).containsOnlyOnce(UNION_HINT);
    assertThat(DocumentParameterSchema.withHint(once)).isEqualTo(once);
  }

  @Test
  void theUnionHintIsTheWholeDescriptionOfAUnionWithoutOne() {
    assertThat(
            DocumentParameterSchema.withHint(
                object("body", Map.of("anyOf", List.of(string(), doc())))))
        .isEqualTo(union(UNION_HINT));
  }

  @Test
  void aDocumentParameterAndAUnionParameterGetTheirOwnHint() {
    final var definition =
        new ToolDefinition(
            "send",
            null,
            object(
                "doc",
                    Map.of(
                        "type", "string", "format", "camunda-document-id", "description", "A file"),
                "body", Map.of("anyOf", List.of(string(), doc()), "description", "A body")));

    assertThat(definition.inputSchema().toString()).doesNotContain(HINT).doesNotContain(UNION_HINT);
    assertThat(
            DocumentParameterSchema.toProviderToolDefinitions(List.of(definition))
                .getFirst()
                .inputSchema())
        .isEqualTo(
            object(
                "doc", Map.of("type", "string", "description", "A file. " + HINT),
                "body", Map.of("type", "string", "description", "A body. " + UNION_HINT)));
  }

  // --- union parameters: authored schema

  @Test
  void normalizesTheShorthandTypeInsideAlternativesAtAnyDepth() {
    final var schema =
        object(
            "text", Map.of("anyOf", List.of(string(), Map.of("type", "document"))),
            "either",
                Map.of("oneOf", List.of(Map.of("type", "document"), Map.of("type", "number"))),
            "items",
                Map.of(
                    "type",
                    "array",
                    "items",
                    Map.of("anyOf", List.of(string(), Map.of("type", "document")))),
            "req", object("file", Map.of("anyOf", List.of(Map.of("type", "document"), string()))));

    assertThat(DocumentParameterSchema.normalizeAuthoredSchema(schema))
        .isEqualTo(
            object(
                "text", Map.of("anyOf", List.of(string(), doc())),
                "either", Map.of("oneOf", List.of(doc(), Map.of("type", "number"))),
                "items",
                    Map.of("type", "array", "items", Map.of("anyOf", List.of(string(), doc()))),
                "req", object("file", Map.of("anyOf", List.of(doc(), string())))));
  }

  @Test
  void normalizesATypeArrayThatListsTheDocumentTypeToAlternativesInThePlaceOfTheType() {
    final var node = new LinkedHashMap<String, Object>();
    node.put("title", "Body");
    node.put("type", List.of("string", "document"));
    node.put("description", "The body");

    final var normalized = DocumentParameterSchema.normalizeAuthoredSchema(object("body", node));

    assertThat(normalized)
        .isEqualTo(
            object(
                "body",
                Map.of(
                    "title",
                    "Body",
                    "anyOf",
                    List.of(string(), doc()),
                    "description",
                    "The body")));
    assertThat(List.copyOf(valueOf(normalized, "body").keySet()))
        .containsExactly("title", "anyOf", "description");
    assertThat(DocumentParameterSchema.isUnionParameter(valueOf(normalized, "body"))).isTrue();
  }

  @Test
  void normalizesATypeArrayWithOtherTypesAndNestedOnesAndAloneAsADocument() {
    final var schema =
        object(
            "value", Map.of("type", List.of("number", "document", "null")),
            "docs", Map.of("type", "array", "items", Map.of("type", List.of("string", "document"))),
            "alone", Map.of("type", List.of("document")));

    assertThat(DocumentParameterSchema.normalizeAuthoredSchema(schema))
        .isEqualTo(
            object(
                "value",
                    Map.of(
                        "anyOf", List.of(Map.of("type", "number"), doc(), Map.of("type", "null"))),
                "docs", Map.of("type", "array", "items", Map.of("anyOf", List.of(string(), doc()))),
                "alone", doc()));
  }

  @Test
  void leavesTypeArraysWithoutTheDocumentTypeAndNextToAlternativesAsTheyAre() {
    final var plain = object("value", Map.of("type", List.of("string", "null")));
    final var withAlternatives =
        object("value", Map.of("type", List.of("string", "document"), "anyOf", List.of(string())));

    assertThat(DocumentParameterSchema.normalizeAuthoredSchema(plain)).isEqualTo(plain);
    assertThat(DocumentParameterSchema.normalizeAuthoredSchema(withAlternatives))
        .isEqualTo(withAlternatives);
  }

  private static Map<String, Object> doc() {
    return Map.of("type", "string", "format", "camunda-document-id");
  }

  private static Map<String, Object> ordered(Object... keysAndValues) {
    final var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return map;
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
