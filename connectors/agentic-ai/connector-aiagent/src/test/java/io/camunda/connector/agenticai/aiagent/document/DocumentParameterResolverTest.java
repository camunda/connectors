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

  private static String unionProblem(String path, String id) {
    return "Parameter '%s' holds a <doc /> tag with the id '%s'. That is not a known document."
            .formatted(path, id)
        + " Pass the tag of a document exactly as you read it, or pass other text.";
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

  @Test
  void aUnionParameterResolvesAValueThatIsExactlyOneTag() {
    final var tag = "<doc id=\"" + firstId + "\" fileName=\"first.txt\" />";
    final var spaced = "\n <doc id=\"" + secondId + "\"/> \t";

    assertThat(resolve(Map.of("a", tag, "b", spaced), object("a", UNION, "b", ONE_OF_UNION)))
        .isEqualTo(
            new Resolution.Resolved(Map.of("a", reference(firstId), "b", reference(secondId))));
  }

  @Test
  void aUnionParameterKeepsAPlainStringAsItIs() {
    final var arguments = Map.<String, Object>of("a", "hello", "b", "", "c", "  padded  ");

    assertThat(resolve(arguments, object("a", UNION, "b", UNION, "c", UNION)))
        .isEqualTo(new Resolution.Resolved(arguments));
  }

  @Test
  void aUnionParameterKeepsABareKnownIdAsText() {
    final var arguments = Map.<String, Object>of("a", firstId.value(), "b", " " + secondId + "\n");

    assertThat(resolve(arguments, object("a", UNION, "b", UNION)))
        .isEqualTo(new Resolution.Resolved(arguments));
  }

  @Test
  void aUnionParameterReportsATagWithAnUnknownIdWithItsOwnText() {
    final var tag = "<doc id=\"nope\" fileName=\"x\" />";

    assertThat(resolve(Map.of("doc", tag), object("doc", UNION)))
        .isEqualTo(new Resolution.Invalid(List.of(unionProblem("doc", "nope"))));
    assertThat(resolve(Map.of("doc", tag), object("doc", DOC)))
        .isEqualTo(new Resolution.Invalid(List.of(problem("doc", tag))));
  }

  @Test
  void aUnionParameterLeavesValuesThatAreNotStringsAlone() {
    final var arguments = new java.util.HashMap<String, Object>();
    arguments.put("number", 42);
    arguments.put("flag", true);
    arguments.put("object", Map.of("documentId", firstId.value()));
    arguments.put("list", List.of(firstId.value(), "x"));
    arguments.put("nothing", null);

    assertThat(
            resolve(
                arguments,
                object(
                    "number", UNION, "flag", UNION, "object", UNION, "list", UNION, "nothing",
                    UNION)))
        .isEqualTo(new Resolution.Resolved(arguments));
  }

  @Test
  void aUnionParameterInAnArrayResolvesEveryElementThatIsATag() {
    final var tag = "<doc id=\"" + secondId + "\" />";
    final var elements =
        Arrays.<Object>asList(tag, "text", 7, firstId.value(), null, Map.of("k", "v"));

    final var result = resolve(Map.of("items", elements), object("items", array(UNION)));

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved ->
                assertThat(resolved.arguments().get("items"))
                    .isEqualTo(
                        Arrays.<Object>asList(
                            reference(secondId),
                            "text",
                            7,
                            firstId.value(),
                            null,
                            Map.of("k", "v"))));
  }

  @Test
  void aUnionParameterInAnArrayReportsTheIndexOfATagWithAnUnknownId() {
    final var result =
        resolve(
            Map.of("items", List.of("text", "<doc id=\"nope\" />")), object("items", array(UNION)));

    assertThat(result).isEqualTo(new Resolution.Invalid(List.of(unionProblem("items[1]", "nope"))));
  }

  @Test
  void aUnionParameterInsideObjectsResolvesLikeATopLevelOne() {
    final var tag = "<doc id=\"" + firstId + "\" />";
    final var arguments =
        Map.<String, Object>of(
            "req",
            Map.of("file", tag, "note", "n"),
            "rows",
            List.of(Map.of("file", firstId.value())));

    final var result =
        resolve(
            arguments,
            object(
                "req",
                object("file", UNION, "note", string()),
                "rows",
                array(object("file", UNION))));

    assertThat(result)
        .isEqualTo(
            new Resolution.Resolved(
                Map.of(
                    "req",
                    Map.of("file", reference(firstId), "note", "n"),
                    "rows",
                    List.of(Map.of("file", firstId.value())))));
  }

  @Test
  void aDocumentParameterKeepsItsRulesNextToAUnionParameter() {
    final var arguments = Map.<String, Object>of("doc", firstTag, "text", firstId.value());

    assertThat(resolve(arguments, object("doc", DOC, "text", UNION)))
        .isEqualTo(
            new Resolution.Resolved(Map.of("doc", reference(firstId), "text", firstId.value())));
    assertThat(
            resolve(
                Map.of("doc", firstId.value(), "text", firstId.value()),
                object("doc", DOC, "text", UNION)))
        .isEqualTo(new Resolution.Invalid(List.of(problem("doc", firstId.value()))));
    assertThat(resolve(Map.of("doc", "nope", "text", "nope"), object("doc", DOC, "text", UNION)))
        .isEqualTo(new Resolution.Invalid(List.of(problem("doc", "nope"))));
  }

  @Test
  void resolveIdsHandsAUnionParameterThePlainIdOfATagAndKeepsTextAsText() {
    final var tag = "<doc id=\"" + secondId + "\" fileName=\"x\" />";
    final var arguments =
        Map.<String, Object>of(
            "tagged",
            tag,
            "bare",
            firstId.value(),
            "text",
            "hello",
            "list",
            List.of(tag, "hello", 3),
            "doc",
            tag);

    final var result =
        resolveIds(
            arguments,
            object(
                "tagged", UNION, "bare", UNION, "text", UNION, "list", array(UNION), "doc", DOC));

    assertThat(result)
        .isEqualTo(
            new Resolution.Resolved(
                Map.of(
                    "tagged",
                    secondId,
                    "bare",
                    firstId.value(),
                    "text",
                    "hello",
                    "list",
                    List.of(secondId, "hello", 3),
                    "doc",
                    secondId)));
    assertThat(resolveIds(Map.of("tagged", "<doc id=\"nope\" />"), object("tagged", UNION)))
        .isEqualTo(new Resolution.Invalid(List.of(unionProblem("tagged", "nope"))));
  }

  @Test
  void resolvesAUnionParameterOfTheCallOfAToolOfTheProcessToTheReferenceOfATag() {
    final var tagged = new ToolCall("call", "send", Map.of("body", firstTag, "other", "kept"));
    final var text = new ToolCall("call", "send", Map.of("body", "hello"));

    assertThat(DocumentParameterResolver.resolveProcessCall(agentContext(), tagged))
        .isEqualTo(new Resolution.Resolved(Map.of("body", reference(firstId), "other", "kept")));
    assertThat(DocumentParameterResolver.resolveProcessCall(agentContext(), text))
        .isEqualTo(new Resolution.Resolved(Map.of("body", "hello")));
  }

  @Test
  void rejectsTheCallOfAToolOfTheProcessWithATagOfAnUnknownDocumentInAUnionParameter() {
    final var call = new ToolCall("call", "send", Map.of("body", tag(DocumentId.of("none"))));

    assertThat(DocumentParameterResolver.resolveProcessCall(agentContext(), call))
        .isEqualTo(new Resolution.Invalid(List.of(unionProblem("body", "none"))));
  }

  @Test
  void resolvesAUnionParameterOfTheCallOfARuntimeToolToTheIdOfATagAndKeepsTextAsText() {
    final var definition = definition("c8__send", object("body", UNION));
    final var tagged = new ToolCall("call", "c8__send", Map.of("body", firstTag));
    final var text = new ToolCall("call", "c8__send", Map.of("body", firstId.value()));

    assertThat(DocumentParameterResolver.resolveRuntimeCall(agentContext(), tagged, definition))
        .isEqualTo(new Resolution.Resolved(Map.of("body", firstId)));
    assertThat(DocumentParameterResolver.resolveRuntimeCall(agentContext(), text, definition))
        .isEqualTo(new Resolution.Resolved(Map.of("body", firstId.value())));
  }

  @Test
  void rejectsTheCallOfARuntimeToolWithATagOfAnUnknownDocumentInAUnionParameter() {
    final var call = new ToolCall("call", "c8__send", Map.of("body", tag(DocumentId.of("none"))));

    assertThat(
            DocumentParameterResolver.resolveRuntimeCall(
                agentContext(), call, definition("c8__send", object("body", UNION))))
        .isEqualTo(new Resolution.Invalid(List.of(unionProblem("body", "none"))));
  }

  @Test
  void anAgentInstanceWithoutTheDocumentsFeatureHasNoUnionParameters() {
    final var call = new ToolCall("call", "send", Map.of("body", firstTag));
    final var withoutDocuments = agentContext().withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS));

    assertThat(DocumentParameterResolver.resolveProcessCall(withoutDocuments, call))
        .isEqualTo(new Resolution.Resolved(call.arguments()));
  }

  private AgentContext agentContext() {
    return AgentContext.empty()
        .withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS))
        .withDocumentRegistry(registry)
        .withToolDefinitions(
            List.of(
                definition("upload", object("doc", DOC)),
                definition("upload-many", object("docs", array(DOC))),
                definition("send", object("body", UNION)),
                definition("weather", object("doc", Map.of("type", "string")))));
  }

  private static ToolDefinition definition(String name, Map<String, Object> inputSchema) {
    return ToolDefinition.builder().name(name).inputSchema(inputSchema).build();
  }

  private static final Map<String, Object> DOC =
      Map.of("type", "string", "format", "camunda-document-id");

  private static final Map<String, Object> UNION =
      Map.of("anyOf", List.of(Map.of("type", "string"), DOC));

  private static final Map<String, Object> ONE_OF_UNION =
      Map.of("oneOf", List.of(Map.of("type", "string"), DOC, Map.of("type", "number")));

  private static Map<String, Object> string() {
    return Map.of("type", "string");
  }

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
