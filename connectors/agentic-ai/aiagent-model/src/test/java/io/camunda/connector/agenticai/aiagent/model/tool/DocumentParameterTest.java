/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter.Step;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentParameterTest {

  private static DocumentParameter document(Step... path) {
    return new DocumentParameter.Document(List.of(path));
  }

  private static DocumentParameter union(Step... path) {
    return new DocumentParameter.Union(List.of(path));
  }

  private static Step.Property property(String name) {
    return new Step.Property(name);
  }

  private static Step.Items items() {
    return new Step.Items();
  }

  @Test
  void findsTopLevelArrayAndNestedDocumentParameters() {
    final var schema =
        object(
            "doc", withDescription(doc(), "A doc"),
            "docs", Map.of("type", "array", "items", doc()),
            "req", object("file", doc(), "note", string()),
            "rows", Map.of("type", "array", "items", object("file", doc())));

    assertThat(DocumentParameter.in(schema))
        .containsExactly(
            document(property("doc")),
            document(property("docs"), items()),
            document(property("req"), property("file")),
            document(property("rows"), items(), property("file")));
  }

  @Test
  void findsNoDocumentParameterInASchemaWithoutTheHandleSchema() {
    final var schema =
        object(
            "text", string(),
            "type", Map.of("type", "string", "description", "a property named type"));

    assertThat(DocumentParameter.in(schema)).isEmpty();
  }

  @Test
  void anObjectIsAHandleSchemaOnlyWithTheHandleTypeAsTheOnlyValueOfTheTypeProperty() {
    assertThat(DocumentParameter.in(object("p", doc()))).containsExactly(document(property("p")));
    assertThat(DocumentParameter.in(object("p", withDescription(doc(), "A doc"))))
        .containsExactly(document(property("p")));
    assertThat(DocumentParameter.in(object("p", object("id", string())))).isEmpty();
    assertThat(
            DocumentParameter.in(
                object("p", object("type", Map.of("type", "string", "enum", List.of("other"))))))
        .isEmpty();
    assertThat(
            DocumentParameter.in(
                object(
                    "p",
                    object(
                        "type",
                        Map.of(
                            "type",
                            "string",
                            "enum",
                            List.of("camunda.agenticai.document", "other"))))))
        .isEmpty();
    assertThat(DocumentParameter.in(object("p", object("type", string())))).isEmpty();
    assertThat(DocumentParameter.in(object("p", string()))).isEmpty();
  }

  @Test
  void theSearchStopsAtAHandleSchemaAndNeverTreatsItsPropertiesAsParameters() {
    final var schema = object("doc", doc());

    assertThat(DocumentParameter.in(schema)).containsExactly(document(property("doc")));
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

    assertThat(DocumentParameter.in(schema))
        .containsExactly(
            union(property("text")),
            union(property("either")),
            union(property("items"), items()),
            union(property("req"), property("file")),
            union(property("rows"), items(), property("file")),
            document(property("pure")));
  }

  @Test
  void aNodeWithSeveralAlternativesAndADocumentSchemaAmongThemIsAUnion() {
    assertThat(DocumentParameter.isUnionSchema(Map.of("anyOf", List.of(string(), doc())))).isTrue();
    assertThat(
            DocumentParameter.isUnionSchema(
                Map.of("oneOf", List.of(doc(), Map.of("type", "null")))))
        .isTrue();
    // the other alternatives may be objects or arrays
    assertThat(
            DocumentParameter.isUnionSchema(Map.of("anyOf", List.of(object("a", string()), doc()))))
        .isTrue();
    assertThat(
            DocumentParameter.isUnionSchema(
                Map.of("oneOf", List.of(Map.of("type", "array", "items", string()), doc()))))
        .isTrue();
    // no document alternative
    assertThat(
            DocumentParameter.isUnionSchema(
                Map.of("anyOf", List.of(string(), Map.of("type", "number")))))
        .isFalse();
    // a document node is not a union node
    assertThat(DocumentParameter.isUnionSchema(doc())).isFalse();
  }

  @Test
  void aDocumentInAnObjectAlternativeMakesAUnion() {
    final var schema =
        object(
            "message",
            Map.of("anyOf", List.of(string(), object("file", doc(), "caption", string()))));

    assertThat(DocumentParameter.in(schema)).containsExactly(union(property("message")));
  }

  @Test
  void aDocumentInAnArrayAlternativeMakesAUnion() {
    final var schema =
        object(
            "files",
            Map.of("oneOf", List.of(doc(), Map.of("type", "array", "items", doc()))),
            "texts",
            Map.of("anyOf", List.of(string(), Map.of("type", "array", "items", doc()))));

    assertThat(DocumentParameter.in(schema))
        .containsExactly(union(property("files")), union(property("texts")));
  }

  @Test
  void aDocumentInANestedUnionMakesTheOuterNodeAUnion() {
    final var nested = Map.of("anyOf", List.of(string(), doc()));
    final var schema = object("value", Map.of("oneOf", List.of(Map.of("type", "number"), nested)));

    assertThat(DocumentParameter.in(schema)).containsExactly(union(property("value")));
    assertThat(DocumentParameter.isUnionSchema(nested)).isTrue();
  }

  @Test
  void aDocumentDeeperInAnAlternativeMakesAUnion() {
    final var alternative = object("rows", Map.of("type", "array", "items", object("file", doc())));
    final var schema = object("value", Map.of("anyOf", List.of(string(), alternative)));

    assertThat(DocumentParameter.in(schema)).containsExactly(union(property("value")));
  }

  @Test
  void theSearchStopsAtAUnionAndFindsNoParameterInsideItsAlternatives() {
    final var schema =
        object(
            "doc",
            Map.of(
                "anyOf",
                List.of(string(), Map.of("type", "array", "items", doc()), object("file", doc()))));

    assertThat(DocumentParameter.in(schema)).containsExactly(union(property("doc")));
  }

  @Test
  void anAnyOfWithoutADocumentAnywhereIsNoUnionAndIsNotSearched() {
    final var schema =
        object(
            "value",
            Map.of("anyOf", List.of(string(), object("a", string()), Map.of("type", "number"))));

    assertThat(DocumentParameter.in(schema)).isEmpty();
    assertThat(
            DocumentParameter.isUnionSchema(Map.of("anyOf", List.of(string(), object("a", doc())))))
        .isTrue();
    assertThat(
            DocumentParameter.isUnionSchema(
                Map.of("anyOf", List.of(string(), object("a", string())))))
        .isFalse();
  }

  @Test
  void aSingleAlternativeWithADocumentInsideMakesAUnion() {
    final var schema =
        object(
            "doc",
            Map.of("anyOf", List.of(doc())),
            "value",
            Map.of("oneOf", List.of(object("file", doc()))));

    assertThat(DocumentParameter.in(schema))
        .containsExactly(union(property("doc")), union(property("value")));
  }

  @Test
  void anAlternativeWithPropertiesHoldsNoDocumentInItsItems() {
    final var alternative = new LinkedHashMap<String, Object>();
    alternative.put("type", "object");
    alternative.put("properties", Map.of("text", string()));
    alternative.put("items", doc());

    assertThat(DocumentParameter.in(object("v", Map.of("anyOf", List.of(string(), alternative)))))
        .isEmpty();
  }

  @Test
  void anAlternativeHoldsADocumentDirectlyInItsPropertiesInItsItemsOrInItsAlternatives() {
    for (final var alternative :
        List.of(
            doc(),
            object("file", doc()),
            Map.of("type", "array", "items", doc()),
            Map.of("oneOf", List.of(string(), doc())))) {
      assertThat(DocumentParameter.in(object("v", Map.of("anyOf", List.of(string(), alternative)))))
          .containsExactly(union(property("v")));
    }
    for (final var alternative : List.of(object("a", string()), string())) {
      assertThat(DocumentParameter.in(object("v", Map.of("anyOf", List.of(string(), alternative)))))
          .isEmpty();
    }
  }

  @Test
  void looksIntoTheItemsOfANodeOnlyWhenItHasNoProperties() {
    final var withProperties = new LinkedHashMap<String, Object>();
    withProperties.put("type", "object");
    withProperties.put("properties", Map.of("file", doc()));
    withProperties.put("items", doc());

    assertThat(DocumentParameter.in(object("both", withProperties)))
        .containsExactly(document(property("both"), property("file")));
  }

  @Test
  void aTypeDocumentNodeIsNotADocumentParameter() {
    assertThat(DocumentParameter.in(object("doc", Map.of("type", "document")))).isEmpty();
  }

  @Test
  void aSchemaThatIsItselfADocumentHasNoParameter() {
    assertThat(DocumentParameter.in(doc())).isEmpty();
  }

  @Test
  void findsThePropertiesOfASchemaWithAlternativesNextToItsProperties() {
    final var schema = new LinkedHashMap<>(object("file", doc(), "text", string()));
    schema.put("anyOf", List.of(object("file", doc()), object("text", string())));

    assertThat(DocumentParameter.in(schema)).containsExactly(document(property("file")));
  }

  @Test
  void aToolDefinitionLooksUpItsDocumentParametersInTheInputSchema() {
    final var definition =
        new ToolDefinition("upload", null, object("doc", doc(), "text", string()));

    assertThat(definition.documentParameters()).containsExactly(document(property("doc")));
    assertThat(new ToolDefinition("plain", null, object("text", string())).documentParameters())
        .isEmpty();
  }

  @Test
  void mapChildrenReplacesThePropertiesAndKeepsTheOtherEntriesInOrder() {
    final var node = new LinkedHashMap<String, Object>();
    node.put("title", "T");
    node.put("properties", Map.of("a", string()));
    node.put("required", List.of("a"));

    final var mapped = Step.mapChildren(node, (step, child) -> Map.of("seen", step.toString()));

    assertThat(mapped)
        .containsExactly(
            Map.entry("title", "T"),
            Map.entry("properties", Map.of("a", Map.of("seen", "Property[name=a]"))),
            Map.entry("required", List.of("a")));
  }

  private static Map<String, Object> doc() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of(
            "type",
            Map.of("type", "string", "enum", List.of("camunda.agenticai.document")),
            "id",
            Map.of("type", "string")),
        "required",
        List.of("type", "id"));
  }

  private static Map<String, Object> withDescription(Map<String, Object> schema, String text) {
    final var result = new LinkedHashMap<>(schema);
    result.put("description", text);
    return result;
  }

  private static Map<String, Object> string() {
    return Map.of("type", "string");
  }

  /** An object schema from alternating property names and schemas, in declaration order. */
  private static Map<String, Object> object(Object... namesAndSchemas) {
    final var properties = new LinkedHashMap<String, Object>();
    if (namesAndSchemas.length % 2 != 0) {
      throw new IllegalArgumentException("Expected alternating property names and schemas");
    }
    for (int i = 0; i + 1 < namesAndSchemas.length; i += 2) {
      properties.put((String) namesAndSchemas[i], namesAndSchemas[i + 1]);
    }
    final var schema = new LinkedHashMap<String, Object>();
    schema.put("type", "object");
    schema.put("properties", properties);
    return schema;
  }
}
