/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.document.DocumentParameterResolver.Resolution;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.document.Document;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentParameterResolverTest {

  private final Document first = createDocument("first", "first.txt");
  private final Document second = createDocument("second", "second.txt");
  private final DocumentRegistry registry =
      DocumentRegistries.withDocuments(
          DocumentRegistry.empty(),
          List.of(
              new IngestedDocument(first, DocumentSource.userMessage()),
              new IngestedDocument(second, DocumentSource.userMessage())));

  private final DocumentId firstId = DocumentHandle.idFor(first);
  private final DocumentId secondId = DocumentHandle.idFor(second);
  private final String firstTag = tag(firstId);
  private final String secondTag = tag(secondId);

  private static String tag(DocumentId id) {
    return "<doc id=\"" + id + "\" />";
  }

  private static String notAString(String path) {
    return "Parameter '%s' must be the <doc /> tag of a known document, exactly as you read it,"
            .formatted(path)
        + " a string.";
  }

  private static String problem(String path, String value) {
    return "Parameter '%s' must be the <doc /> tag of a known document, exactly as you read it,"
            .formatted(path)
        + " got '%s'.".formatted(value);
  }

  private Map<String, Object> reference(DocumentId id) {
    return registry.findById(id).orElseThrow().reference();
  }

  @Test
  void replacesATopLevelTagWithTheDocumentReference() {
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
  void reportsAValueThatIsNotATag() {
    assertThat(resolve(Map.of("doc", "nope"), object("doc", DOC)))
        .isEqualTo(new Resolution.Invalid(List.of(problem("doc", "nope"))));
  }

  @Test
  void reportsABareIdOfAKnownDocument() {
    assertThat(resolve(Map.of("doc", firstId.value()), object("doc", DOC)))
        .isEqualTo(new Resolution.Invalid(List.of(problem("doc", firstId.value()))));
  }

  @Test
  void reportsTheArrayIndexAndNestedPathOfEveryInvalidValue() {
    final var result =
        resolve(
            Map.of("docs", List.of(firstTag, "nope"), "req", Map.of("file", "gone")),
            object("docs", array(DOC), "req", object("file", DOC)));

    assertThat(result)
        .isEqualTo(
            new Resolution.Invalid(
                List.of(problem("docs[1]", "nope"), problem("req.file", "gone"))));
  }

  @Test
  void reportsATagWithAnUnknownIdWithTheTag() {
    final var tag = "<doc id=\"nope\" fileName=\"x\" />";

    assertThat(resolve(Map.of("doc", tag), object("doc", DOC)))
        .isEqualTo(new Resolution.Invalid(List.of(problem("doc", tag))));
  }

  @Test
  void resolveIdsReplacesTagsByTheirIdInArraysAndNestedObjects() {
    final var tag = "<doc id=\"" + secondId + "\" fileName=\"x\" />";
    final var arguments =
        Map.<String, Object>of(
            "doc",
            firstTag,
            "docs",
            List.of(tag, firstTag),
            "req",
            Map.of("file", tag, "note", "n"),
            "other",
            "kept");

    final var result =
        resolveIds(
            arguments,
            object("doc", DOC, "docs", array(DOC), "req", object("file", DOC), "other", Map.of()));

    assertThat(result)
        .isEqualTo(
            new Resolution.Resolved(
                Map.of(
                    "doc",
                    firstId,
                    "docs",
                    List.of(secondId, firstId),
                    "req",
                    Map.of("file", secondId, "note", "n"),
                    "other",
                    "kept")));
  }

  @Test
  void resolveIdsReportsTheSameProblemsAsResolve() {
    final var twoTags = firstTag + secondTag;
    final var arguments =
        Map.<String, Object>of("a", "nope", "b", twoTags, "c", 42, "d", firstId.value());
    final var schema = object("a", DOC, "b", DOC, "c", DOC, "d", DOC);

    assertThat(resolveIds(arguments, schema)).isInstanceOf(Resolution.Invalid.class);
    assertThat(resolveIds(arguments, schema)).isEqualTo(resolve(arguments, schema));
  }

  @Test
  void reportsABlankValue() {
    assertThat(resolve(Map.of("doc", "  "), object("doc", DOC)))
        .isInstanceOfSatisfying(
            Resolution.Invalid.class,
            invalid -> assertThat(invalid.problems()).containsExactly(problem("doc", "  ")));
  }

  @Test
  void reportsNonStringValues() {
    final var arguments = new java.util.HashMap<String, Object>();
    arguments.put("a", 42);
    arguments.put("b", Map.of("documentId", firstId.value()));
    arguments.put("c", Arrays.asList(firstTag, null));

    assertThat(resolve(arguments, object("a", DOC, "b", DOC, "c", array(DOC))))
        .isInstanceOfSatisfying(
            Resolution.Invalid.class,
            invalid ->
                assertThat(invalid.problems())
                    .containsExactly(notAString("a"), notAString("b"), notAString("c[1]")));
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
  void resolvesTheCallOfAToolOfTheProcessWithTheToolDefinitionOfTheAgentContext() {
    final var call = new ToolCall("call", "upload", Map.of("doc", firstTag, "other", "kept"));

    final var result = DocumentParameterResolver.resolveProcessCall(agentContext(), call);

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved ->
                assertThat(resolved.arguments())
                    .containsEntry("doc", reference(firstId))
                    .containsEntry("other", "kept"));
  }

  @Test
  void rejectsTheCallOfAToolOfTheProcessThatDoesNotNameAKnownDocument() {
    final var call = new ToolCall("call", "upload", Map.of("doc", firstId.value()));

    final var result = DocumentParameterResolver.resolveProcessCall(agentContext(), call);

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Invalid.class,
            invalid -> assertThat(invalid.message()).isEqualTo(problem("doc", firstId.value())));
  }

  @Test
  void joinsTheProblemsOfSeveralInvalidValuesIntoOneMessage() {
    final var call = new ToolCall("call", "upload-many", Map.of("docs", List.of("a", "b")));

    final var result = DocumentParameterResolver.resolveProcessCall(agentContext(), call);

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Invalid.class,
            invalid ->
                assertThat(invalid.message())
                    .isEqualTo(problem("docs[0]", "a") + " " + problem("docs[1]", "b")));
  }

  @Test
  void leavesTheCallOfAToolWithoutDocumentParametersAsItIs() {
    final var call = new ToolCall("call", "weather", Map.of("doc", "nope"));

    final var result = DocumentParameterResolver.resolveProcessCall(agentContext(), call);

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved -> assertThat(resolved.arguments()).isEqualTo(call.arguments()));
  }

  @Test
  void leavesTheCallOfAToolTheAgentDoesNotKnowAsItIs() {
    final var call = new ToolCall("call", "unknown", Map.of("doc", "nope"));

    final var result = DocumentParameterResolver.resolveProcessCall(agentContext(), call);

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved -> assertThat(resolved.arguments()).isEqualTo(call.arguments()));
  }

  @Test
  void anAgentInstanceWithoutTheDocumentsFeatureHasNoDocumentParameters() {
    final var call = new ToolCall("call", "upload", Map.of("doc", "bogus"));
    final var withoutDocuments = agentContext().withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS));

    assertThat(DocumentParameterResolver.resolveProcessCall(withoutDocuments, call))
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved -> assertThat(resolved.arguments()).isEqualTo(call.arguments()));
    assertThat(
            DocumentParameterResolver.resolveRuntimeCall(
                withoutDocuments, call, definition("upload", object("doc", DOC))))
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved -> assertThat(resolved.arguments()).isEqualTo(call.arguments()));
  }

  @Test
  void resolvesTheCallOfARuntimeToolToTheIdsOfTheDocuments() {
    final var call = new ToolCall("call", "c8__read", Map.of("file", firstTag, "other", "kept"));

    final var result =
        DocumentParameterResolver.resolveRuntimeCall(
            agentContext(), call, definition("c8__read", object("file", DOC)));

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved ->
                assertThat(resolved.arguments())
                    .containsEntry("file", firstId)
                    .containsEntry("other", "kept"));
  }

  @Test
  void rejectsTheCallOfARuntimeToolThatDoesNotNameAKnownDocument() {
    final var call = new ToolCall("call", "c8__read", Map.of("file", tag(DocumentId.of("none"))));

    final var result =
        DocumentParameterResolver.resolveRuntimeCall(
            agentContext(), call, definition("c8__read", object("file", DOC)));

    assertThat(result).isInstanceOf(Resolution.Invalid.class);
  }

  @Test
  void leavesTheCallOfARuntimeToolWithoutDocumentParametersAsItIs() {
    final var call = new ToolCall("call", "c8__read", Map.of("file", "bogus"));

    final var result =
        DocumentParameterResolver.resolveRuntimeCall(
            agentContext(), call, definition("c8__read", object("file", Map.of("type", "string"))));

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved -> assertThat(resolved.arguments()).isEqualTo(call.arguments()));
  }

  @Test
  void theOutcomeIsTheSameEveryTimeItIsComputedFromTheSameContext() {
    final var call = new ToolCall("call", "upload", Map.of("doc", firstTag));
    final var context = agentContext();

    assertThat(DocumentParameterResolver.resolveProcessCall(context, call))
        .isEqualTo(DocumentParameterResolver.resolveProcessCall(context, call));
  }

  private AgentContext agentContext() {
    return AgentContext.empty()
        .withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS))
        .withDocumentRegistry(registry)
        .withToolDefinitions(
            List.of(
                definition("upload", object("doc", DOC)),
                definition("upload-many", object("docs", array(DOC))),
                definition("weather", object("doc", Map.of("type", "string")))));
  }

  private static ToolDefinition definition(String name, Map<String, Object> inputSchema) {
    return ToolDefinition.builder().name(name).inputSchema(inputSchema).build();
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

  private Resolution resolveIds(Map<String, Object> arguments, Map<String, Object> inputSchema) {
    final var definition = ToolDefinition.builder().name("tool").inputSchema(inputSchema).build();
    return DocumentParameterResolver.resolveIds(
        new ToolCall("call", "tool", arguments), definition, registry);
  }
}
