/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.agent.DocumentParameterResolver.Resolution;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentParameterResolverTest {

  private final Document first = createDocument("first", "first.txt");
  private final Document second = createDocument("second", "second.txt");
  private final DocumentRegistry registry =
      DocumentRegistry.empty()
          .withDocuments(
              List.of(
                  new IngestedDocument(first, DocumentSource.userMessage(null)),
                  new IngestedDocument(second, DocumentSource.userMessage(null))));

  private final String firstId = DocumentHandle.idFor(first);
  private final String secondId = DocumentHandle.idFor(second);

  private static String notAString(String path) {
    return "Parameter '%s' must be the exact id of a known document (the id attribute of a"
            .formatted(path)
        + " <doc/> tag in the conversation), a string.";
  }

  private static String problem(String path, String value) {
    return "Parameter '%s' must be the exact id of a known document (the id attribute of a"
            .formatted(path)
        + " <doc/> tag in the conversation), got '%s'.".formatted(value);
  }

  private Map<String, Object> reference(String id) {
    return registry.findById(id).orElseThrow().reference();
  }

  @Test
  void replacesATopLevelIdWithTheDocumentReference() {
    final var result = resolve(Map.of("doc", firstId, "other", "kept"), object("doc", DOC));

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved ->
                assertThat(resolved.arguments())
                    .containsEntry("doc", reference(firstId))
                    .containsEntry("other", "kept"));
    assertThat(reference(firstId)).containsKey("camunda.document.type");
  }

  @Test
  void replacesEveryElementOfAnArray() {
    final var result =
        resolve(Map.of("docs", List.of(firstId, secondId)), object("docs", array(DOC)));

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved ->
                assertThat(resolved.arguments().get("docs"))
                    .isEqualTo(List.of(reference(firstId), reference(secondId))));
  }

  @Test
  void replacesNestedObjectAndArrayOfObjectsParameters() {
    final var arguments =
        Map.<String, Object>of(
            "req",
            Map.of("file", firstId, "note", "n"),
            "rows",
            List.of(Map.of("file", secondId), Map.of("file", firstId)));

    final var result =
        resolve(arguments, object("req", object("file", DOC), "rows", array(object("file", DOC))));

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved -> {
              assertThat(resolved.arguments().get("req"))
                  .isEqualTo(Map.of("file", reference(firstId), "note", "n"));
              assertThat(resolved.arguments().get("rows"))
                  .isEqualTo(
                      List.of(
                          Map.of("file", reference(secondId)), Map.of("file", reference(firstId))));
            });
  }

  @Test
  void leavesAbsentAndNullParametersAlone() {
    final var withNull = new java.util.HashMap<String, Object>();
    withNull.put("doc", null);

    assertThat(resolve(Map.of(), object("doc", DOC))).isEqualTo(new Resolution.Resolved(Map.of()));
    assertThat(resolve(withNull, object("doc", DOC))).isEqualTo(new Resolution.Resolved(withNull));
  }

  @Test
  void doesNotModifyTheOriginalArguments() {
    final var arguments = Map.<String, Object>of("doc", firstId);

    resolve(arguments, object("doc", DOC));

    assertThat(arguments).containsEntry("doc", firstId);
  }

  @Test
  void reportsAnUnknownId() {
    assertThat(resolve(Map.of("doc", "nope"), object("doc", DOC)))
        .isEqualTo(new Resolution.Invalid(List.of(problem("doc", "nope"))));
  }

  @Test
  void reportsTheArrayIndexAndNestedPathOfEveryInvalidValue() {
    final var result =
        resolve(
            Map.of("docs", List.of(firstId, "nope"), "req", Map.of("file", "gone")),
            object("docs", array(DOC), "req", object("file", DOC)));

    assertThat(result)
        .isEqualTo(
            new Resolution.Invalid(
                List.of(problem("docs[1]", "nope"), problem("req.file", "gone"))));
  }

  @Test
  void rejectsATagInsteadOfTheId() {
    final var tag = "<doc id=\"" + firstId + "\" fileName=\"first.txt\" />";

    assertThat(resolve(Map.of("doc", tag), object("doc", DOC)))
        .isEqualTo(new Resolution.Invalid(List.of(problem("doc", tag))));
  }

  @Test
  void rejectsAnEscapedTag() {
    final var escaped = "&lt;doc id=\"" + firstId + "\" /&gt;";

    assertThat(resolve(Map.of("doc", escaped), object("doc", DOC)))
        .isEqualTo(new Resolution.Invalid(List.of(problem("doc", escaped))));
  }

  @Test
  void rejectsAnIdWithSurroundingWhitespace() {
    assertThat(resolve(Map.of("doc", " " + firstId + "\n"), object("doc", DOC)))
        .isInstanceOf(Resolution.Invalid.class);
  }

  @Test
  void rejectsAnIdThatIsPartOfTheText() {
    assertThat(resolve(Map.of("doc", "the file " + firstId), object("doc", DOC)))
        .isInstanceOf(Resolution.Invalid.class);
  }

  @Test
  void rejectsTagsInArraysAndNestedObjects() {
    final var tag = "<doc id=\"" + secondId + "\" />";
    final var result =
        resolve(
            Map.of("docs", List.of(firstId, tag), "req", Map.of("file", tag)),
            object("docs", array(DOC), "req", object("file", DOC)));

    assertThat(result)
        .isEqualTo(
            new Resolution.Invalid(List.of(problem("docs[1]", tag), problem("req.file", tag))));
  }

  @Test
  void substituteKnownReferencesReplacesAKnownId() {
    final var definition =
        ToolDefinition.builder().name("tool").inputSchema(object("doc", DOC)).build();

    assertThat(
            DocumentParameterResolver.substituteKnownReferences(
                Map.of("doc", firstId), definition, registry))
        .containsEntry("doc", reference(firstId));
  }

  @Test
  void substituteKnownReferencesLeavesATagAsGiven() {
    final var definition =
        ToolDefinition.builder().name("tool").inputSchema(object("doc", DOC)).build();
    final var tag = "<doc id=\"" + firstId + "\" fileName=\"x\" />";

    assertThat(
            DocumentParameterResolver.substituteKnownReferences(
                Map.of("doc", tag), definition, registry))
        .containsEntry("doc", tag);
  }

  @Test
  void reportsABlankValueAsAnUnknownId() {
    assertThat(resolve(Map.of("doc", "  "), object("doc", DOC)))
        .isInstanceOfSatisfying(
            Resolution.Invalid.class,
            invalid -> assertThat(invalid.problems()).containsExactly(problem("doc", "  ")));
  }

  @Test
  void reportsNonStringValues() {
    final var arguments = new java.util.HashMap<String, Object>();
    arguments.put("a", 42);
    arguments.put("b", Map.of("documentId", firstId));
    arguments.put("c", Arrays.asList(firstId, null));

    assertThat(resolve(arguments, object("a", DOC, "b", DOC, "c", array(DOC))))
        .isInstanceOfSatisfying(
            Resolution.Invalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly(notAString("a"), notAString("b"), notAString("c[1]")));
  }

  @Test
  void reportsAnArrayParameterThatIsNotAnArray() {
    assertThat(resolve(Map.of("docs", firstId), object("docs", array(DOC))))
        .isEqualTo(new Resolution.Invalid(List.of("Parameter 'docs' must be an array.")));
  }

  @Test
  void resolvesPropertyNamesWithSpecialCharacters() {
    final var result = resolve(Map.of("a/b~c", firstId), object("a/b~c", DOC));

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved ->
                assertThat(resolved.arguments()).containsEntry("a/b~c", reference(firstId)));
  }

  @Test
  void substituteKnownReferencesReplacesKnownIdsAndLeavesEverythingElseAsGiven() {
    final var definition =
        ToolDefinition.builder()
            .name("tool")
            .inputSchema(object("doc", DOC, "docs", array(DOC), "req", object("file", DOC)))
            .build();
    final var arguments =
        Map.<String, Object>of(
            "doc",
            firstId,
            "docs",
            List.of(secondId, "nope"),
            "req",
            Map.of("file", "gone", "note", "n"),
            "other",
            "kept");

    final var result =
        DocumentParameterResolver.substituteKnownReferences(arguments, definition, registry);

    assertThat(result)
        .containsEntry("doc", reference(firstId))
        .containsEntry("docs", List.of(reference(secondId), "nope"))
        .containsEntry("req", Map.of("file", "gone", "note", "n"))
        .containsEntry("other", "kept");
    assertThat(arguments).containsEntry("doc", firstId);
  }

  @Test
  void substituteKnownReferencesToleratesValuesOfAnUnexpectedShape() {
    final var definition =
        ToolDefinition.builder()
            .name("tool")
            .inputSchema(object("doc", DOC, "docs", array(DOC), "req", object("file", DOC)))
            .build();
    final var arguments = new java.util.HashMap<String, Object>();
    arguments.put("doc", 42);
    arguments.put("docs", firstId);
    arguments.put("req", "not an object");

    assertThat(DocumentParameterResolver.substituteKnownReferences(arguments, definition, registry))
        .isEqualTo(arguments);
    assertThat(DocumentParameterResolver.substituteKnownReferences(Map.of(), definition, registry))
        .isEmpty();
  }

  @Test
  void substituteKnownReferencesLeavesToolsWithoutDocumentParametersAlone() {
    final var definition =
        ToolDefinition.builder().name("other").inputSchema(Map.of("type", "object")).build();

    assertThat(
            DocumentParameterResolver.substituteKnownReferences(
                Map.of("doc", firstId), definition, registry))
        .containsEntry("doc", firstId);
  }

  @Test
  void resolveAllOnlyTouchesToolCallsOfToolsWithDocumentParameters() {
    final var withDocs =
        ToolDefinition.builder().name("upload").inputSchema(object("doc", DOC)).build();
    final var withoutDocs = ToolDefinition.builder().name("other").inputSchema(Map.of()).build();
    final var uploadCall = new ToolCall("1", "upload", Map.of("doc", firstId));
    final var otherCall = new ToolCall("2", "other", Map.of("doc", firstId));
    final var unknownCall = new ToolCall("3", "gateway_tool", Map.of("doc", firstId));

    final var result =
        DocumentParameterResolver.resolveAll(
            List.of(uploadCall, otherCall, unknownCall), List.of(withDocs, withoutDocs), registry);

    assertThat(result)
        .containsExactly(
            uploadCall.withArguments(Map.of("doc", reference(firstId))), otherCall, unknownCall);
  }

  @Test
  void resolveAllRejectsToolCallsThatWereNotValidated() {
    final var definition =
        ToolDefinition.builder().name("upload").inputSchema(object("doc", DOC)).build();

    assertThatThrownBy(
            () ->
                DocumentParameterResolver.resolveAll(
                    List.of(new ToolCall("1", "upload", Map.of("doc", "nope"))),
                    List.of(definition),
                    registry))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nope");
  }

  private static final Map<String, Object> DOC =
      Map.of("type", "string", "format", "camunda-document-id");

  private static Map<String, Object> array(Map<String, Object> items) {
    return Map.of("type", "array", "items", items);
  }

  /** An object schema from alternating property names and schemas, in declaration order. */
  private static Map<String, Object> object(Object... namesAndSchemas) {
    final var properties = new LinkedHashMap<String, Object>();
    for (int i = 0; i < namesAndSchemas.length; i += 2) {
      properties.put((String) namesAndSchemas[i], namesAndSchemas[i + 1]);
    }
    return Map.of("type", "object", "properties", properties);
  }

  private Resolution resolve(Map<String, Object> arguments, Map<String, Object> inputSchema) {
    final var definition = ToolDefinition.builder().name("tool").inputSchema(inputSchema).build();
    return DocumentParameterResolver.resolve(
        new ToolCall("call", "tool", arguments), definition, registry);
  }

  private static Document createDocument(String content, String fileName) {
    return new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
        .create(
            DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .fileName(fileName)
                .build());
  }
}
