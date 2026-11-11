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
  void normalizesTheShorthandTypeOfPropertiesAndItemsAtAnyDepth() {
    final var schema =
        object(
            "rows",
                Map.of(
                    "type",
                    "array",
                    "items",
                    object("file", Map.of("type", "document"), "note", string())),
            "nested", object("inner", object("file", Map.of("type", "document"))));

    assertThat(normalizer.normalize(schema))
        .isEqualTo(
            object(
                "rows", Map.of("type", "array", "items", object("file", doc(), "note", string())),
                "nested", object("inner", object("file", doc()))));
  }

  @Test
  void leavesATypeArrayWithoutTheDocumentTypeAsItIs() {
    final var schema = object("value", Map.of("type", List.of("string", "null")));

    assertThat(normalizer.normalize(schema)).isEqualTo(schema);
  }

  @Test
  void leavesTheShorthandInTheDataKeywordsAsItIs() {
    final var shorthand = Map.<String, Object>of("type", "document");
    final var schema =
        object(
            "withEnum", Map.of("type", "string", "enum", List.of("a", shorthand)),
            "withDefault", Map.of("type", "object", "default", shorthand),
            "withConst", Map.of("const", shorthand),
            "withExamples", Map.of("type", "object", "examples", List.of(shorthand)),
            "doc", Map.of("type", "document"));

    assertThat(normalizer.normalize(schema))
        .isEqualTo(
            object(
                "withEnum", Map.of("type", "string", "enum", List.of("a", shorthand)),
                "withDefault", Map.of("type", "object", "default", shorthand),
                "withConst", Map.of("const", shorthand),
                "withExamples", Map.of("type", "object", "examples", List.of(shorthand)),
                "doc", doc()));
  }

  static Stream<Map<String, Object>> schemasWithTheDocumentTypeWhereNoParameterCanSit() {
    final var shorthand = Map.<String, Object>of("type", "document");
    final var itemsNextToProperties = new LinkedHashMap<String, Object>();
    itemsNextToProperties.put("type", "object");
    itemsNextToProperties.put("properties", Map.of("file", shorthand));
    itemsNextToProperties.put("items", shorthand);
    return Stream.of(
        object("value", Map.of("anyOf", List.of(string(), shorthand))),
        object("value", Map.of("oneOf", List.of(shorthand, Map.of("type", "number")))),
        object(
            "value",
            Map.of(
                "anyOf",
                List.of(Map.of("type", "number"), Map.of("oneOf", List.of(string(), shorthand))))),
        object(
            "docs",
            Map.of("type", "array", "items", Map.of("anyOf", List.of(string(), shorthand)))),
        object("req", object("file", Map.of("anyOf", List.of(shorthand, string())))),
        object(
            "message",
            Map.of("anyOf", List.of(string(), object("file", shorthand, "caption", string())))),
        object("value", Map.of("type", List.of("string", "document"))),
        object("value", Map.of("type", List.of("document"))),
        object("value", Map.of("type", List.of("string", "document"), "anyOf", List.of(string()))),
        object(
            "docs",
            Map.of("type", "array", "items", Map.of("type", List.of("string", "document")))),
        object("all", Map.of("allOf", List.of(shorthand))),
        object(
            "allTypeArray",
            Map.of("allOf", List.of(Map.of("type", List.of("string", "document"))))),
        object("not", Map.of("not", shorthand)),
        object("additional", Map.of("type", "object", "additionalProperties", shorthand)),
        object("prefix", Map.of("type", "array", "prefixItems", List.of(shorthand))),
        object("defs", Map.of("$defs", Map.of("file", shorthand))),
        object("both", itemsNextToProperties));
  }

  @ParameterizedTest
  @MethodSource("schemasWithTheDocumentTypeWhereNoParameterCanSit")
  void rejectsTheDocumentTypeWhereNoDocumentParameterCanSit(Map<String, Object> schema) {
    assertThatThrownBy(() -> normalizer.normalize(schema))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The type 'document' can only be the type of a parameter, of the items of an array or"
                + " of an object property. Use a separate parameter for a document.");
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
