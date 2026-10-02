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
import java.util.ArrayList;
import java.util.Arrays;
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

  private Map<String, Object> reference(String id) {
    return registry.findById(id).orElseThrow().reference();
  }

  @Test
  void replacesATopLevelIdWithTheDocumentReference() {
    final var result = resolve(Map.of("doc", firstId, "other", "kept"), "/properties/doc");

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
        resolve(Map.of("docs", List.of(firstId, secondId)), "/properties/docs/items");

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
        resolve(
            arguments, "/properties/req/properties/file", "/properties/rows/items/properties/file");

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

    assertThat(resolve(Map.of(), "/properties/doc")).isEqualTo(new Resolution.Resolved(Map.of()));
    assertThat(resolve(withNull, "/properties/doc")).isEqualTo(new Resolution.Resolved(withNull));
  }

  @Test
  void doesNotModifyTheOriginalArguments() {
    final var arguments = Map.<String, Object>of("doc", firstId);

    resolve(arguments, "/properties/doc");

    assertThat(arguments).containsEntry("doc", firstId);
  }

  @Test
  void reportsAnUnknownId() {
    assertThat(resolve(Map.of("doc", "nope"), "/properties/doc"))
        .isEqualTo(
            new Resolution.Invalid(List.of("Unknown document id 'nope' for parameter 'doc'.")));
  }

  @Test
  void reportsTheArrayIndexAndNestedPathOfEveryInvalidValue() {
    final var result =
        resolve(
            Map.of("docs", List.of(firstId, "nope"), "req", Map.of("file", "gone")),
            "/properties/docs/items",
            "/properties/req/properties/file");

    assertThat(result)
        .isEqualTo(
            new Resolution.Invalid(
                List.of(
                    "Unknown document id 'nope' for parameter 'docs[1]'.",
                    "Unknown document id 'gone' for parameter 'req.file'.")));
  }

  @Test
  void reportsABlankId() {
    assertThat(resolve(Map.of("doc", "  "), "/properties/doc"))
        .isInstanceOfSatisfying(
            Resolution.Invalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly(
                        "Parameter 'doc' must be the id of a known document (a non-blank string)."));
  }

  @Test
  void reportsNonStringValues() {
    final var arguments = new java.util.HashMap<String, Object>();
    arguments.put("a", 42);
    arguments.put("b", Map.of("documentId", firstId));
    arguments.put("c", Arrays.asList(firstId, null));

    assertThat(resolve(arguments, "/properties/a", "/properties/b", "/properties/c/items"))
        .isInstanceOfSatisfying(
            Resolution.Invalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly(
                        "Parameter 'a' must be the id of a known document (a non-blank string).",
                        "Parameter 'b' must be the id of a known document (a non-blank string).",
                        "Parameter 'c[1]' must be the id of a known document (a non-blank string)."));
  }

  @Test
  void reportsAnArrayParameterThatIsNotAnArray() {
    assertThat(resolve(Map.of("docs", firstId), "/properties/docs/items"))
        .isEqualTo(new Resolution.Invalid(List.of("Parameter 'docs' must be an array.")));
  }

  @Test
  void unescapesPropertyNamesOfThePointer() {
    final var result = resolve(Map.of("a/b~c", firstId), "/properties/a~1b~0c");

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved ->
                assertThat(resolved.arguments()).containsEntry("a/b~c", reference(firstId)));
  }

  @Test
  void resolveAllOnlyTouchesToolCallsOfToolsWithDocumentParameters() {
    final var withDocs =
        ToolDefinition.builder()
            .name("upload")
            .inputSchema(Map.of())
            .documentParameters(List.of("/properties/doc"))
            .build();
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
        ToolDefinition.builder()
            .name("upload")
            .inputSchema(Map.of())
            .documentParameters(List.of("/properties/doc"))
            .build();

    assertThatThrownBy(
            () ->
                DocumentParameterResolver.resolveAll(
                    List.of(new ToolCall("1", "upload", Map.of("doc", "nope"))),
                    List.of(definition),
                    registry))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nope");
  }

  private Resolution resolve(Map<String, Object> arguments, String... pointers) {
    final var definition =
        ToolDefinition.builder()
            .name("tool")
            .inputSchema(Map.of())
            .documentParameters(new ArrayList<>(Arrays.asList(pointers)))
            .build();
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
