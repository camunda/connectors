/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_CHAT_MODEL;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_SYSTEM_PROMPT;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.document.ReturnDocumentTool;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistryImpl;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolSchema;
import io.camunda.connector.agenticai.aiagent.tool.runtime.SaveNoteTool;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
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
              null)
          .withFeatures(AgentContext.newInstanceFeatures());
  private static final AgentConfiguration WITHOUT_MEMORY =
      new AgentConfiguration(TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null)
          .withFeatures(AgentContext.newInstanceFeatures());

  private final Document report = createDocument("report", "report.txt");
  private final String reportId = DocumentHandle.idFor(report);

  private final DocumentRegistry registry =
      DocumentRegistry.empty()
          .withDocuments(
              List.of(
                  new IngestedDocument(report, DocumentSource.toolCall("search", "call-1", null))));

  private final RuntimeToolCallHandler handler = handlerOf(SaveNoteTool.create());

  private static RuntimeToolCallHandlerImpl handlerOf(RuntimeTool... tools) {
    return handlerOf(mock(DocumentFactory.class), tools);
  }

  private static RuntimeToolCallHandlerImpl handlerOf(
      DocumentFactory documentFactory, RuntimeTool... tools) {
    return new RuntimeToolCallHandlerImpl(
        new RuntimeToolRegistryImpl(List.of(tools)),
        TestObjectMapperSupplier.INSTANCE,
        documentFactory);
  }

  private static final RuntimeTool ECHO_TOOL =
      RuntimeTool.builder("acme_echo")
          .description("Echoes.")
          .handler((call, context) -> RuntimeToolResult.success("echo"));

  private static AgentConversation conversationWithFeatures(
      Set<String> features, ToolCall... calls) {
    final var featureSet = new LinkedHashSet<>(features);
    return AgentConversation.rehydrate(
            WITHOUT_MEMORY.withToolDefinitions(List.of(WEATHER)).withFeatures(featureSet),
            AgentContext.builder().state(AgentState.READY).features(featureSet).build(),
            TurnReconstructor.reconstruct(List.of()),
            null,
            List.of(userMessage("hi")))
        .ingest(assistantMessage("calling", List.of(calls)), AgentMetrics.empty());
  }

  private AgentConversation conversation(AgentConfiguration configuration, ToolCall... calls) {
    return AgentConversation.rehydrate(
            configuration
                .withToolDefinitions(List.of(UPLOAD, WEATHER))
                .withFeatures(AgentContext.newInstanceFeatures()),
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
              assertThat(result.content()).isEqualTo("Saved note #1");
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
        .extracting(ToolCallResult::content)
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
    assertThat(split.runtimeResults()).extracting(ToolCallResult::id).containsExactly("n1", "n2");
    assertThat(split.remainingCalls()).containsExactly(WEATHER_CALL, other);
  }

  @Test
  void callsOfAnUnavailableRuntimeToolGoToTheProcess() {
    final var call = note("n1", "a");

    final var split = handler.handle(conversation(WITHOUT_MEMORY, call), List.of(call));

    assertThat(split.hasRuntimeResults()).isFalse();
    assertThat(split.remainingCalls()).containsExactly(call);
    assertThat(handler.availableToolDefinitions(WITHOUT_MEMORY)).isEmpty();
    assertThat(handler.availableToolDefinitions(WITH_MEMORY))
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
        .satisfies(result -> assertThat(result.content()).isEqualTo("The note must not be blank."));
    assertThat(split.conversation().toAgentContext().properties()).isEmpty();
  }

  @Test
  void answersARuntimeToolExceptionWithItsMessage() {
    final var call = note("n1", "reject");

    final var split = handler.handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(split.runtimeResults())
        .singleElement()
        .satisfies(result -> assertThat(result.content()).isEqualTo("This note is not allowed."));
  }

  @Test
  void answersArgumentsOfTheWrongTypeWithAnErrorResultTheModelCanActOn() {
    record Args(int limit) {}
    final var tool =
        RuntimeTool.builder("c8__limited")
            .handler(Args.class, (args, context) -> RuntimeToolResult.success("never"));
    final var call = new ToolCall("l1", "c8__limited", Map.of("limit", "many"));

    final var split = handlerOf(tool).handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(split.runtimeResults())
        .singleElement()
        .satisfies(
            result ->
                assertThat(result.content().toString())
                    .startsWith("Invalid arguments for tool 'c8__limited':"));
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
        RuntimeTool.builder("c8__read_file")
            .inputSchema(
                RuntimeToolSchema.object()
                    .document("file", "The file", true)
                    .stringArray("files", "More files", false))
            .handler(
                Args.class,
                (args, context) -> {
                  received.add(args.file());
                  received.add(args.files());
                  return RuntimeToolResult.success("read");
                });
    final var call = new ToolCall("r1", "c8__read_file", Map.of("file", reportId));

    final var split = handlerOf(tool).handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(split.runtimeResults()).extracting(ToolCallResult::content).containsExactly("read");
    assertThat(received).containsExactly(reportId, null);
  }

  @Test
  void handsTheIdOfATagPassedToADocumentParameterOfARuntimeTool() {
    final var received = new ArrayList<Object>();
    final var tool =
        RuntimeTool.builder("c8__read_file")
            .inputSchema(RuntimeToolSchema.object().document("file", "The file", true))
            .handler(
                (call, context) -> {
                  received.add(call.arguments());
                  return RuntimeToolResult.success("read");
                });
    final var arguments =
        Map.<String, Object>of(
            "file", "<doc id=\"" + reportId + "\" fileName=\"report.txt\" />", "other", "kept");
    final var call = new ToolCall("r1", "c8__read_file", arguments);

    final var split = handlerOf(tool).handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(split.runtimeResults()).extracting(ToolCallResult::content).containsExactly("read");
    assertThat(received).containsExactly(Map.of("file", reportId, "other", "kept"));
  }

  @Test
  void answersAnUnknownDocumentIdOfARuntimeToolWithTheKnownDocumentsAndDoesNotRunIt() {
    final var runs = new AtomicInteger();
    final var tool =
        RuntimeTool.builder("c8__read_file")
            .inputSchema(RuntimeToolSchema.object().document("file", "The file", true))
            .handler(
                (call, context) -> {
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
                assertThat(result.content().toString())
                    .startsWith(
                        "Parameter 'file' must be the exact id of a known document (the id attribute of its <doc /> tag) or that tag itself, got 'bogus'. Known documents:")
                    .contains(reportId));
  }

  // document parameters of the tools of the process

  @Test
  void passesACallWithKnownDocumentsToTheProcess() {
    final var call = new ToolCall("u1", "upload", Map.of("doc", reportId));

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
  void answersAnUnknownDocumentIdOfAProcessToolWithTheKnownDocuments() {
    final var call = new ToolCall("u1", "upload", Map.of("doc", "nope"));

    final var split = handler.handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(split.remainingCalls()).isEmpty();
    assertThat(split.runtimeResults())
        .singleElement()
        .satisfies(
            result ->
                assertThat(result.content())
                    .isEqualTo(
                        "Parameter 'doc' must be the exact id of a known document (the id attribute of its <doc /> tag) or that tag itself, got 'nope'. Known documents:\n"
                            + "<doc id=\"%s\" fileName=\"report.txt\" contentType=\"text/plain\" toolName=\"search\" toolCallId=\"call-1\" />"
                                .formatted(reportId)));
  }

  @Test
  void saysSoWhenNoDocumentIsKnown() {
    final var call = new ToolCall("u1", "upload", Map.of("doc", "nope"));
    final var conversation =
        AgentConversation.rehydrate(
                WITH_MEMORY.withToolDefinitions(List.of(UPLOAD)),
                AgentContext.builder()
                    .state(AgentState.READY)
                    .features(AgentContext.newInstanceFeatures())
                    .build(),
                TurnReconstructor.reconstruct(List.of()),
                null,
                List.of(userMessage("hi")))
            .ingest(assistantMessage("calling", List.of(call)), AgentMetrics.empty());

    final var split = handler.handle(conversation, List.of(call));

    assertThat(split.runtimeResults())
        .singleElement()
        .satisfies(
            result ->
                assertThat(result.content())
                    .isEqualTo(
                        "Parameter 'doc' must be the exact id of a known document (the id attribute of its <doc /> tag) or that tag itself, got 'nope'. No documents are known."));
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
    final var both = handlerOf(new ReturnDocumentTool(), SaveNoteTool.create());

    assertThat(both.availableToolDefinitions(WITH_MEMORY))
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
        .satisfies(result -> assertThat(result.content()).isEqualTo("echo"));
    assertThat(handlerOf(ECHO_TOOL).availableToolDefinitions(conversation.configuration()))
        .extracting(ToolDefinition::name)
        .containsExactly("acme_echo");
  }

  @Test
  void doesNotOfferTheDocumentToolsToAnAgentWithOnlyTheRuntimeToolsFeature() {
    final var configuration = conversationWithFeatures(Set.of("runtimeTools")).configuration();

    assertThat(
            handlerOf(ECHO_TOOL, new ReturnDocumentTool()).availableToolDefinitions(configuration))
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
    assertThat(handlerOf(ECHO_TOOL).availableToolDefinitions(conversation.configuration()))
        .isEmpty();
  }

  @Test
  void aToolCreatesADocumentThroughTheContext() {
    final var factory = new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
    final var creating =
        RuntimeTool.builder("acme_create")
            .description("Creates a document.")
            .handler(
                (call, context) ->
                    RuntimeToolResult.success(
                        context
                            .documentFactory()
                            .create(
                                DocumentCreationRequest.from(
                                        "created".getBytes(StandardCharsets.UTF_8))
                                    .contentType("text/plain")
                                    .fileName("created.txt")
                                    .build())));
    final var call = new ToolCall("c1", "acme_create", Map.of());
    final var conversation = conversationWithFeatures(Set.of("runtimeTools"), call);

    final var split = handlerOf(factory, creating).handle(conversation, List.of(call));

    assertThat(split.runtimeResults())
        .singleElement()
        .satisfies(
            result ->
                assertThat(result.content())
                    .isInstanceOfSatisfying(
                        Document.class,
                        document -> {
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
