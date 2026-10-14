/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.document.DocumentParameterResolver.Resolution;
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
  private final String firstTag = tag(firstId);
  private final String secondTag = tag(secondId);

  private static String tag(String id) {
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

  private Map<String, Object> reference(String id) {
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
    assertThat(resolve(Map.of("doc", firstId), object("doc", DOC)))
        .isEqualTo(new Resolution.Invalid(List.of(problem("doc", firstId))));
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
  void acceptsATagWithFurtherAttributes() {
    final var tag =
        "<doc id=\"" + firstId + "\" fileName=\"first.txt\" contentType=\"text/plain\" />";

    assertThat(resolve(Map.of("doc", tag), object("doc", DOC)))
        .isEqualTo(new Resolution.Resolved(Map.of("doc", reference(firstId))));
  }

  @Test
  void acceptsATagWithSurroundingWhitespaceAndWithoutASpaceBeforeTheEnd() {
    final var spaced = "\n  " + firstTag + "  \t";
    final var compact = "<doc id=\"" + secondId + "\"/>";

    assertThat(resolve(Map.of("a", spaced, "b", compact), object("a", DOC, "b", DOC)))
        .isEqualTo(
            new Resolution.Resolved(Map.of("a", reference(firstId), "b", reference(secondId))));
  }

  @Test
  void usesTheIdAttributeOfATagWhateverItsPositionAndUnescapesIt() {
    final var tag = "<doc fileName=\"a&amp;b.txt\" toolCallId=\"x\" id=\"" + firstId + "\" />";

    assertThat(resolve(Map.of("doc", tag), object("doc", DOC)))
        .isEqualTo(new Resolution.Resolved(Map.of("doc", reference(firstId))));
  }

  @Test
  void reportsATagWithAnUnknownIdWithTheTag() {
    final var tag = "<doc id=\"nope\" fileName=\"x\" />";

    assertThat(resolve(Map.of("doc", tag), object("doc", DOC)))
        .isEqualTo(new Resolution.Invalid(List.of(problem("doc", tag))));
  }

  @Test
  void rejectsATagWithTextAroundIt() {
    final var leading = "see " + firstTag;
    final var trailing = firstTag + " please";

    assertThat(resolve(Map.of("a", leading, "b", trailing), object("a", DOC, "b", DOC)))
        .isEqualTo(new Resolution.Invalid(List.of(problem("a", leading), problem("b", trailing))));
  }

  @Test
  void rejectsTwoTags() {
    final var tags = firstTag + secondTag;
    final var spaced = firstTag + " " + secondTag;

    assertThat(resolve(Map.of("a", tags, "b", spaced), object("a", DOC, "b", DOC)))
        .isEqualTo(new Resolution.Invalid(List.of(problem("a", tags), problem("b", spaced))));
  }

  @Test
  void rejectsATagWithoutAnIdOrThatIsNotClosed() {
    final var noId = "<doc fileName=\"first.txt\" />";
    final var blankId = "<doc id=\"\" />";
    final var open = "<doc id=\"" + firstId + "\">";
    final var otherElement = "<document id=\"" + firstId + "\" />";

    assertThat(
            resolve(
                Map.of("a", noId, "b", blankId, "c", open, "d", otherElement),
                object("a", DOC, "b", DOC, "c", DOC, "d", DOC)))
        .isEqualTo(
            new Resolution.Invalid(
                List.of(
                    problem("a", noId),
                    problem("b", blankId),
                    problem("c", open),
                    problem("d", otherElement))));
  }

  @Test
  void rejectsAnEscapedTag() {
    final var escaped = "&lt;doc id=\"" + firstId + "\" /&gt;";

    assertThat(resolve(Map.of("doc", escaped), object("doc", DOC)))
        .isEqualTo(new Resolution.Invalid(List.of(problem("doc", escaped))));
  }

  @Test
  void rejectsAnIdThatIsPartOfTheText() {
    assertThat(resolve(Map.of("doc", "the file " + firstId), object("doc", DOC)))
        .isInstanceOf(Resolution.Invalid.class);
  }

  @Test
  void acceptsTagsInArraysAndNestedObjects() {
    final var result =
        resolve(
            Map.of("docs", List.of(firstTag, secondTag), "req", Map.of("file", secondTag)),
            object("docs", array(DOC), "req", object("file", DOC)));

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved -> {
              assertThat(resolved.arguments().get("docs"))
                  .isEqualTo(List.of(reference(firstId), reference(secondId)));
              assertThat(resolved.arguments().get("req"))
                  .isEqualTo(Map.of("file", reference(secondId)));
            });
  }

  @Test
  void reportsInvalidTagsInArraysAndNestedObjects() {
    final var twoTags = firstTag + secondTag;
    final var unknown = "<doc id=\"x\" />";
    final var result =
        resolve(
            Map.of("docs", List.of(firstTag, twoTags), "req", Map.of("file", unknown)),
            object("docs", array(DOC), "req", object("file", DOC)));

    assertThat(result)
        .isEqualTo(
            new Resolution.Invalid(
                List.of(problem("docs[1]", twoTags), problem("req.file", unknown))));
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
    final var arguments = Map.<String, Object>of("a", "nope", "b", twoTags, "c", 42, "d", firstId);
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
    arguments.put("b", Map.of("documentId", firstId));
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
  void aUnionParameterResolvesAValueThatIsExactlyOneTag() {
    final var tag = "<doc id=\"" + firstId + "\" fileName=\"first.txt\" />";
    final var spaced = "\n <doc id=\"" + secondId + "\"/> \t";

    assertThat(resolve(Map.of("a", tag, "b", spaced), object("a", UNION, "b", ONE_OF_UNION)))
        .isEqualTo(
            new Resolution.Resolved(Map.of("a", reference(firstId), "b", reference(secondId))));
  }

  @Test
  void aUnionParameterUsesTheIdAttributeOfATagWhateverItsPositionAndUnescapesIt() {
    final var tag = "<doc fileName=\"a&amp;b.txt\" toolCallId=\"x\" id=\"" + firstId + "\" />";

    assertThat(resolve(Map.of("doc", tag), object("doc", UNION)))
        .isEqualTo(new Resolution.Resolved(Map.of("doc", reference(firstId))));
  }

  @Test
  void aUnionParameterKeepsAPlainStringAsItIs() {
    final var arguments = Map.<String, Object>of("a", "hello", "b", "", "c", "  padded  ");

    assertThat(resolve(arguments, object("a", UNION, "b", UNION, "c", UNION)))
        .isEqualTo(new Resolution.Resolved(arguments));
  }

  @Test
  void aUnionParameterKeepsABareKnownIdAsText() {
    final var arguments = Map.<String, Object>of("a", firstId, "b", " " + secondId + "\n");

    assertThat(resolve(arguments, object("a", UNION, "b", UNION)))
        .isEqualTo(new Resolution.Resolved(arguments));
  }

  @Test
  void aUnionParameterKeepsATagInsideLongerTextAsText() {
    final var leading = "see <doc id=\"" + firstId + "\" />";
    final var trailing = "<doc id=\"" + firstId + "\" /> please";
    final var twoTags = "<doc id=\"" + firstId + "\" /><doc id=\"" + secondId + "\" />";
    final var noId = "<doc fileName=\"first.txt\" />";
    final var escaped = "&lt;doc id=\"" + firstId + "\" /&gt;";
    final var unknownInText = "see <doc id=\"nope\" />";
    final var arguments =
        Map.<String, Object>of(
            "a", leading, "b", trailing, "c", twoTags, "d", noId, "e", escaped, "f", unknownInText);

    assertThat(
            resolve(
                arguments,
                object("a", UNION, "b", UNION, "c", UNION, "d", UNION, "e", UNION, "f", UNION)))
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
  void aUnionParameterReportsATagWithAnUnknownIdWithItsOwnTextForRuntimeTools() {
    final var tag = "<doc id=\"nope\" fileName=\"x\" />";

    assertThat(resolveIds(Map.of("doc", tag), object("doc", UNION)))
        .isEqualTo(new Resolution.Invalid(List.of(unionProblem("doc", "nope"))));
    assertThat(resolveIds(Map.of("doc", tag), object("doc", DOC)))
        .isEqualTo(new Resolution.Invalid(List.of(problem("doc", tag))));
    assertThat(resolveIds(Map.of("doc", "nope"), object("doc", UNION)))
        .isEqualTo(new Resolution.Resolved(Map.of("doc", "nope")));
  }

  @Test
  void aUnionParameterLeavesValuesThatAreNotStringsAlone() {
    final var arguments = new java.util.HashMap<String, Object>();
    arguments.put("number", 42);
    arguments.put("flag", true);
    arguments.put("object", Map.of("documentId", firstId));
    arguments.put("list", List.of(firstId, "x"));
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
    final var elements = Arrays.<Object>asList(tag, "text", 7, firstId, null, Map.of("k", "v"));

    final var result = resolve(Map.of("items", elements), object("items", array(UNION)));

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved ->
                assertThat(resolved.arguments().get("items"))
                    .isEqualTo(
                        Arrays.<Object>asList(
                            reference(secondId), "text", 7, firstId, null, Map.of("k", "v"))));
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
            "req", Map.of("file", tag, "note", "n"), "rows", List.of(Map.of("file", firstId)));

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
                    List.of(Map.of("file", firstId)))));
  }

  @Test
  void aDocumentParameterKeepsItsRulesNextToAUnionParameter() {
    final var arguments = Map.<String, Object>of("doc", firstTag, "text", firstId);

    assertThat(resolve(arguments, object("doc", DOC, "text", UNION)))
        .isEqualTo(new Resolution.Resolved(Map.of("doc", reference(firstId), "text", firstId)));
    assertThat(resolve(Map.of("doc", firstId, "text", firstId), object("doc", DOC, "text", UNION)))
        .isEqualTo(new Resolution.Invalid(List.of(problem("doc", firstId))));
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
            firstId,
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
                    firstId,
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
  void resolveAllResolvesTheTagOfAUnionParameter() {
    final var definition =
        ToolDefinition.builder().name("send").inputSchema(object("body", UNION)).build();
    final var tagCall = new ToolCall("1", "send", Map.of("body", "<doc id=\"" + firstId + "\" />"));
    final var textCall = new ToolCall("2", "send", Map.of("body", "just text"));

    final var result =
        DocumentParameterResolver.resolveAll(
            List.of(tagCall, textCall), List.of(definition), registry);

    assertThat(result)
        .containsExactly(tagCall.withArguments(Map.of("body", reference(firstId))), textCall);
  }

  @Test
  void resolveAllOnlyTouchesToolCallsOfToolsWithDocumentParameters() {
    final var withDocs =
        ToolDefinition.builder().name("upload").inputSchema(object("doc", DOC)).build();
    final var withoutDocs = ToolDefinition.builder().name("other").inputSchema(Map.of()).build();
    final var uploadCall = new ToolCall("1", "upload", Map.of("doc", firstTag));
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

  private static Document createDocument(String content, String fileName) {
    return new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
        .create(
            DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .fileName(fileName)
                .build());
  }
}
