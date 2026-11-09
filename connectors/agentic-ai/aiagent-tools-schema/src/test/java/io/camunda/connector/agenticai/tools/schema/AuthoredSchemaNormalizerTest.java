/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.tools.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.api.tool.DocumentSchemas;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter.Step;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
  void leavesATypeArrayWithoutTheDocumentTypeAsItIs() {
    final var schema = object("value", Map.of("type", List.of("string", "null")));

    assertThat(normalizer.normalize(schema)).isEqualTo(schema);
  }

  static Stream<Map<String, Object>> schemasWithTheDocumentTypeInATypeArray() {
    return Stream.of(
        object("value", Map.of("type", List.of("string", "document"))),
        object("value", Map.of("type", List.of("document"))),
        object("value", Map.of("type", List.of("string", "document"), "anyOf", List.of(string()))),
        object(
            "docs",
            Map.of("type", "array", "items", Map.of("type", List.of("string", "document")))),
        object(
            "value",
            Map.of(
                "anyOf",
                List.of(string(), object("file", Map.of("type", List.of("string", "document")))))));
  }

  @ParameterizedTest
  @MethodSource("schemasWithTheDocumentTypeInATypeArray")
  void rejectsATypeArrayThatListsTheDocumentType(Map<String, Object> schema) {
    assertThatThrownBy(() -> normalizer.normalize(schema))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The type 'document' cannot be part of a type array. Use \"type\": \"document\" for a"
                + " document, or \"anyOf\" with {\"type\": \"document\"} as one alternative for a"
                + " document or another value.");
  }

  @Test
  void normalizesTheShorthandInsideObjectAndArrayAlternatives() {
    final var schema =
        object(
            "message",
                Map.of(
                    "anyOf",
                    List.of(
                        string(), object("file", Map.of("type", "document"), "caption", string()))),
            "files",
                Map.of(
                    "oneOf",
                    List.of(
                        Map.of("type", "document"),
                        Map.of("type", "array", "items", Map.of("type", "document")))));

    final var normalized = normalizer.normalize(schema);

    assertThat(normalized)
        .isEqualTo(
            object(
                "message",
                    Map.of("anyOf", List.of(string(), object("file", doc(), "caption", string()))),
                "files", Map.of("oneOf", List.of(doc(), Map.of("type", "array", "items", doc())))));
    assertThat(DocumentParameter.in(normalized))
        .containsExactly(
            new DocumentParameter.Union(List.of(new Step.Property("message"))),
            new DocumentParameter.Union(List.of(new Step.Property("files"))));
  }

  @Test
  void normalizesTheShorthandInsideNestedAlternatives() {
    final var schema =
        object(
            "value",
            Map.of(
                "anyOf",
                List.of(
                    Map.of("type", "number"),
                    Map.of("oneOf", List.of(string(), Map.of("type", "document"))))));

    assertThat(normalizer.normalize(schema))
        .isEqualTo(
            object(
                "value",
                Map.of(
                    "anyOf",
                    List.of(Map.of("type", "number"), Map.of("oneOf", List.of(string(), doc()))))));
  }

  @Test
  void leavesTheShorthandWhereTheAgentResolvesNoDocumentAsAuthored() {
    final var shorthand = Map.<String, Object>of("type", "document");
    final var itemsNextToProperties = new LinkedHashMap<String, Object>();
    itemsNextToProperties.put("type", "object");
    itemsNextToProperties.put("properties", Map.of("file", shorthand));
    itemsNextToProperties.put("items", shorthand);
    final var schema =
        object(
            "all", Map.of("allOf", List.of(shorthand)),
            "not", Map.of("not", shorthand),
            "additional", Map.of("type", "object", "additionalProperties", shorthand),
            "prefix", Map.of("type", "array", "prefixItems", List.of(shorthand)),
            "defs", Map.of("$defs", Map.of("file", shorthand)),
            "allTypeArray", Map.of("allOf", List.of(Map.of("type", List.of("string", "document")))),
            "both", itemsNextToProperties);

    final var normalized = normalizer.normalize(schema);

    assertThat(normalized)
        .isEqualTo(
            object(
                "all", Map.of("allOf", List.of(shorthand)),
                "not", Map.of("not", shorthand),
                "additional", Map.of("type", "object", "additionalProperties", shorthand),
                "prefix", Map.of("type", "array", "prefixItems", List.of(shorthand)),
                "defs", Map.of("$defs", Map.of("file", shorthand)),
                "allTypeArray",
                    Map.of("allOf", List.of(Map.of("type", List.of("string", "document")))),
                "both",
                    Map.of(
                        "type",
                        "object",
                        "properties",
                        Map.of("file", doc()),
                        "items",
                        shorthand)));
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
