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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

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
