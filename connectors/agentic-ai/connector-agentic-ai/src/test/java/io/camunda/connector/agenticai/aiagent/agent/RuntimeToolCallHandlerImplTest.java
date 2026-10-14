/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestAgentExecutionContext.withConfiguration;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_CHAT_MODEL;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_SYSTEM_PROMPT;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistryImpl;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.tool.runtime.SaveNoteTool;
import io.camunda.connector.agenticai.aiagent.tool.runtime.StubRuntimeTool;
import io.camunda.connector.agenticai.aiagent.tool.runtime.builtin.ReturnDocumentTool;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RuntimeToolCallHandlerImplTest {

  private static final ToolDefinition UPLOAD =
      ToolDefinition.builder()
          .name("upload")
          .inputSchema(
              Map.of(
                  "type",
                  "object",
                  "properties",
                  Map.of("doc", Map.of("type", "string", "format", "camunda-document-id"))))
          .build();
  private static final Map<String, Object> TEXT_OR_DOCUMENT =
      Map.of(
          "anyOf",
          List.of(
              Map.of("type", "string"), Map.of("type", "string", "format", "camunda-document-id")));
  private static final ToolDefinition SEND =
      ToolDefinition.builder()
          .name("send")
          .inputSchema(Map.of("type", "object", "properties", Map.of("body", TEXT_OR_DOCUMENT)))
          .build();
  private static final ToolDefinition WEATHER =
      ToolDefinition.builder().name("getWeather").inputSchema(Map.of()).build();

  private static final AgentConfiguration WITH_MEMORY =
      new AgentConfiguration(
          TEST_CHAT_MODEL,
          TEST_SYSTEM_PROMPT,
          null,
          new MemoryConfiguration(null, 10),
          null,
          null,
          null);
  private static final AgentConfiguration WITHOUT_MEMORY =
      new AgentConfiguration(TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null);
  private static final AgentContext NEW_INSTANCE =
      AgentContext.empty().withFeatures(AgentContext.newInstanceFeatures());

  private final Document report = createDocument("report", "report.txt");
  private final String reportId = DocumentHandle.idFor(report);

  private final DocumentRegistry registry =
      DocumentRegistry.empty()
          .withDocuments(
              List.of(
                  new IngestedDocument(report, DocumentSource.toolCall("search", "call-1", null))));

  /**
   * The handler under test, run for an agent execution that has the configuration of the
   * conversation.
   */
  private record Handler(RuntimeToolCallHandler delegate) {

    RuntimeToolCallHandler.RuntimeToolCallSplit split(
        AgentConversation conversation, List<ToolCall> toolCalls) {
      return delegate.split(
          withConfiguration(conversation.configuration()), conversation, toolCalls);
    }

    /** Splits the batch and answers the calls the agent answers itself. */
    HandledToolCalls handle(AgentConversation conversation, List<ToolCall> toolCalls) {
      return HandledToolCalls.of(
          delegate, withConfiguration(conversation.configuration()), conversation, toolCalls);
    }

    List<ToolDefinition> availableToolDefinitions(
        AgentConfiguration configuration, AgentContext agentContext) {
      return delegate.availableToolDefinitions(withConfiguration(configuration), agentContext);
    }

    List<ToolDefinition> availableToolDefinitions(AgentConversation conversation) {
      return availableToolDefinitions(conversation.configuration(), conversation.toAgentContext());
    }
  }

  private final Handler handler = handlerOf(new SaveNoteTool());

  private static Handler handlerOf(RuntimeTool... tools) {
    return new Handler(new RuntimeToolCallHandlerImpl(new RuntimeToolRegistryImpl(List.of(tools))));
  }

  private static final RuntimeTool ECHO_TOOL =
      StubRuntimeTool.of(
          "acme_echo", (call, executionContext, conv) -> RuntimeToolResult.success("echo"));

  private static ToolDefinition definitionOf(String name, Map<String, Object> properties) {
    return ToolDefinition.builder()
        .name(name)
        .inputSchema(Map.of("type", "object", "properties", properties))
        .build();
  }

  private static AgentConversation conversationWithFeatures(
      Set<String> features, ToolCall... calls) {
    final var featureSet = new LinkedHashSet<>(features);
    return AgentConversation.rehydrate(
            WITHOUT_MEMORY.withToolDefinitions(List.of(WEATHER)),
            AgentContext.builder().state(AgentState.READY).features(featureSet).build(),
            TurnReconstructor.reconstruct(List.of()),
            null,
            List.of(userMessage("hi")))
        .ingest(assistantMessage("calling", List.of(calls)), AgentMetrics.empty());
  }

  private AgentConversation conversation(AgentConfiguration configuration, ToolCall... calls) {
    return AgentConversation.rehydrate(
            configuration.withToolDefinitions(List.of(UPLOAD, SEND, WEATHER)),
            AgentContext.builder()
                .state(AgentState.READY)
                .features(AgentContext.newInstanceFeatures())
                .documents(registry.entries())
                .build(),
            TurnReconstructor.reconstruct(List.of()),
            null,
            List.of(userMessage("hi")))
        .ingest(assistantMessage("calling", List.of(calls)), AgentMetrics.empty());
  }

  private static ToolCall note(String id, String text) {
    return new ToolCall(id, SaveNoteTool.NAME, Map.of("text", text));
  }

  private static final ToolCall WEATHER_CALL = new ToolCall("w", "getWeather", Map.of());

  private static String text(ToolCallResultContent result) {
    assertThat(result.content()).singleElement().isInstanceOf(TextContent.class);
    return ((TextContent) result.content().getFirst()).text();
  }

  @Test
  void leavesEveryCallToTheProcessWhenNoRuntimeToolIsRegistered() {
    final var split =
        handlerOf().handle(conversation(WITH_MEMORY, WEATHER_CALL), List.of(WEATHER_CALL));

    assertThat(split.hasRuntimeResults()).isFalse();
    assertThat(split.remainingCalls()).containsExactly(WEATHER_CALL);
  }

  @Test
  void runsARuntimeToolAndAnswersItsCall() {
    final var call = note("n1", "buy milk");

    final var split = handler.handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(split.answersAllCalls()).isTrue();
    assertThat(split.runtimeResults())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.id()).isEqualTo("n1");
              assertThat(result.name()).isEqualTo("c8__save_note");
              assertThat(text(result)).isEqualTo("Saved note #1");
              assertThat(result.completedAt()).isNotNull();
            });
  }

  @Test
  void appliesTheContextUpdateOfAResultToTheReturnedConversation() {
    final var call = note("n1", "buy milk");
    final var conversation = conversation(WITH_MEMORY, call);

    final var split = handler.handle(conversation, List.of(call));

    assertThat(SaveNoteTool.notesOf(split.conversation().toAgentContext().properties()))
        .containsExactly("buy milk");
    // the input conversation is immutable
    assertThat(conversation.toAgentContext().properties()).isEmpty();
  }

  @Test
  void aLaterCallOfTheBatchSeesTheStateAnEarlierCallLeft() {
    final var first = note("n1", "a");
    final var second = note("n2", "b");

    final var split =
        handler.handle(conversation(WITH_MEMORY, first, second), List.of(first, second));

    assertThat(split.runtimeResults())
        .extracting(r -> text(r))
        .containsExactly("Saved note #1", "Saved note #2");
    assertThat(SaveNoteTool.notesOf(split.conversation().toAgentContext().properties()))
        .containsExactly("a", "b");
  }

  @Test
  void splitsAMixedBatchAndKeepsTheOrderOfTheCalls() {
    final var first = note("n1", "a");
    final var second = note("n2", "b");
    final var other = new ToolCall("w2", "getWeather", Map.of("location", "MUC"));

    final var split =
        handler.handle(
            conversation(WITH_MEMORY, first, WEATHER_CALL, second, other),
            List.of(first, WEATHER_CALL, second, other));

    assertThat(split.answersAllCalls()).isFalse();
    assertThat(split.runtimeResults())
        .extracting(ToolCallResultContent::id)
        .containsExactly("n1", "n2");
    assertThat(split.remainingCalls()).containsExactly(WEATHER_CALL, other);
  }

  @Test
  void callsOfAnUnavailableRuntimeToolGoToTheProcess() {
    final var call = note("n1", "a");

    final var split = handler.handle(conversation(WITHOUT_MEMORY, call), List.of(call));

    assertThat(split.hasRuntimeResults()).isFalse();
    assertThat(split.remainingCalls()).containsExactly(call);
    assertThat(handler.availableToolDefinitions(WITHOUT_MEMORY, NEW_INSTANCE)).isEmpty();
    assertThat(handler.availableToolDefinitions(WITH_MEMORY, NEW_INSTANCE))
        .extracting(ToolDefinition::name)
        .containsExactly("c8__save_note");
  }

  @Test
  void answersAnErrorResultWithoutChangingTheState() {
    final var call = note("n1", " ");
    final var conversation = conversation(WITH_MEMORY, call);

    final var split = handler.handle(conversation, List.of(call));

    assertThat(split.runtimeResults())
        .singleElement()
        .satisfies(result -> assertThat(text(result)).isEqualTo("The note must not be blank."));
    assertThat(split.conversation().toAgentContext().properties()).isEmpty();
  }

  @Test
  void answersARuntimeToolExceptionWithItsMessage() {
    final var call = note("n1", "reject");

    final var split = handler.handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(split.runtimeResults())
        .singleElement()
        .satisfies(result -> assertThat(text(result)).isEqualTo("This note is not allowed."));
  }

  @Test
  void answersArgumentsOfTheWrongTypeWithAnErrorResultTheModelCanActOn() {
    record Args(int limit) {}
    final var tool =
        StubRuntimeTool.of(
            "c8__limited",
            (call, executionContext, conv) -> {
              call.argumentsAs(Args.class, TestObjectMapperSupplier.INSTANCE);
              return RuntimeToolResult.success("never");
            });
    final var call = new ToolCall("l1", "c8__limited", Map.of("limit", "many"));

    final var split = handlerOf(tool).handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(split.runtimeResults())
        .singleElement()
        .satisfies(
            result ->
                assertThat(text(result)).startsWith("Invalid arguments for tool 'c8__limited':"));
  }

  @Test
  void anUnexpectedExceptionOfAToolPropagates() {
    final var call = note("n1", "explode");
    final var conversation = conversation(WITH_MEMORY, call);

    assertThatThrownBy(() -> handler.handle(conversation, List.of(call)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("unexpected failure");
  }

  @Test
  void handsTheIdsOfTheDocumentParametersToARuntimeToolAfterCheckingThem() {
    final var received = new ArrayList<Object>();
    record Args(String file, List<String> files) {}
    final var tool =
        StubRuntimeTool.of(
            definitionOf(
                "c8__read_file",
                Map.of(
                    "file",
                    DocumentParameterSchema.documentSchema("The file"),
                    "files",
                    Map.of("type", "array", "items", Map.of("type", "string")))),
            (call, executionContext, conv) -> {
              final var args = call.argumentsAs(Args.class, TestObjectMapperSupplier.INSTANCE);
              received.add(args.file());
              received.add(args.files());
              return RuntimeToolResult.success("read");
            });
    final var call =
        new ToolCall("r1", "c8__read_file", Map.of("file", "<doc id=\"" + reportId + "\" />"));

    final var split = handlerOf(tool).handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(split.runtimeResults()).extracting(r -> text(r)).containsExactly("read");
    assertThat(received).containsExactly(reportId, null);
  }

  @Test
  void handsTheIdOfATagPassedToADocumentParameterOfARuntimeTool() {
    final var received = new ArrayList<Object>();
    final var tool =
        StubRuntimeTool.of(
            definitionOf(
                "c8__read_file",
                Map.of("file", DocumentParameterSchema.documentSchema("The file"))),
            (call, executionContext, conv) -> {
              received.add(call.arguments());
              return RuntimeToolResult.success("read");
            });
    final var arguments =
        Map.<String, Object>of(
            "file", "<doc id=\"" + reportId + "\" fileName=\"report.txt\" />", "other", "kept");
    final var call = new ToolCall("r1", "c8__read_file", arguments);

    final var split = handlerOf(tool).handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(split.runtimeResults()).extracting(r -> text(r)).containsExactly("read");
    assertThat(received).containsExactly(Map.of("file", reportId, "other", "kept"));
  }

  @Test
  void answersAValueThatIsNotATagOfARuntimeToolAndDoesNotRunIt() {
    final var runs = new AtomicInteger();
    final var tool =
        StubRuntimeTool.of(
            definitionOf(
                "c8__read_file",
                Map.of("file", DocumentParameterSchema.documentSchema("The file"))),
            (call, executionContext, conv) -> {
              runs.incrementAndGet();
              return RuntimeToolResult.success("read");
            });
    final var call = new ToolCall("r1", "c8__read_file", Map.of("file", "bogus"));

    final var split = handlerOf(tool).handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(runs).hasValue(0);
    assertThat(split.runtimeResults())
        .singleElement()
        .satisfies(
            result ->
                assertThat(text(result))
                    .isEqualTo(
                        "Parameter 'file' must be the <doc /> tag of a known document, exactly as you read it, got 'bogus'."));
  }

  @Test
  void handsTheIdOfATagAndOtherValuesAsTheyAreToAUnionParameterOfARuntimeTool() {
    final var received = new ArrayList<Object>();
    final var tool =
        StubRuntimeTool.of(
            definitionOf("c8__send", Map.of("body", TEXT_OR_DOCUMENT)),
            (call, executionContext, conv) -> {
              received.add(call.arguments().get("body"));
              return RuntimeToolResult.success("sent");
            });
    final var tagCall =
        new ToolCall("s1", "c8__send", Map.of("body", "<doc id=\"" + reportId + "\" />"));
    final var bareIdCall = new ToolCall("s2", "c8__send", Map.of("body", reportId));
    final var textCall = new ToolCall("s3", "c8__send", Map.of("body", "hello"));

    final var split =
        handlerOf(tool)
            .handle(
                conversation(WITH_MEMORY, tagCall, bareIdCall, textCall),
                List.of(tagCall, bareIdCall, textCall));

    assertThat(split.runtimeResults())
        .extracting(r -> text(r))
        .containsExactly("sent", "sent", "sent");
    assertThat(received).containsExactly(reportId, reportId, "hello");
  }

  @Test
  void answersATagWithAnUnknownIdOfAUnionParameterOfARuntimeToolAndDoesNotRunIt() {
    final var runs = new AtomicInteger();
    final var tool =
        StubRuntimeTool.of(
            definitionOf("c8__send", Map.of("body", TEXT_OR_DOCUMENT)),
            (call, executionContext, conv) -> {
              runs.incrementAndGet();
              return RuntimeToolResult.success("sent");
            });
    final var call = new ToolCall("s1", "c8__send", Map.of("body", "<doc id=\"bogus\" />"));

    final var split = handlerOf(tool).handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(runs).hasValue(0);
    assertThat(split.runtimeResults())
        .singleElement()
        .satisfies(
            result ->
                assertThat(text(result))
                    .isEqualTo(
                        "Parameter 'body' holds a <doc /> tag with the id 'bogus'. That is not a known document. Pass the tag of a document exactly as you read it, or pass other text."));
  }

  // document parameters of the tools of the process

  @Test
  void passesACallWithTextOrAKnownTagToAUnionParameterToTheProcess() {
    final var text = new ToolCall("s1", "send", Map.of("body", "hello"));
    final var bareId = new ToolCall("s2", "send", Map.of("body", reportId));
    final var tag = new ToolCall("s3", "send", Map.of("body", "<doc id=\"" + reportId + "\" />"));
    final var number = new ToolCall("s4", "send", Map.of("body", 3));

    final var split =
        handler.handle(
            conversation(WITH_MEMORY, text, bareId, tag, number),
            List.of(text, bareId, tag, number));

    assertThat(split.hasRuntimeResults()).isFalse();
    assertThat(split.remainingCalls()).containsExactly(text, bareId, tag, number);
  }

  @Test
  void answersATagWithAnUnknownIdOfAUnionParameterOfAProcessTool() {
    final var good = new ToolCall("s1", "send", Map.of("body", "hello"));
    final var bad = new ToolCall("s2", "send", Map.of("body", "<doc id=\"nope\" />"));

    final var split = handler.handle(conversation(WITH_MEMORY, good, bad), List.of(good, bad));

    assertThat(split.remainingCalls()).containsExactly(good);
    assertThat(split.runtimeResults())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.id()).isEqualTo("s2");
              assertThat(text(result))
                  .isEqualTo(
                      "Parameter 'body' holds a <doc /> tag with the id 'nope'. That is not a known document. Pass the tag of a document exactly as you read it, or pass other text.");
            });
  }

  @Test
  void passesACallWithKnownDocumentsToTheProcess() {
    final var call = new ToolCall("u1", "upload", Map.of("doc", "<doc id=\"" + reportId + "\" />"));

    final var split = handler.handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(split.hasRuntimeResults()).isFalse();
    assertThat(split.remainingCalls()).containsExactly(call);
  }

  @Test
  void passesACallOfAToolWithoutDocumentParametersToTheProcess() {
    final var call = new ToolCall("w1", "getWeather", Map.of("doc", "nope"));

    final var split = handler.handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(split.remainingCalls()).containsExactly(call);
  }

  @Test
  void answersAValueThatIsNotATagOfAProcessTool() {
    final var call = new ToolCall("u1", "upload", Map.of("doc", reportId));

    final var split = handler.handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(split.remainingCalls()).isEmpty();
    assertThat(split.runtimeResults())
        .singleElement()
        .satisfies(
            result ->
                assertThat(text(result))
                    .isEqualTo(
                        "Parameter 'doc' must be the <doc /> tag of a known document, exactly as you read it, got '"
                            + reportId
                            + "'."));
  }

  @Test
  void returnsTheConversationUntouchedForAnEmptyBatch() {
    final var conversation = conversation(WITH_MEMORY, WEATHER_CALL);

    final var split = handler.handle(conversation, List.of());

    assertThat(split.conversation()).isSameAs(conversation);
    assertThat(split.hasRuntimeResults()).isFalse();
  }

  @Test
  void aReturnDocumentToolCanBeRegisteredNextToOtherTools() {
    final var both =
        handlerOf(new ReturnDocumentTool(TestObjectMapperSupplier.INSTANCE), new SaveNoteTool());

    assertThat(both.availableToolDefinitions(WITH_MEMORY, NEW_INSTANCE))
        .extracting(ToolDefinition::name)
        .containsExactlyInAnyOrder(ReturnDocumentTool.NAME, "c8__save_note");
  }

  @Test
  void runsACustomToolForAnAgentWithOnlyTheRuntimeToolsFeature() {
    final var call = new ToolCall("e1", "acme_echo", Map.of());
    final var conversation = conversationWithFeatures(Set.of("runtimeTools"), call);

    final var split = handlerOf(ECHO_TOOL).handle(conversation, List.of(call));

    assertThat(split.answersAllCalls()).isTrue();
    assertThat(split.runtimeResults())
        .singleElement()
        .satisfies(result -> assertThat(text(result)).isEqualTo("echo"));
    assertThat(handlerOf(ECHO_TOOL).availableToolDefinitions(conversation))
        .extracting(ToolDefinition::name)
        .containsExactly("acme_echo");
  }

  @Test
  void doesNotOfferTheDocumentToolsToAnAgentWithOnlyTheRuntimeToolsFeature() {
    final var conversation = conversationWithFeatures(Set.of("runtimeTools"));

    assertThat(
            handlerOf(ECHO_TOOL, new ReturnDocumentTool(TestObjectMapperSupplier.INSTANCE))
                .availableToolDefinitions(conversation))
        .extracting(ToolDefinition::name)
        .containsExactly("acme_echo");
  }

  @Test
  void leavesTheCallsOfAnAgentWithoutTheRuntimeToolsFeatureToTheProcess() {
    final var call = new ToolCall("e1", "acme_echo", Map.of());
    final var conversation = conversationWithFeatures(Set.of(), call);

    final var split = handlerOf(ECHO_TOOL).handle(conversation, List.of(call));

    assertThat(split.hasRuntimeResults()).isFalse();
    assertThat(split.remainingCalls()).containsExactly(call);
    assertThat(handlerOf(ECHO_TOOL).availableToolDefinitions(conversation)).isEmpty();
  }

  @Test
  void answersTheCallsOfAToolOnlyForAgentsThatHaveTheFeatureItNeeds() {
    final var featured =
        StubRuntimeTool.of(
                "acme_featured",
                (call, executionContext, conv) -> RuntimeToolResult.success("featured"))
            .availableWhen((executionContext, agentContext) -> agentContext.supports("acme"));
    final var call = new ToolCall("f1", "acme_featured", Map.of());

    final var without =
        handlerOf(featured)
            .handle(conversationWithFeatures(Set.of("runtimeTools"), call), List.of(call));
    final var with =
        handlerOf(featured)
            .handle(conversationWithFeatures(Set.of("runtimeTools", "acme"), call), List.of(call));

    assertThat(without.hasRuntimeResults()).isFalse();
    assertThat(without.remainingCalls()).containsExactly(call);
    assertThat(with.answersAllCalls()).isTrue();
    assertThat(with.runtimeResults())
        .singleElement()
        .satisfies(result -> assertThat(text(result)).isEqualTo("featured"));
  }

  @Test
  void aToolReadsTheConversationAndTheExecutionContextItIsCalledWith() {
    final var seen = new ArrayList<Object>();
    final var reading =
        StubRuntimeTool.of(
            "acme_reading",
            (call, executionContext, conv) -> {
              seen.add(executionContext);
              seen.add(conv.agentContext().documentRegistry().entries());
              seen.add(conv.agentContext().features());
              return RuntimeToolResult.success("read");
            });
    final var call = new ToolCall("r1", "acme_reading", Map.of());
    final var conversation = conversation(WITH_MEMORY, call);

    final var executionContext = withConfiguration(WITH_MEMORY);

    HandledToolCalls.of(
        handlerOf(reading).delegate(), executionContext, conversation, List.of(call));

    assertThat(seen)
        .containsExactly(
            executionContext,
            conversation.documentRegistry().entries(),
            AgentContext.newInstanceFeatures());
  }

  @Test
  void aToolCreatesADocumentWithTheFactoryItWasGiven() {
    final var factory = new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
    final var creating =
        StubRuntimeTool.of(
            "acme_create",
            (call, executionContext, conv) ->
                RuntimeToolResult.success(
                    factory.create(
                        DocumentCreationRequest.from("created".getBytes(StandardCharsets.UTF_8))
                            .contentType("text/plain")
                            .fileName("created.txt")
                            .build())));
    final var call = new ToolCall("c1", "acme_create", Map.of());
    final var conversation = conversationWithFeatures(Set.of("runtimeTools"), call);

    final var split = handlerOf(creating).handle(conversation, List.of(call));

    assertThat(split.runtimeResults())
        .singleElement()
        .satisfies(
            result ->
                assertThat(result.content())
                    .singleElement()
                    .isInstanceOfSatisfying(
                        DocumentContent.class,
                        documentContent -> {
                          final var document = documentContent.document();
                          assertThat(document.metadata().getFileName()).isEqualTo("created.txt");
                          assertThat(new String(document.asByteArray(), StandardCharsets.UTF_8))
                              .isEqualTo("created");
                        }));
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
