/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter.Step;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DocumentParameterTest {

  @Test
  void findsTopLevelArrayAndNestedDocumentParameters() {
    final var schema =
        object(
            "doc", withDescription(doc(), "A doc"),
            "docs", Map.of("type", "array", "items", doc()),
            "req", object("file", doc(), "note", string()),
            "rows", Map.of("type", "array", "items", object("file", doc())));

    assertThat(DocumentParameter.findAll(schema))
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

    assertThat(DocumentParameter.findAll(schema)).isEmpty();
  }

  @Test
  void anObjectIsAHandleSchemaOnlyWithTheHandleTypeAsTheOnlyValueOfTheTypeProperty() {
    assertThat(DocumentParameter.findAll(object("p", doc())))
        .containsExactly(document(property("p")));
    assertThat(DocumentParameter.findAll(object("p", withDescription(doc(), "A doc"))))
        .containsExactly(document(property("p")));
    assertThat(DocumentParameter.findAll(object("p", object("id", string())))).isEmpty();
    assertThat(
            DocumentParameter.findAll(
                object("p", object("type", Map.of("type", "string", "enum", List.of("other"))))))
        .isEmpty();
    assertThat(
            DocumentParameter.findAll(
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
    assertThat(DocumentParameter.findAll(object("p", object("type", string())))).isEmpty();
    assertThat(DocumentParameter.findAll(object("p", string()))).isEmpty();
  }

  @Test
  void theSearchStopsAtAHandleSchemaAndNeverTreatsItsPropertiesAsParameters() {
    final var schema = object("doc", doc());

    assertThat(DocumentParameter.findAll(schema)).containsExactly(document(property("doc")));
  }

  @Test
  void findsNoDocumentParameterInAnAlternative() {
    final var schema =
        object(
            "text", Map.of("anyOf", List.of(string(), doc())),
            "either", Map.of("oneOf", List.of(Map.of("type", "number"), doc())),
            "items", Map.of("type", "array", "items", Map.of("anyOf", List.of(doc(), string()))),
            "message", Map.of("anyOf", List.of(string(), object("file", doc()))),
            "pure", doc());

    assertThat(DocumentParameter.findAll(schema)).containsExactly(document(property("pure")));
  }

  @Test
  void looksIntoTheItemsOfANodeOnlyWhenItHasNoProperties() {
    final var withProperties = new LinkedHashMap<String, Object>();
    withProperties.put("type", "object");
    withProperties.put("properties", Map.of("file", doc()));
    withProperties.put("items", doc());

    assertThat(DocumentParameter.findAll(object("both", withProperties)))
        .containsExactly(document(property("both"), property("file")));
  }

  @Test
  void aTypeDocumentNodeIsNotADocumentParameter() {
    assertThat(DocumentParameter.findAll(object("doc", Map.of("type", "document")))).isEmpty();
  }

  @Test
  void aSchemaThatIsItselfADocumentHasNoParameter() {
    assertThat(DocumentParameter.findAll(doc())).isEmpty();
  }

  @Test
  void findsThePropertiesOfASchemaWithAlternativesNextToItsProperties() {
    final var schema = new LinkedHashMap<>(object("file", doc(), "text", string()));
    schema.put("anyOf", List.of(object("file", doc()), object("text", string())));

    assertThat(DocumentParameter.findAll(schema)).containsExactly(document(property("file")));
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

    final var mapped =
        DocumentParameter.mapChildren(node, (step, child) -> Map.of("seen", step.toString()));

    assertThat(mapped)
        .containsExactly(
            Map.entry("title", "T"),
            Map.entry("properties", Map.of("a", Map.of("seen", "Property[name=a]"))),
            Map.entry("required", List.of("a")));
  }

  @Test
  void theDocumentSchemaIsTheObjectSchemaOfAHandleWithTheDescription() {
    final var schema = DocumentParameter.documentSchema("The file");

    assertThat(schema).isEqualTo(handleSchema("The file"));
    assertThat(List.copyOf(schema.keySet()))
        .containsExactly("type", "description", "properties", "required");
    assertThat(List.copyOf(properties(schema).keySet())).containsExactly("type", "id");
  }

  @Test
  void theDocumentSchemaWithoutAUsableDescriptionHasNone() {
    final var schema = DocumentParameter.documentSchema(null);

    assertThat(schema).isEqualTo(handleSchema(null));
    assertThat(List.copyOf(schema.keySet())).containsExactly("type", "properties", "required");
    assertThat(DocumentParameter.documentSchema("  ")).isEqualTo(handleSchema(null));
  }

  @Test
  void theDocumentSchemaHasNoFormatAndNoAdditionalPropertiesRule() {
    assertThat(DocumentParameter.documentSchema("The file"))
        .doesNotContainKeys("format", "additionalProperties");
  }

  @Test
  void fromDocumentTypeMapsTheDocumentTypeOfParametersItemsAndPropertiesToTheDocumentSchema() {
    final var inputSchema =
        object(
            "doc", Map.of("type", "document", "description", "The invoice"),
            "note", string(),
            "docs", Map.of("type", "array", "items", Map.of("type", "document")),
            "message", object("file", Map.of("type", "document")));
    inputSchema.put("required", List.of("doc", "note"));

    final var expected =
        object(
            "doc", handleSchema("The invoice"),
            "note", string(),
            "docs", Map.of("type", "array", "items", handleSchema(null)),
            "message", object("file", handleSchema(null)));
    expected.put("required", List.of("doc", "note"));
    assertThat(DocumentParameter.fromDocumentType(inputSchema)).isEqualTo(expected);
  }

  @Test
  void fromDocumentTypeMapsTheDocumentTypeAtAnyDepth() {
    final var inputSchema =
        object(
            "rows",
                Map.of(
                    "type",
                    "array",
                    "items",
                    object("file", Map.of("type", "document"), "note", string())),
            "nested", object("inner", object("file", Map.of("type", "document"))));

    assertThat(DocumentParameter.fromDocumentType(inputSchema))
        .isEqualTo(
            object(
                "rows",
                    Map.of(
                        "type",
                        "array",
                        "items",
                        object("file", handleSchema(null), "note", string())),
                "nested", object("inner", object("file", handleSchema(null)))));
  }

  @Test
  void fromDocumentTypeMapsTheDocumentTypeOfAPropertyNamedLikeADataKeyword() {
    final var inputSchema = object("request", object("default", Map.of("type", "document")));

    assertThat(DocumentParameter.fromDocumentType(inputSchema))
        .isEqualTo(object("request", object("default", handleSchema(null))));
  }

  @Test
  void fromDocumentTypeKeepsTheOtherAuthoredEntriesAfterTheDocumentSchema() {
    final var authored = new LinkedHashMap<String, Object>();
    authored.put("title", "Doc");
    authored.put("type", "document");
    authored.put("description", "A document");

    final var doc = properties(DocumentParameter.fromDocumentType(object("doc", authored)));

    @SuppressWarnings("unchecked")
    final var mapped = (Map<String, Object>) doc.get("doc");
    final var expected = new LinkedHashMap<>(handleSchema("A document"));
    expected.put("title", "Doc");
    assertThat(mapped).isEqualTo(expected);
    assertThat(List.copyOf(mapped.keySet()))
        .containsExactly("type", "description", "properties", "required", "title");
  }

  @Test
  void fromDocumentTypeLeavesTheDocumentTypeInDataKeywordsAsItIs() {
    final var document = Map.<String, Object>of("type", "document");
    final var inputSchema =
        object(
            "kind", Map.of("type", "string", "enum", List.of("a", document)),
            "payload", Map.of("type", "object", "default", document),
            "fixed", Map.of("type", "object", "const", document),
            "sample", Map.of("type", "object", "examples", List.of(document)));

    assertThat(DocumentParameter.fromDocumentType(inputSchema)).isEqualTo(inputSchema);
  }

  @Test
  void fromDocumentTypeLeavesPropertyNamesInDependenciesAsTheyAre() {
    final var inputSchema =
        object(
            "first",
                Map.of("type", "object", "dependentRequired", Map.of("type", List.of("document"))),
            "second",
                Map.of("type", "object", "dependencies", Map.of("type", List.of("document"))));

    assertThat(DocumentParameter.fromDocumentType(inputSchema)).isEqualTo(inputSchema);
  }

  @Test
  void fromDocumentTypeLeavesATypeArrayWithoutTheDocumentTypeAsItIs() {
    final var inputSchema = object("value", Map.of("type", List.of("string", "null")));

    assertThat(DocumentParameter.fromDocumentType(inputSchema)).isEqualTo(inputSchema);
  }

  static Stream<Map<String, Object>> schemasWithTheDocumentTypeWhereNoParameterCanSit() {
    final var document = Map.<String, Object>of("type", "document");
    final var string = string();
    final var itemsNextToProperties = new LinkedHashMap<String, Object>();
    itemsNextToProperties.put("type", "object");
    itemsNextToProperties.put("properties", Map.of("file", document));
    itemsNextToProperties.put("items", document);
    return Stream.of(
        Map.of("anyOf", List.of(string, document)),
        Map.of("oneOf", List.of(Map.of("type", "number"), document)),
        Map.of("anyOf", List.of(document)),
        Map.of(
            "anyOf", List.of(Map.of("type", "number"), Map.of("oneOf", List.of(string, document)))),
        Map.of("anyOf", List.of(string, object("file", document))),
        Map.of("type", "array", "items", Map.of("anyOf", List.of(string, document))),
        object("file", Map.of("anyOf", List.of(document, string))),
        Map.of("type", List.of("string", "document")),
        Map.of("type", List.of("document")),
        Map.of("type", List.of("string", "document"), "anyOf", List.of(string)),
        Map.of("type", "array", "items", Map.of("type", List.of("string", "document"))),
        Map.of("allOf", List.of(document)),
        Map.of("allOf", List.of(Map.of("type", List.of("string", "document")))),
        Map.of("not", document),
        Map.of("type", "object", "additionalProperties", document),
        Map.of("type", "array", "prefixItems", List.of(document)),
        Map.of("$defs", Map.of("file", document)),
        itemsNextToProperties,
        object("default", Map.of("anyOf", List.of(string, document))),
        object("enum", Map.of("type", List.of("string", "document"))),
        Map.of("$defs", Map.of("examples", document)),
        Map.of("type", "object", "dependencies", Map.of("type", document)));
  }

  @ParameterizedTest
  @MethodSource("schemasWithTheDocumentTypeWhereNoParameterCanSit")
  void fromDocumentTypeRejectsTheDocumentTypeWhereNoDocumentParameterCanSit(
      Map<String, Object> schema) {
    final var inputSchema = object("note", string(), "body", schema);

    assertThatThrownBy(() -> DocumentParameter.fromDocumentType(inputSchema))
        .isInstanceOf(MisplacedDocumentTypeException.class)
        .hasMessage(
            "Parameter 'body': The type 'document' can only be the type of a parameter, of the items of an array or of an object property. Use a separate parameter for a document.");
  }

  private static Map<String, Object> handleSchema(@Nullable String description) {
    final var expected = new LinkedHashMap<String, Object>();
    expected.put("type", "object");
    if (description != null) {
      expected.put("description", description);
    }
    expected.put(
        "properties",
        Map.of(
            "type",
            Map.of("type", "string", "enum", List.of("camunda.agenticai.document")),
            "id",
            Map.of("type", "string", "description", "The id of the document.")));
    expected.put("required", List.of("type", "id"));
    return expected;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> properties(Map<String, Object> schema) {
    return (Map<String, Object>) schema.get("properties");
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

  private static DocumentParameter document(Step... path) {
    return new DocumentParameter(List.of(path));
  }

  private static Step.Property property(String name) {
    return new Step.Property(name);
  }

  private static Step.Items items() {
    return new Step.Items();
  }
}
