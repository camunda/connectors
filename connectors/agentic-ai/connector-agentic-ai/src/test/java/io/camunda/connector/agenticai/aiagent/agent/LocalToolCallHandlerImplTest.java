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

import io.camunda.connector.agenticai.aiagent.localtool.LocalTool;
import io.camunda.connector.agenticai.aiagent.localtool.LocalToolRegistryImpl;
import io.camunda.connector.agenticai.aiagent.localtool.LocalToolResult;
import io.camunda.connector.agenticai.aiagent.localtool.LocalToolSchema;
import io.camunda.connector.agenticai.aiagent.localtool.ReturnDocumentsTool;
import io.camunda.connector.agenticai.aiagent.localtool.SaveNoteTool;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LocalToolCallHandlerImplTest {

  private static final ToolDefinition UPLOAD =
      ToolDefinition.builder()
          .name("upload")
          .inputSchema(
              Map.of(
                  "type",
                  "object",
                  "properties",
                  Map.of("doc", Map.of("type", "string", "format", "camunda-document"))))
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

  private final Document report = createDocument("report", "report.txt");
  private final String reportId = DocumentHandle.idFor(report);

  private static String tag(String id) {
    return "<doc id=\"" + id + "\" />";
  }

  private final DocumentRegistry registry =
      DocumentRegistry.empty()
          .withDocuments(
              List.of(
                  new IngestedDocument(report, DocumentSource.toolCall("search", "call-1", null))));

  private final LocalToolCallHandler handler = handlerOf(SaveNoteTool.create());

  private static LocalToolCallHandlerImpl handlerOf(LocalTool... tools) {
    return new LocalToolCallHandlerImpl(
        new LocalToolRegistryImpl(List.of(tools)), TestObjectMapperSupplier.INSTANCE);
  }

  private AgentConversation conversation(AgentConfiguration configuration, ToolCall... calls) {
    return AgentConversation.rehydrate(
            configuration.withToolDefinitions(List.of(UPLOAD, WEATHER)),
            AgentContext.builder().state(AgentState.READY).documents(registry.entries()).build(),
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
  void leavesEveryCallToTheProcessWhenNoLocalToolIsRegistered() {
    final var split =
        handlerOf().handle(conversation(WITH_MEMORY, WEATHER_CALL), List.of(WEATHER_CALL));

    assertThat(split.hasLocalResults()).isFalse();
    assertThat(split.remainingCalls()).containsExactly(WEATHER_CALL);
  }

  @Test
  void runsALocalToolAndAnswersItsCall() {
    final var call = note("n1", "buy milk");

    final var split = handler.handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(split.answersAllCalls()).isTrue();
    assertThat(split.localResults())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.id()).isEqualTo("n1");
              assertThat(result.name()).isEqualTo("save_note");
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

    assertThat(split.localResults())
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
    assertThat(split.localResults()).extracting(ToolCallResult::id).containsExactly("n1", "n2");
    assertThat(split.remainingCalls()).containsExactly(WEATHER_CALL, other);
  }

  @Test
  void callsOfAnUnavailableLocalToolGoToTheProcess() {
    final var call = note("n1", "a");

    final var split = handler.handle(conversation(WITHOUT_MEMORY, call), List.of(call));

    assertThat(split.hasLocalResults()).isFalse();
    assertThat(split.remainingCalls()).containsExactly(call);
    assertThat(handler.availableToolDefinitions(WITHOUT_MEMORY)).isEmpty();
    assertThat(handler.availableToolDefinitions(WITH_MEMORY))
        .extracting(ToolDefinition::name)
        .containsExactly("save_note");
  }

  @Test
  void answersAnErrorResultWithoutChangingTheState() {
    final var call = note("n1", " ");
    final var conversation = conversation(WITH_MEMORY, call);

    final var split = handler.handle(conversation, List.of(call));

    assertThat(split.localResults())
        .singleElement()
        .satisfies(result -> assertThat(result.content()).isEqualTo("The note must not be blank."));
    assertThat(split.conversation().toAgentContext().properties()).isEmpty();
  }

  @Test
  void answersALocalToolExceptionWithItsMessage() {
    final var call = note("n1", "reject");

    final var split = handler.handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(split.localResults())
        .singleElement()
        .satisfies(result -> assertThat(result.content()).isEqualTo("This note is not allowed."));
  }

  @Test
  void answersArgumentsOfTheWrongTypeWithAnErrorResultTheModelCanActOn() {
    record Args(int limit) {}
    final var tool =
        LocalTool.builder("limited")
            .handler(Args.class, (args, context) -> LocalToolResult.success("never"));
    final var call = new ToolCall("l1", "limited", Map.of("limit", "many"));

    final var split = handlerOf(tool).handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(split.localResults())
        .singleElement()
        .satisfies(
            result ->
                assertThat(result.content().toString())
                    .startsWith("Invalid arguments for tool 'limited':"));
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
  void resolvesTheDocumentParametersOfALocalToolBeforeItRuns() {
    final var received = new ArrayList<Document>();
    record Args(Document file) {}
    final var tool =
        LocalTool.builder("read_file")
            .inputSchema(LocalToolSchema.object().document("file", "The file", true))
            .handler(
                Args.class,
                (args, context) -> {
                  received.add(args.file());
                  return LocalToolResult.success("read");
                });
    final var call = new ToolCall("r1", "read_file", Map.of("file", tag(reportId)));

    final var split = handlerOf(tool).handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(split.localResults()).extracting(ToolCallResult::content).containsExactly("read");
    assertThat(received)
        .singleElement()
        .satisfies(d -> assertThat(DocumentHandle.idFor(d)).isEqualTo(reportId));
  }

  @Test
  void answersAnUnknownDocumentIdOfALocalToolWithTheKnownDocumentsAndDoesNotRunIt() {
    final var runs = new AtomicInteger();
    final var tool =
        LocalTool.builder("read_file")
            .inputSchema(LocalToolSchema.object().document("file", "The file", true))
            .handler(
                (call, context) -> {
                  runs.incrementAndGet();
                  return LocalToolResult.success("read");
                });
    final var call = new ToolCall("r1", "read_file", Map.of("file", tag("bogus")));

    final var split = handlerOf(tool).handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(runs).hasValue(0);
    assertThat(split.localResults())
        .singleElement()
        .satisfies(
            result ->
                assertThat(result.content().toString())
                    .startsWith(
                        "Unknown document id 'bogus' for parameter 'file'. Known documents:")
                    .contains(reportId));
  }

  // document parameters of the tools of the process

  @Test
  void passesACallWithKnownDocumentsToTheProcess() {
    final var call = new ToolCall("u1", "upload", Map.of("doc", tag(reportId)));

    final var split = handler.handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(split.hasLocalResults()).isFalse();
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
    final var call = new ToolCall("u1", "upload", Map.of("doc", tag("nope")));

    final var split = handler.handle(conversation(WITH_MEMORY, call), List.of(call));

    assertThat(split.remainingCalls()).isEmpty();
    assertThat(split.localResults())
        .singleElement()
        .satisfies(
            result ->
                assertThat(result.content())
                    .isEqualTo(
                        "Unknown document id 'nope' for parameter 'doc'. Known documents:\n"
                            + "<doc id=\"%s\" fileName=\"report.txt\" contentType=\"text/plain\" toolName=\"search\" toolCallId=\"call-1\" />"
                                .formatted(reportId)));
  }

  @Test
  void saysSoWhenNoDocumentIsKnown() {
    final var call = new ToolCall("u1", "upload", Map.of("doc", tag("nope")));
    final var conversation =
        AgentConversation.rehydrate(
                WITH_MEMORY.withToolDefinitions(List.of(UPLOAD)),
                AgentContext.builder().state(AgentState.READY).build(),
                TurnReconstructor.reconstruct(List.of()),
                null,
                List.of(userMessage("hi")))
            .ingest(assistantMessage("calling", List.of(call)), AgentMetrics.empty());

    final var split = handler.handle(conversation, List.of(call));

    assertThat(split.localResults())
        .singleElement()
        .satisfies(
            result ->
                assertThat(result.content())
                    .isEqualTo(
                        "Unknown document id 'nope' for parameter 'doc'. No documents are known."));
  }

  @Test
  void returnsTheConversationUntouchedForAnEmptyBatch() {
    final var conversation = conversation(WITH_MEMORY, WEATHER_CALL);

    final var split = handler.handle(conversation, List.of());

    assertThat(split.conversation()).isSameAs(conversation);
    assertThat(split.hasLocalResults()).isFalse();
  }

  @Test
  void aReturnDocumentsToolCanBeRegisteredNextToOtherTools() {
    final var both = handlerOf(new ReturnDocumentsTool(), SaveNoteTool.create());

    assertThat(both.availableToolDefinitions(WITH_MEMORY))
        .extracting(ToolDefinition::name)
        .containsExactly("save_note");
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
