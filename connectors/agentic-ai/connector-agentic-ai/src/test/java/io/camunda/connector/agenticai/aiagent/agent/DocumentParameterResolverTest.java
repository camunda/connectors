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
                  new IngestedDocument(first, null, null),
                  new IngestedDocument(second, null, null)));

  private final String firstId = DocumentHandle.idFor(first);
  private final String secondId = DocumentHandle.idFor(second);
  private final String firstTag = tag(firstId);
  private final String secondTag = tag(secondId);

  private static String tag(String id) {
    return "<doc id=\"" + id + "\" />";
  }

  private Map<String, Object> reference(String id) {
    return registry.findById(id).orElseThrow().reference();
  }

  @Test
  void replacesATopLevelIdWithTheDocumentReference() {
    final var result = resolve(Map.of("doc", firstTag, "other", "kept"), object("doc", DOC));

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
        resolve(Map.of("docs", List.of(firstTag, secondTag)), object("docs", array(DOC)));

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
            Map.of("file", firstTag, "note", "n"),
            "rows",
            List.of(Map.of("file", secondTag), Map.of("file", firstTag)));

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
    final var arguments = Map.<String, Object>of("doc", firstTag);

    resolve(arguments, object("doc", DOC));

    assertThat(arguments).containsEntry("doc", firstTag);
  }

  @Test
  void reportsAnUnknownId() {
    assertThat(resolve(Map.of("doc", tag("nope")), object("doc", DOC)))
        .isEqualTo(
            new Resolution.Invalid(List.of("Unknown document id 'nope' for parameter 'doc'.")));
  }

  @Test
  void reportsTheArrayIndexAndNestedPathOfEveryInvalidValue() {
    final var result =
        resolve(
            Map.of("docs", List.of(firstTag, tag("nope")), "req", Map.of("file", tag("gone"))),
            object("docs", array(DOC), "req", object("file", DOC)));

    assertThat(result)
        .isEqualTo(
            new Resolution.Invalid(
                List.of(
                    "Unknown document id 'nope' for parameter 'docs[1]'.",
                    "Unknown document id 'gone' for parameter 'req.file'.")));
  }

  @Test
  void readsOnlyTheIdAttributeOfATagThatCarriesOtherAttributes() {
    final var echoed =
        "<doc id=\""
            + firstId
            + "\" fileName=\"first.txt\" contentType=\"text/plain\""
            + " toolName=\"Download\" toolCallId=\"call_1\" />";

    assertThat(resolve(Map.of("doc", echoed), object("doc", DOC)))
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved -> assertThat(resolved.arguments()).containsEntry("doc", reference(firstId)));
  }

  @Test
  void toleratesWhitespaceAroundTheTagAndInsideIt() {
    assertThat(
            resolve(Map.of("doc", "\n  <doc  id = \"" + firstId + "\"/>  \n"), object("doc", DOC)))
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved -> assertThat(resolved.arguments()).containsEntry("doc", reference(firstId)));
  }

  @Test
  void rejectsABareIdWithAnActionableMessage() {
    assertThat(resolve(Map.of("doc", firstId), object("doc", DOC)))
        .isEqualTo(
            new Resolution.Invalid(
                List.of(
                    "Parameter 'doc' must be the <doc id=\"...\" /> tag of a known document, copied"
                        + " from the conversation, not a bare id or other text: '"
                        + firstId
                        + "'.")));
  }

  @Test
  void rejectsBareIdsInArraysAndNestedObjects() {
    final var result =
        resolve(
            Map.of("docs", List.of(firstTag, secondId), "req", Map.of("file", firstId)),
            object("docs", array(DOC), "req", object("file", DOC)));

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Invalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .hasSize(2)
                    .allSatisfy(problem -> assertThat(problem).contains("not a bare id"))
                    .anyMatch(problem -> problem.contains("'docs[1]'"))
                    .anyMatch(problem -> problem.contains("'req.file'")));
  }

  @Test
  void rejectsATagWithoutAnId() {
    assertThat(resolve(Map.of("doc", "<doc fileName=\"a.txt\" />"), object("doc", DOC)))
        .isInstanceOf(Resolution.Invalid.class);
  }

  @Test
  void substituteKnownReferencesLeavesABareIdAsGiven() {
    final var definition =
        ToolDefinition.builder().name("tool").inputSchema(object("doc", DOC)).build();

    assertThat(
            DocumentParameterResolver.substituteKnownReferences(
                Map.of("doc", firstId), definition, registry))
        .containsEntry("doc", firstId);
  }

  @Test
  void substituteKnownReferencesReadsTheIdOfATagWithOtherAttributes() {
    final var definition =
        ToolDefinition.builder().name("tool").inputSchema(object("doc", DOC)).build();

    assertThat(
            DocumentParameterResolver.substituteKnownReferences(
                Map.of("doc", "<doc id=\"" + firstId + "\" fileName=\"x\" />"),
                definition,
                registry))
        .containsEntry("doc", reference(firstId));
  }

  @Test
  void reportsABlankId() {
    assertThat(resolve(Map.of("doc", "  "), object("doc", DOC)))
        .isInstanceOfSatisfying(
            Resolution.Invalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly(
                        "Parameter 'doc' must be the <doc id=\"...\" /> tag of a known document (a non-blank string)."));
  }

  @Test
  void reportsNonStringValues() {
    final var arguments = new java.util.HashMap<String, Object>();
    arguments.put("a", 42);
    arguments.put("b", Map.of("documentId", firstId));
    arguments.put("c", Arrays.asList(firstTag, null));

    assertThat(resolve(arguments, object("a", DOC, "b", DOC, "c", array(DOC))))
        .isInstanceOfSatisfying(
            Resolution.Invalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly(
                        "Parameter 'a' must be the <doc id=\"...\" /> tag of a known document (a non-blank string).",
                        "Parameter 'b' must be the <doc id=\"...\" /> tag of a known document (a non-blank string).",
                        "Parameter 'c[1]' must be the <doc id=\"...\" /> tag of a known document (a non-blank string)."));
  }

  @Test
  void reportsAnArrayParameterThatIsNotAnArray() {
    assertThat(resolve(Map.of("docs", firstTag), object("docs", array(DOC))))
        .isEqualTo(new Resolution.Invalid(List.of("Parameter 'docs' must be an array.")));
  }

  @Test
  void resolvesPropertyNamesWithSpecialCharacters() {
    final var result = resolve(Map.of("a/b~c", firstTag), object("a/b~c", DOC));

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
            firstTag,
            "docs",
            List.of(secondTag, tag("nope")),
            "req",
            Map.of("file", tag("gone"), "note", "n"),
            "other",
            "kept");

    final var result =
        DocumentParameterResolver.substituteKnownReferences(arguments, definition, registry);

    assertThat(result)
        .containsEntry("doc", reference(firstId))
        .containsEntry("docs", List.of(reference(secondId), tag("nope")))
        .containsEntry("req", Map.of("file", tag("gone"), "note", "n"))
        .containsEntry("other", "kept");
    assertThat(arguments).containsEntry("doc", firstTag);
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
    arguments.put("docs", firstTag);
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
    final var uploadCall = new ToolCall("1", "upload", Map.of("doc", firstTag));
    final var otherCall = new ToolCall("2", "other", Map.of("doc", firstTag));
    final var unknownCall = new ToolCall("3", "gateway_tool", Map.of("doc", firstTag));

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
      Map.of("type", "string", "format", "camunda-document");

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
