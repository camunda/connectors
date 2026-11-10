/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.api.tool.DocumentSchemas;
import io.camunda.connector.agenticai.aiagent.document.DocumentParameterResolver.Resolution;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.connector.runtime.core.intrinsic.DisabledIntrinsicFunctionExecutor;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentParameterResolverTest {

  private final DocumentParameterResolver resolver =
      new DocumentParameterResolver(TestObjectMapperSupplier.INSTANCE);

  private final Document first = createDocument("first", "first.txt");
  private final Document second = createDocument("second", "second.txt");
  private final AgentDocuments documents =
      AgentDocuments.empty()
          .register(
              List.of(
                  AgentDocument.of(
                      first, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID)),
                  AgentDocument.of(
                      second, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID))));

  private final AgentDocumentId firstId = AgentDocumentId.of(first);
  private final AgentDocumentId secondId = AgentDocumentId.of(second);
  private final Map<String, Object> firstHandle = handle(firstId);
  private final Map<String, Object> secondHandle = handle(secondId);

  private static Map<String, Object> handle(AgentDocumentId id) {
    return Map.of("type", "camunda.agenticai.document", "id", id.value());
  }

  private static String notAHandle(String path) {
    return ("Parameter '%s' must be the handle of a document from the conversation: a JSON object"
            + " such as {\"type\":\"camunda.agenticai.document\",\"id\":\"doc_…\"}.")
        .formatted(path);
  }

  private static String unknownId(String path, String id) {
    return ("Parameter '%s' has the id '%s', but the conversation has no document with this id."
            + " Pass the handle of a document from the conversation: a JSON object such as"
            + " {\"type\":\"camunda.agenticai.document\",\"id\":\"doc_…\"}.")
        .formatted(path, id);
  }

  private static String holdsHandleButNoDocuments(String path) {
    return ("Parameter '%s' holds a document handle, but the conversation has no documents. Do not"
            + " pass a document handle.")
        .formatted(path);
  }

  private static String noDocuments(String path) {
    return ("Parameter '%s' takes a document, but the conversation has no documents. Do not pass a"
            + " document.")
        .formatted(path);
  }

  private Map<String, Object> reference(AgentDocumentId id) {
    return TestDocuments.referenceJson(documents.find(id).orElseThrow());
  }

  /** The handle JSON the model reads, with all its read-only fields. */
  private String handleJson(AgentDocumentId id) {
    return TestDocuments.handleText(documents, id);
  }

  private static String json(Object value) {
    try {
      return new ObjectMapper().writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void replacesAHandleObjectWithTheDocumentReference() {
    final var result = resolve(Map.of("doc", firstHandle, "other", "kept"), object("doc", DOC));

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved ->
                assertThat(resolved.arguments())
                    .containsEntry("doc", reference(firstId))
                    .containsEntry("other", "kept"));
    assertThat(reference(firstId))
        .containsEntry("camunda.document.type", "camunda")
        .containsKeys("storeId", "documentId", "contentHash");
  }

  @Test
  void ignoresTheOtherFieldsOfAHandleObject() {
    final var handle = new LinkedHashMap<String, Object>(firstHandle);
    handle.put("fileName", "renamed.txt");
    handle.put("size", 1);
    handle.put("unknown", List.of(1, 2));

    assertThat(resolve(Map.of("doc", handle), object("doc", DOC)))
        .isEqualTo(new Resolution.Resolved(Map.of("doc", reference(firstId))));
  }

  @Test
  void aDocumentParameterAcceptsAStringThatHoldsAHandleObject() {
    final var spaced = "\n " + json(secondHandle) + " \t";

    assertThat(resolve(Map.of("a", handleJson(firstId), "b", spaced), object("a", DOC, "b", DOC)))
        .isEqualTo(
            new Resolution.Resolved(Map.of("a", reference(firstId), "b", reference(secondId))));
  }

  @Test
  void aDocumentParameterRejectsTheBareIdOfAKnownAndOfAnUnknownDocument() {
    assertThat(
            resolve(
                Map.of("a", firstId.value(), "b", "\n " + secondId.value() + " \t"),
                object("a", DOC, "b", DOC)))
        .isEqualTo(new Resolution.Invalid(List.of(notAHandle("a"), notAHandle("b"))));
    assertThat(resolveRuntime(Map.of("a", "doc_000000000000"), object("a", DOC)))
        .isEqualTo(new Resolution.Invalid(List.of(notAHandle("a"))));
  }

  @Test
  void replacesEveryElementOfAnArray() {
    final var result =
        resolve(
            Map.of("docs", List.of(firstHandle, handleJson(secondId))), object("docs", array(DOC)));

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
            Map.of("file", firstHandle, "note", "n"),
            "rows",
            List.of(Map.of("file", secondHandle), Map.of("file", firstHandle)));

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
    final var withNull = new HashMap<String, Object>();
    withNull.put("doc", null);

    assertThat(resolve(Map.of(), object("doc", DOC))).isEqualTo(new Resolution.Resolved(Map.of()));
    assertThat(resolve(withNull, object("doc", DOC))).isEqualTo(new Resolution.Resolved(withNull));
  }

  @Test
  void doesNotModifyTheOriginalArguments() {
    final var arguments = Map.<String, Object>of("doc", firstHandle);

    resolve(arguments, object("doc", DOC));

    assertThat(arguments).containsEntry("doc", firstHandle);
  }

  @Test
  void rejectsEveryValueThatIsNotTheHandleOfAKnownDocumentForADocumentParameter() {
    final var withoutType = Map.of("id", firstId.value());
    final var otherType = Map.of("type", "other", "id", firstId.value());
    final var unknownHandle =
        Map.of("type", "camunda.agenticai.document", "id", "doc_000000000000");
    final var blankId = Map.of("type", "camunda.agenticai.document", "id", " ");
    final var numberId = Map.of("type", "camunda.agenticai.document", "id", 7);
    final var noId = Map.of("type", "camunda.agenticai.document");

    final Map<Object, String> withUnknownId =
        Map.of(
            unknownHandle,
            "doc_000000000000",
            "{\"type\":\"camunda.agenticai.document\",\"id\":\"doc_000000000000\"}",
            "doc_000000000000",
            "\n " + json(unknownHandle) + " \t",
            "doc_000000000000");
    withUnknownId.forEach((invalid, id) ->
        assertThat(resolve(Map.of("doc", invalid), object("doc", DOC)))
            .as("%s", invalid)
            .isEqualTo(new Resolution.Invalid(List.of(unknownId("doc", id)))));

    for (final Object invalid :
        List.of(
            withoutType,
            otherType,
            blankId,
            numberId,
            noId,
            Map.of(),
            "  ",
            "doc_000000000000",
            firstId.value(),
            "nope",
            "[" + json(firstHandle) + "]",
            "{\"id\":\"" + firstId + "\"}",
            "{not json",
            json(firstHandle) + " and more",
            json(firstHandle) + json(secondHandle),
            42,
            true,
            List.of(firstHandle))) {
      assertThat(resolve(Map.of("doc", invalid), object("doc", DOC)))
          .as("%s", invalid)
          .isEqualTo(new Resolution.Invalid(List.of(notAHandle("doc"))));
    }
  }

  @Test
  void tellsThatTheConversationHasNoDocumentsForEveryInvalidValueOfADocumentParameter() {
    final var withoutDocuments =
        AgentContext.empty().withFeatures(List.of(AgentFeatures.DOCUMENTS));
    final var definition = definition("upload", object("doc", DOC));

    for (final Object value :
        List.of(
            "doc_000000000000",
            "nope",
            "",
            42,
            Map.of("type", "camunda.agenticai.document", "id", "doc_000000000000"),
            Map.of("id", "x"))) {
      assertThat(
              resolver.check(
                  withoutDocuments,
                  new ToolCall("call", "upload", Map.of("doc", value)),
                  definition))
          .as("%s", value)
          .isEqualTo(new Resolution.Invalid(List.of(noDocuments("doc"))));
    }
  }

  @Test
  void reportsTheArrayIndexAndNestedPathOfEveryInvalidValue() {
    final var arguments = new HashMap<String, Object>();
    arguments.put("docs", Arrays.asList(firstHandle, "nope", null));
    arguments.put("req", Map.of("file", "gone", "other", handle(AgentDocumentId.of("gone"))));

    assertThat(
            resolve(
                arguments, object("docs", array(DOC), "req", object("file", DOC, "other", DOC))))
        .isEqualTo(
            new Resolution.Invalid(
                List.of(
                    notAHandle("docs[1]"),
                    notAHandle("docs[2]"),
                    notAHandle("req.file"),
                    unknownId("req.other", "gone"))));
  }

  @Test
  void aRuntimeCallReplacesHandlesByTheirIdInArraysAndNestedObjects() {
    final var arguments =
        Map.<String, Object>of(
            "doc",
            handleJson(firstId),
            "docs",
            List.of(secondHandle, firstHandle),
            "req",
            Map.of("file", secondHandle, "note", "n"),
            "other",
            "kept");

    final var result =
        resolveRuntime(
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
  void aRuntimeCallReportsTheSameProblemsAsAProcessCall() {
    final var arguments =
        Map.<String, Object>of(
            "a",
            handle(AgentDocumentId.of("nope")),
            "b",
            json(firstHandle) + "x",
            "c",
            42,
            "d",
            "doc_000000000000");
    final var schema = object("a", DOC, "b", DOC, "c", DOC, "d", DOC);

    final var expected =
        new Resolution.Invalid(
            List.of(unknownId("a", "nope"), notAHandle("b"), notAHandle("c"), notAHandle("d")));
    assertThat(resolveRuntime(arguments, schema)).isEqualTo(expected);
    assertThat(resolve(arguments, schema)).isEqualTo(expected);
  }

  @Test
  void reportsAnArrayParameterThatIsNotAnArray() {
    assertThat(resolve(Map.of("docs", firstHandle), object("docs", array(DOC))))
        .isEqualTo(new Resolution.Invalid(List.of("Parameter 'docs' must be an array.")));
  }

  @Test
  void resolvesPropertyNamesWithSpecialCharacters() {
    final var result = resolve(Map.of("a/b~c", firstHandle), object("a/b~c", DOC));

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved ->
                assertThat(resolved.arguments()).containsEntry("a/b~c", reference(firstId)));
  }

  @Test
  void resolvesTheCallOfAToolOfTheProcessWithTheToolDefinitionOfTheAgentContext() {
    final var call = new ToolCall("call", "upload", Map.of("doc", firstHandle, "other", "kept"));

    final var result = resolver.resolveProcessCall(agentContext(), call);

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
    final var call =
        new ToolCall("call", "upload", Map.of("doc", handle(AgentDocumentId.of("nope"))));

    final var result = resolver.resolveProcessCall(agentContext(), call);

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Invalid.class,
            invalid -> assertThat(invalid.message()).isEqualTo(unknownId("doc", "nope")));
  }

  @Test
  void joinsTheProblemsOfSeveralInvalidValuesIntoOneMessage() {
    final var call =
        new ToolCall(
            "call",
            "upload-many",
            Map.of(
                "docs", List.of(handle(AgentDocumentId.of("a")), handle(AgentDocumentId.of("b")))));

    final var result = resolver.resolveProcessCall(agentContext(), call);

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Invalid.class,
            invalid ->
                assertThat(invalid.message())
                    .isEqualTo(unknownId("docs[0]", "a") + " " + unknownId("docs[1]", "b")));
  }

  @Test
  void leavesTheCallOfAToolWithoutDocumentParametersAsItIs() {
    final var call = new ToolCall("call", "weather", Map.of("doc", "nope"));

    final var result = resolver.resolveProcessCall(agentContext(), call);

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved -> assertThat(resolved.arguments()).isEqualTo(call.arguments()));
  }

  @Test
  void leavesTheCallOfAToolTheAgentDoesNotKnowAsItIs() {
    final var call = new ToolCall("call", "unknown", Map.of("doc", "nope"));

    final var result = resolver.resolveProcessCall(agentContext(), call);

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved -> assertThat(resolved.arguments()).isEqualTo(call.arguments()));
  }

  @Test
  void anAgentInstanceWithoutTheDocumentsFeatureHasNoDocumentParameters() {
    final var call = new ToolCall("call", "upload", Map.of("doc", "bogus"));
    final var withoutDocuments = agentContext().withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS));

    assertThat(resolver.resolveProcessCall(withoutDocuments, call))
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved -> assertThat(resolved.arguments()).isEqualTo(call.arguments()));
    assertThat(
            resolver.resolveRuntimeCall(
                withoutDocuments, call, definition("upload", object("doc", DOC))))
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved -> assertThat(resolved.arguments()).isEqualTo(call.arguments()));
  }

  @Test
  void resolvesTheCallOfARuntimeToolToTheIdsOfTheDocuments() {
    final var call = new ToolCall("call", "c8__read", Map.of("file", firstHandle, "other", "kept"));

    final var result =
        resolver.resolveRuntimeCall(
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
    final var call =
        new ToolCall("call", "c8__read", Map.of("file", handle(AgentDocumentId.of("none"))));

    final var result =
        resolver.resolveRuntimeCall(
            agentContext(), call, definition("c8__read", object("file", DOC)));

    assertThat(result).isEqualTo(new Resolution.Invalid(List.of(unknownId("file", "none"))));
  }

  @Test
  void checksTheCallOfAToolOfEitherKindLikeTheCallOfARuntimeTool() {
    final var definition = definition("upload", object("doc", DOC));
    final var valid = new ToolCall("call", "upload", Map.of("doc", firstHandle));
    final var invalid =
        new ToolCall("call", "upload", Map.of("doc", handle(AgentDocumentId.of("nope"))));

    assertThat(resolver.check(agentContext(), valid, definition))
        .isEqualTo(new Resolution.Resolved(Map.of("doc", firstId)));
    assertThat(resolver.check(agentContext(), invalid, definition))
        .isEqualTo(new Resolution.Invalid(List.of(unknownId("doc", "nope"))));
    assertThat(
            resolver.check(
                agentContext().withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS)),
                invalid,
                definition))
        .isEqualTo(new Resolution.Resolved(invalid.arguments()));
  }

  @Test
  void leavesTheCallOfARuntimeToolWithoutDocumentParametersAsItIs() {
    final var call = new ToolCall("call", "c8__read", Map.of("file", "bogus"));

    final var result =
        resolver.resolveRuntimeCall(
            agentContext(), call, definition("c8__read", object("file", Map.of("type", "string"))));

    assertThat(result)
        .isInstanceOfSatisfying(
            Resolution.Resolved.class,
            resolved -> assertThat(resolved.arguments()).isEqualTo(call.arguments()));
  }

  @Test
  void theOutcomeIsTheSameEveryTimeItIsComputedFromTheSameContext() {
    final var call = new ToolCall("call", "upload", Map.of("doc", firstHandle));
    final var context = agentContext();

    assertThat(resolver.resolveProcessCall(context, call))
        .isEqualTo(resolver.resolveProcessCall(context, call));
  }

  @Test
  void aUnionParameterResolvesAnObjectWithTheHandleType() {
    final var handle = new LinkedHashMap<String, Object>(firstHandle);
    handle.put("fileName", "first.txt");

    assertThat(
            resolve(Map.of("a", handle, "b", secondHandle), object("a", UNION, "b", ONE_OF_UNION)))
        .isEqualTo(
            new Resolution.Resolved(Map.of("a", reference(firstId), "b", reference(secondId))));
  }

  @Test
  void aUnionParameterKeepsEveryStringThatIsNoHandleAsText() {
    final var arguments =
        Map.<String, Object>of(
            "plain", "hello",
            "empty", "",
            "padded", "  padded  ",
            "bareId", firstId.value(),
            "json", "{\"a\":1}",
            "notJson", "{not json",
            "handleAndMore", handleJson(firstId) + " and more");

    assertThat(
            resolve(
                arguments,
                object(
                    "plain", UNION,
                    "empty", UNION,
                    "padded", UNION,
                    "bareId", UNION,
                    "json", UNION,
                    "notJson", UNION,
                    "handleAndMore", UNION)))
        .isEqualTo(new Resolution.Resolved(arguments));
  }

  @Test
  void aUnionParameterResolvesAStringThatHoldsAHandleAsJson() {
    final var spaced = "\n " + json(secondHandle) + " \t";

    assertThat(
            resolve(Map.of("a", handleJson(firstId), "b", spaced), object("a", UNION, "b", UNION)))
        .isEqualTo(
            new Resolution.Resolved(Map.of("a", reference(firstId), "b", reference(secondId))));
    assertThat(
            resolve(Map.of("doc", json(handle(AgentDocumentId.of("none")))), object("doc", UNION)))
        .isEqualTo(new Resolution.Invalid(List.of(unknownId("doc", "none"))));
  }

  @Test
  void aUnionOfOneDocumentOrAListOfDocumentsResolvesEitherShape() {
    final var oneOrList = Map.<String, Object>of("anyOf", List.of(DOC, array(DOC)));
    final var schema = object("files", oneOrList);

    assertThat(resolve(Map.of("files", firstHandle), schema))
        .isEqualTo(new Resolution.Resolved(Map.of("files", reference(firstId))));
    assertThat(resolve(Map.of("files", List.of(firstHandle, handleJson(secondId))), schema))
        .isEqualTo(
            new Resolution.Resolved(
                Map.of("files", List.of(reference(firstId), reference(secondId)))));
    assertThat(
            resolve(
                Map.of("files", List.of(firstHandle, handle(AgentDocumentId.of("none")))), schema))
        .isEqualTo(new Resolution.Invalid(List.of(unknownId("files[1]", "none"))));
    assertThat(resolveRuntime(Map.of("files", List.of(secondHandle, firstHandle)), schema))
        .isEqualTo(new Resolution.Resolved(Map.of("files", List.of(secondId, firstId))));
  }

  @Test
  void aUnionOfTextOrAFileWithACaptionResolvesTheFileInTheObject() {
    final var textOrFile =
        Map.<String, Object>of(
            "anyOf", List.of(string(), object("file", DOC, "caption", string())));
    final var schema = object("message", textOrFile);

    assertThat(resolve(Map.of("message", "hello"), schema))
        .isEqualTo(new Resolution.Resolved(Map.of("message", "hello")));
    assertThat(resolve(Map.of("message", Map.of("file", firstHandle, "caption", "c")), schema))
        .isEqualTo(
            new Resolution.Resolved(
                Map.of("message", Map.of("file", reference(firstId), "caption", "c"))));
    assertThat(
            resolve(Map.of("message", Map.of("file", handle(AgentDocumentId.of("none")))), schema))
        .isEqualTo(new Resolution.Invalid(List.of(unknownId("message.file", "none"))));
    assertThat(
            resolveRuntime(Map.of("message", Map.of("file", firstHandle, "caption", "c")), schema))
        .isEqualTo(
            new Resolution.Resolved(Map.of("message", Map.of("file", firstId, "caption", "c"))));
  }

  @Test
  void aUnionParameterResolvesAHandleAsJsonInsideObjectsAndArrays() {
    final var arguments =
        Map.<String, Object>of(
            "value",
            Map.of(
                "file", handleJson(firstId),
                "more", List.of(Map.of("file", firstHandle), "text", 3),
                "other", Map.of("type", "other")));

    assertThat(resolve(arguments, object("value", UNION)))
        .isEqualTo(
            new Resolution.Resolved(
                Map.of(
                    "value",
                    Map.of(
                        "file", reference(firstId),
                        "more", List.of(Map.of("file", reference(firstId)), "text", 3),
                        "other", Map.of("type", "other")))));
  }

  @Test
  void aUnionParameterTellsThatTheConversationHasNoDocumentsForAHandle() {
    final var withoutDocuments =
        AgentContext.empty().withFeatures(List.of(AgentFeatures.DOCUMENTS));
    final var definition = definition("send", object("body", UNION));

    for (final Object handle : List.of(firstHandle, handleJson(firstId))) {
      assertThat(
              resolver.check(
                  withoutDocuments,
                  new ToolCall("call", "send", Map.of("body", handle)),
                  definition))
          .as("%s", handle)
          .isEqualTo(new Resolution.Invalid(List.of(holdsHandleButNoDocuments("body"))));
    }
    assertThat(
            resolver.check(
                withoutDocuments,
                new ToolCall("call", "send", Map.of("body", List.of("text", firstHandle))),
                definition))
        .isEqualTo(new Resolution.Invalid(List.of(holdsHandleButNoDocuments("body[1]"))));
    assertThat(
            resolver.check(
                withoutDocuments, new ToolCall("call", "send", Map.of("body", "text")), definition))
        .isEqualTo(new Resolution.Resolved(Map.of("body", "text")));
  }

  @Test
  void aUnionParameterRejectsAnObjectWithTheHandleTypeAndAnUnknownIdOrNoId() {
    assertThat(
            resolve(
                Map.of(
                    "doc", Map.of("type", "camunda.agenticai.document", "id", "doc_000000000000")),
                object("doc", UNION)))
        .isEqualTo(new Resolution.Invalid(List.of(unknownId("doc", "doc_000000000000"))));
    for (final Object invalid :
        List.of(
            Map.of("type", "camunda.agenticai.document"),
            Map.of("type", "camunda.agenticai.document", "id", 5),
            Map.of("type", "camunda.agenticai.document", "id", " "))) {
      assertThat(resolve(Map.of("doc", invalid), object("doc", UNION)))
          .as("%s", invalid)
          .isEqualTo(new Resolution.Invalid(List.of(notAHandle("doc"))));
    }
  }

  @Test
  void aUnionParameterPassesOtherObjectsAndValuesThatAreNotStringsAsTheyAre() {
    final var arguments = new HashMap<String, Object>();
    arguments.put("number", 42);
    arguments.put("flag", true);
    arguments.put("withoutType", Map.of("id", firstId.value()));
    arguments.put("otherType", Map.of("type", "other", "id", firstId.value()));
    arguments.put("list", List.of(firstId.value(), "x"));
    arguments.put("nothing", null);

    assertThat(
            resolve(
                arguments,
                object(
                    "number",
                    UNION,
                    "flag",
                    UNION,
                    "withoutType",
                    UNION,
                    "otherType",
                    UNION,
                    "list",
                    UNION,
                    "nothing",
                    UNION)))
        .isEqualTo(new Resolution.Resolved(arguments));
  }

  @Test
  void aUnionWithAnotherObjectAlternativePassesAnObjectWithoutTheHandleTypeAsItIs() {
    final var other = Map.<String, Object>of("city", "Berlin");
    final var arguments = Map.<String, Object>of("where", other, "doc", firstHandle);

    assertThat(
            resolve(
                arguments,
                object(
                    "where",
                    Map.of("anyOf", List.of(object("city", string()), DOC)),
                    "doc",
                    Map.of("oneOf", List.of(object("city", string()), DOC)))))
        .isEqualTo(new Resolution.Resolved(Map.of("where", other, "doc", reference(firstId))));
  }

  @Test
  void aUnionParameterInAnArrayResolvesEveryElementThatIsAHandleObject() {
    final var elements =
        Arrays.<Object>asList(secondHandle, "text", 7, firstId.value(), null, Map.of("k", "v"));

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
  void aUnionParameterInAnArrayReportsTheIndexOfAHandleWithAnUnknownId() {
    final var result =
        resolve(
            Map.of("items", List.of("text", handle(AgentDocumentId.of("none")))),
            object("items", array(UNION)));

    assertThat(result).isEqualTo(new Resolution.Invalid(List.of(unknownId("items[1]", "none"))));
  }

  @Test
  void aUnionParameterInsideObjectsResolvesLikeATopLevelOne() {
    final var arguments =
        Map.<String, Object>of(
            "req",
            Map.of("file", firstHandle, "note", "n"),
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
    assertThat(
            resolve(
                Map.of("doc", firstHandle, "text", firstId.value()),
                object("doc", DOC, "text", UNION)))
        .isEqualTo(
            new Resolution.Resolved(Map.of("doc", reference(firstId), "text", firstId.value())));
    assertThat(
            resolve(
                Map.of("doc", firstId.value(), "text", firstId.value()),
                object("doc", DOC, "text", UNION)))
        .isEqualTo(new Resolution.Invalid(List.of(notAHandle("doc"))));
    assertThat(resolve(Map.of("doc", "nope", "text", "nope"), object("doc", DOC, "text", UNION)))
        .isEqualTo(new Resolution.Invalid(List.of(notAHandle("doc"))));
  }

  @Test
  void aRuntimeCallHandsAUnionParameterTheIdOfAHandleAndKeepsTextAsText() {
    final var arguments =
        Map.<String, Object>of(
            "handle",
            secondHandle,
            "bare",
            firstId.value(),
            "text",
            "hello",
            "list",
            List.of(secondHandle, "hello", 3),
            "doc",
            handleJson(secondId));

    final var result =
        resolveRuntime(
            arguments,
            object(
                "handle", UNION, "bare", UNION, "text", UNION, "list", array(UNION), "doc", DOC));

    assertThat(result)
        .isEqualTo(
            new Resolution.Resolved(
                Map.of(
                    "handle",
                    secondId,
                    "bare",
                    firstId.value(),
                    "text",
                    "hello",
                    "list",
                    List.of(secondId, "hello", 3),
                    "doc",
                    secondId)));
    assertThat(
            resolveRuntime(
                Map.of("handle", handle(AgentDocumentId.of("none"))), object("handle", UNION)))
        .isEqualTo(new Resolution.Invalid(List.of(unknownId("handle", "none"))));
  }

  @Test
  void resolvesAUnionParameterOfTheCallOfAToolOfTheProcessToTheReferenceOfAHandle() {
    final var handle = new ToolCall("call", "send", Map.of("body", firstHandle, "other", "kept"));
    final var text = new ToolCall("call", "send", Map.of("body", "hello"));

    assertThat(resolver.resolveProcessCall(agentContext(), handle))
        .isEqualTo(new Resolution.Resolved(Map.of("body", reference(firstId), "other", "kept")));
    assertThat(resolver.resolveProcessCall(agentContext(), text))
        .isEqualTo(new Resolution.Resolved(Map.of("body", "hello")));
  }

  @Test
  void rejectsTheCallOfAToolOfTheProcessWithAHandleOfAnUnknownDocumentInAUnionParameter() {
    final var call =
        new ToolCall("call", "send", Map.of("body", handle(AgentDocumentId.of("none"))));

    assertThat(resolver.resolveProcessCall(agentContext(), call))
        .isEqualTo(new Resolution.Invalid(List.of(unknownId("body", "none"))));
  }

  @Test
  void resolvesAUnionParameterOfTheCallOfARuntimeToolToTheIdOfAHandleAndKeepsTextAsText() {
    final var definition = definition("c8__send", object("body", UNION));
    final var handle = new ToolCall("call", "c8__send", Map.of("body", firstHandle));
    final var text = new ToolCall("call", "c8__send", Map.of("body", firstId.value()));

    assertThat(resolver.resolveRuntimeCall(agentContext(), handle, definition))
        .isEqualTo(new Resolution.Resolved(Map.of("body", firstId)));
    assertThat(resolver.resolveRuntimeCall(agentContext(), text, definition))
        .isEqualTo(new Resolution.Resolved(Map.of("body", firstId.value())));
  }

  @Test
  void rejectsTheCallOfARuntimeToolWithAHandleOfAnUnknownDocumentInAUnionParameter() {
    final var call =
        new ToolCall("call", "c8__send", Map.of("body", handle(AgentDocumentId.of("none"))));

    assertThat(
            resolver.resolveRuntimeCall(
                agentContext(), call, definition("c8__send", object("body", UNION))))
        .isEqualTo(new Resolution.Invalid(List.of(unknownId("body", "none"))));
  }

  @Test
  void anAgentInstanceWithoutTheDocumentsFeatureHasNoUnionParameters() {
    final var call = new ToolCall("call", "send", Map.of("body", firstHandle));
    final var withoutDocuments = agentContext().withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS));

    assertThat(resolver.resolveProcessCall(withoutDocuments, call))
        .isEqualTo(new Resolution.Resolved(call.arguments()));
  }

  private AgentContext agentContext() {
    return AgentContext.empty()
        .withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS))
        .withDocuments(documents)
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

  private static final Map<String, Object> DOC = DocumentSchemas.document(null);

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
    return resolver.resolveProcessCall(
        agentContext().withToolDefinitions(List.of(definition)),
        new ToolCall("call", "tool", arguments));
  }

  private Resolution resolveRuntime(
      Map<String, Object> arguments, Map<String, Object> inputSchema) {
    final var definition = ToolDefinition.builder().name("tool").inputSchema(inputSchema).build();
    return resolver.resolveRuntimeCall(
        agentContext(), new ToolCall("call", "tool", arguments), definition);
  }

  @Test
  void writesTheReferenceOfADocumentAsDataWithTheMapperOfTheRuntime() {
    // the mapper of the runtime reads documents and intrinsic functions in any object it converts
    final var runtimeMapper =
        new ObjectMapper()
            .registerModule(
                new JacksonModuleDocumentDeserializer(
                    mock(DocumentFactory.class), new DisabledIntrinsicFunctionExecutor()));
    final var withFunctionLikeProperty =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
            .create(
                DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                    .contentType("text/plain")
                    .fileName("data.txt")
                    .customProperties(
                        Map.of(
                            "transform",
                            Map.of("camunda.function.type", "base64", "params", List.of("x"))))
                    .build());
    final var agentDocument =
        AgentDocument.of(
            withFunctionLikeProperty, AgentDocumentOrigin.toolCall("search", "call-1"));

    final var definition =
        ToolDefinition.builder().name("tool").inputSchema(object("doc", DOC)).build();
    final var context =
        AgentContext.empty()
            .withFeatures(List.of(AgentFeatures.DOCUMENTS))
            .withDocuments(AgentDocuments.empty().register(List.of(agentDocument)))
            .withToolDefinitions(List.of(definition));

    final var result =
        new DocumentParameterResolver(runtimeMapper)
            .resolveProcessCall(
                context, new ToolCall("call", "tool", Map.of("doc", handle(agentDocument.id()))));

    assertThat(result)
        .isEqualTo(
            new Resolution.Resolved(Map.of("doc", TestDocuments.referenceJson(agentDocument))));
  }
}
