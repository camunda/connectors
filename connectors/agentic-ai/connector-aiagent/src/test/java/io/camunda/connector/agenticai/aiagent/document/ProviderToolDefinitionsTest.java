/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProviderToolDefinitionsTest {

  private static final String HINT = ProviderToolDefinitions.HINT;

  private static final String UNION_HINT = ProviderToolDefinitions.UNION_HINT;

  private static final DocumentHandling TRACKED =
      new DocumentHandling.Tracked(AgentDocuments.empty());

  private static ToolDefinition provider(ToolDefinition definition) {
    return ProviderToolDefinitions.of(List.of(definition), TRACKED).getFirst();
  }

  private static Map<String, Object> providerSchema(Map<String, Object> inputSchema) {
    return provider(new ToolDefinition("tool", null, inputSchema)).inputSchema();
  }

  @Test
  void hintsAskForTheTagOfAKnownDocument() {
    assertThat(HINT)
        .isEqualTo(
            "Pass the tag of a known document exactly as you read it, for example <doc id=\"...\" />.");
    assertThat(UNION_HINT)
        .isEqualTo(
            "This parameter takes text or a document. To pass a document, give its tag exactly as"
                + " you read it, for example <doc id=\"...\" />. Any other text is passed as text.");
    assertThat(UNION_HINT).doesNotContain(HINT);
    assertThat(HINT).doesNotContain(UNION_HINT);
  }

  @Test
  void rewritesDocumentNodesToStringSchemasWithoutTheFormatAndEndsTheirDescriptionWithTheHint() {
    final var definition =
        new ToolDefinition(
            "upload",
            "Uploads",
            object(
                "doc", described("A document"),
                "bare", doc(),
                "docs", Map.of("type", "array", "description", "Many", "items", doc()),
                "req", object("file", doc(), "note", string()),
                "rows", Map.of("type", "array", "items", object("file", doc()))));

    final var rewritten = provider(definition);

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
                        "note", string()),
                "rows",
                    Map.of(
                        "type",
                        "array",
                        "items",
                        object("file", Map.of("type", "string", "description", HINT)))));
  }

  @Test
  void keepsTheKindOfTheDefinition() {
    final var runtime = ToolDefinition.runtime("upload", null, object("doc", doc()));

    assertThat(provider(runtime).kind()).isEqualTo(ToolKind.RUNTIME);
  }

  @Test
  void doesNotModifyTheStoredDefinitionAndKeepsTheOrderOfTheSchema() {
    final var authored =
        new ToolDefinition("upload", null, object("b", doc(), "a", string(), "c", string()));
    final var authoredSchema = authored.inputSchema();

    final var rewritten = provider(authored);

    assertThat(authored.inputSchema()).isSameAs(authoredSchema);
    assertThat(authored.inputSchema()).isEqualTo(object("b", doc(), "a", string(), "c", string()));
    assertThat(propertyNames(rewritten.inputSchema())).containsExactly("b", "a", "c");
  }

  @Test
  void returnsDefinitionsWithoutDocumentParametersAsTheSameInstance() {
    final var plain = new ToolDefinition("plain", null, object("text", string()));
    final var noDocument =
        new ToolDefinition(
            "plain",
            null,
            object("value", Map.of("anyOf", List.of(string(), Map.of("type", "number")))));

    assertThat(provider(plain)).isSameAs(plain);
    assertThat(provider(noDocument)).isSameAs(noDocument);
  }

  @Test
  void sendsTheDefinitionsOfAnAgentInstanceThatDoesNotTrackItsDocumentsAsTheyAre() {
    final var definitions =
        List.of(
            new ToolDefinition(
                "upload",
                null,
                object("doc", doc(), "body", Map.of("anyOf", List.of(string(), doc())))));

    assertThat(ProviderToolDefinitions.of(definitions, new DocumentHandling.Untracked()))
        .isSameAs(definitions);
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
  void endsEveryDescriptionOfADocumentParameterWithTheHint(String description, String expected) {
    assertThat(providerSchema(object("doc", described(description))))
        .isEqualTo(object("doc", Map.of("type", "string", "description", expected)));
  }

  @Test
  void keepsADescriptionThatAlreadyHoldsTheHint() {
    final var description = "A document. " + HINT;

    assertThat(providerSchema(object("doc", described(description))))
        .isEqualTo(object("doc", Map.of("type", "string", "description", description)));
  }

  // --- parameters that take text or a document

  @Test
  void aUnionOfTextAndADocumentIsAPlainStringWithTheDescriptionOfTheNode() {
    final var schema =
        providerSchema(
            object("body", Map.of("anyOf", List.of(string(), doc()), "description", "The body")));

    assertThat(schema)
        .isEqualTo(
            object("body", Map.of("type", "string", "description", "The body. " + UNION_HINT)));
  }

  @Test
  void aUnionNodeWithATypeKeepsItsOwnTypeAndTheOrderOfItsEntries() {
    final var body = new LinkedHashMap<String, Object>();
    body.put("description", "The body");
    body.put("anyOf", List.of(string(), doc()));
    body.put("type", "string");
    body.put("title", "Body");

    final var schema = providerSchema(object("body", body));

    assertThat(List.copyOf(valueOf(schema, "body").keySet()))
        .containsExactly("description", "type", "title");
    assertThat(valueOf(schema, "body"))
        .isEqualTo(
            Map.of("description", "The body. " + UNION_HINT, "type", "string", "title", "Body"));
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

    final var schema = providerSchema(object("body", body));

    assertThat(List.copyOf(valueOf(schema, "body").keySet()))
        .containsExactly("title", "type", "maxLength", "description");
    assertThat(valueOf(schema, "body"))
        .isEqualTo(
            Map.of(
                "title",
                "Body",
                "type",
                "string",
                "maxLength",
                9,
                "description",
                "The body. " + UNION_HINT));
  }

  @Test
  void alternativesThatDifferAfterTheRewriteAreKept() {
    final var body = new LinkedHashMap<String, Object>();
    body.put("anyOf", List.of(Map.of("type", "string", "maxLength", 9), doc()));

    final var schema = providerSchema(object("body", body));

    assertThat(valueOf(schema, "body"))
        .isEqualTo(
            Map.of(
                "anyOf",
                List.of(Map.of("type", "string", "maxLength", 9), string()),
                "description",
                UNION_HINT));
  }

  @Test
  void identicalAlternativesAreMergedAndASingleOneIsUnwrappedInPlaceOfTheKeyword() {
    final var body = new LinkedHashMap<String, Object>();
    body.put("title", "Body");
    body.put("anyOf", List.of(string(), doc(), string()));
    body.put("description", "The body");

    final var schema = providerSchema(object("body", body));

    assertThat(List.copyOf(valueOf(schema, "body").keySet()))
        .containsExactly("title", "type", "description");
    assertThat(valueOf(schema, "body"))
        .isEqualTo(
            Map.of("title", "Body", "type", "string", "description", "The body. " + UNION_HINT));
  }

  @Test
  void aUnionWithOtherTypesKeepsTheKeywordAndTheOrderOfItsAlternatives() {
    final var schema =
        providerSchema(
            object(
                "value", Map.of("anyOf", List.of(Map.of("type", "number"), doc(), string())),
                "other", Map.of("oneOf", List.of(doc(), Map.of("type", "null")))));

    assertThat(schema)
        .isEqualTo(
            object(
                "value",
                    Map.of(
                        "anyOf",
                        List.of(Map.of("type", "number"), string()),
                        "description",
                        UNION_HINT),
                "other",
                    Map.of(
                        "oneOf",
                        List.of(string(), Map.of("type", "null")),
                        "description",
                        UNION_HINT)));
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

    final var rewritten = provider(definition);

    assertThat(definition.inputSchema()).isSameAs(storedSchema);
    assertThat(storedSchema)
        .isEqualTo(
            object(
                "b", Map.of("type", "array", "items", Map.of("anyOf", List.of(string(), doc()))),
                "a", object("file", Map.of("anyOf", List.of(doc(), string())), "pure", doc()),
                "c", string()));
    assertThat(propertyNames(rewritten.inputSchema())).containsExactly("b", "a", "c");
    assertThat(rewritten.inputSchema())
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
  void aDocumentParameterAndAUnionParameterGetTheirOwnHint() {
    final var definition =
        new ToolDefinition(
            "send",
            null,
            object(
                "doc", described("A file"),
                "body", Map.of("anyOf", List.of(string(), doc()), "description", "A body")));

    assertThat(provider(definition).inputSchema())
        .isEqualTo(
            object(
                "doc", Map.of("type", "string", "description", "A file. " + HINT),
                "body", Map.of("type", "string", "description", "A body. " + UNION_HINT)));
  }

  @Test
  void theUnionHintIsTheWholeDescriptionOfAUnionWithoutOne() {
    assertThat(providerSchema(object("body", Map.of("anyOf", List.of(string(), doc())))))
        .isEqualTo(object("body", Map.of("type", "string", "description", UNION_HINT)));
  }

  @Test
  void neverLeavesADocumentFormatInTheRewrittenSchema() {
    final var definition =
        new ToolDefinition(
            "upload",
            null,
            object(
                "docs", Map.of("type", "array", "items", object("file", doc())),
                "doc", doc(),
                "body", Map.of("anyOf", List.of(string(), doc()))));

    final var rewritten = provider(definition);

    assertThat(rewritten.documentParameters()).isEmpty();
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

  private static Map<String, Object> ordered(Object... keysAndValues) {
    final var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return map;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> valueOf(Map<String, Object> schema, String property) {
    return (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get(property);
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
