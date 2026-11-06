/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.tools.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.api.tool.DocumentSchemas;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter.Step;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthoredSchemaNormalizerTest {

  private final AuthoredSchemaNormalizer normalizer = new AuthoredSchemaNormalizer();

  @Test
  void normalizesTheShorthandTypeToTheHandleSchema() {
    final var schema =
        object(
            "doc", Map.of("type", "document", "description", "A document"),
            "docs", Map.of("type", "array", "items", Map.of("type", "document")),
            "req", object("file", Map.of("type", "document")));

    assertThat(normalizer.normalize(schema))
        .isEqualTo(
            object(
                "doc", DocumentSchemas.document("A document"),
                "docs", Map.of("type", "array", "items", doc()),
                "req", object("file", doc())));
    assertThat(List.copyOf(valueOf(normalizer.normalize(schema), "doc").keySet()))
        .containsExactly("type", "description", "properties", "required");
  }

  @Test
  void keepsTheOtherAuthoredEntriesOfTheShorthandAfterTheHandleSchema() {
    final var node = new LinkedHashMap<String, Object>();
    node.put("title", "Doc");
    node.put("type", "document");
    node.put("description", "A document");

    final var normalized = valueOf(normalizer.normalize(object("doc", node)), "doc");

    assertThat(normalized).isEqualTo(withTitle(DocumentSchemas.document("A document"), "Doc"));
    assertThat(List.copyOf(normalized.keySet()))
        .containsExactly("type", "description", "properties", "required", "title");
  }

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

    assertThat(normalizer.normalize(schema))
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

    final var normalized = normalizer.normalize(object("body", node));

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
    assertThat(DocumentParameter.isTextOrDocumentSchema(valueOf(normalized, "body"))).isTrue();
  }

  @Test
  void normalizesATypeArrayWithOtherTypesAndNestedOnesAndAloneAsADocument() {
    final var schema =
        object(
            "value", Map.of("type", List.of("number", "document", "null")),
            "docs", Map.of("type", "array", "items", Map.of("type", List.of("string", "document"))),
            "alone", Map.of("type", List.of("document")));

    assertThat(normalizer.normalize(schema))
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

    assertThat(normalizer.normalize(plain)).isEqualTo(plain);
    assertThat(normalizer.normalize(withAlternatives)).isEqualTo(withAlternatives);
  }

  @Test
  void normalizesExactlyWhereADocumentParameterIsFound() {
    final var inObjectAlternative =
        Map.of("anyOf", List.of(string(), object("file", Map.of("type", "document"))));
    final var inArrayAlternative =
        Map.of(
            "oneOf",
            List.of(string(), Map.of("type", "array", "items", Map.of("type", "document"))));
    final var itemsNextToProperties = new LinkedHashMap<String, Object>();
    itemsNextToProperties.put("type", "object");
    itemsNextToProperties.put("properties", Map.of("file", Map.of("type", "document")));
    itemsNextToProperties.put("items", Map.of("type", "document"));
    final var schema =
        object(
            "objectAlternative", inObjectAlternative,
            "arrayAlternative", inArrayAlternative,
            "both", itemsNextToProperties);

    final var normalized = normalizer.normalize(schema);

    // a shorthand where no document parameter is found stays as authored, so the handle schema
    // appears exactly where the parameter is found
    assertThat(normalized)
        .isEqualTo(
            object(
                "objectAlternative", inObjectAlternative,
                "arrayAlternative", inArrayAlternative,
                "both",
                    Map.of(
                        "type",
                        "object",
                        "properties",
                        Map.of("file", doc()),
                        "items",
                        Map.of("type", "document"))));
    assertThat(DocumentParameter.in(normalized))
        .containsExactly(
            new DocumentParameter.Document(
                List.of(new Step.Property("both"), new Step.Property("file"))));
  }

  private static Map<String, Object> doc() {
    return DocumentSchemas.document(null);
  }

  private static Map<String, Object> withTitle(Map<String, Object> schema, String title) {
    final var result = new LinkedHashMap<>(schema);
    result.put("title", title);
    return result;
  }

  private static Map<String, Object> string() {
    return Map.of("type", "string");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> valueOf(Map<String, Object> schema, String property) {
    return (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get(property);
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
