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
import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.api.tool.AgentDocument;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolException;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolSchemas;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistries;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.request.MemoryTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistryImpl;
import io.camunda.connector.agenticai.aiagent.tool.runtime.SaveNoteTool;
import io.camunda.connector.agenticai.aiagent.tool.runtime.StubRuntimeTool;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RuntimeToolExecutorImplTest {

  private static final AgentExecutionContext WITH_MEMORY =
      withConfiguration(
          new AgentConfiguration(
              TEST_CHAT_MODEL,
              TEST_SYSTEM_PROMPT,
              null,
              new MemoryTemplateConfiguration(null, 10),
              null,
              null,
              null));
  private static final AgentExecutionContext WITHOUT_MEMORY =
      withConfiguration(
          new AgentConfiguration(
              TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null));
  private static final AgentContext NEW_INSTANCE =
      AgentContext.empty().withFeatures(AgentFeatures.ofNewInstance());

  /** A tool that is enabled while the agent has a memory configuration. */
  private static final RuntimeTool NOTE_TOOL =
      StubRuntimeTool.of(
              "acme_note",
              (call, context) -> {
                final var text = call.arguments().get("text");
                if ("reject".equals(text)) {
                  throw new RuntimeToolException("This note is not allowed.");
                }
                if ("explode".equals(text)) {
                  throw new IllegalStateException("unexpected failure");
                }
                if (text == null || text.toString().isBlank()) {
                  return RuntimeToolResult.error("The note must not be blank.");
                }
                return RuntimeToolResult.success("Saved note: " + text);
              })
          .enabledWhen(context -> context.executionContext().configuration().memory() != null);

  private final RuntimeToolExecutor executor = executorOf(NOTE_TOOL);

  private static RuntimeToolExecutor executorOf(RuntimeTool... tools) {
    return new RuntimeToolExecutorImpl(
        new RuntimeToolRegistryImpl(List.of(tools), TestObjectMapperSupplier.INSTANCE),
        TestObjectMapperSupplier.INSTANCE);
  }

  private static ToolCall note(String id, String text) {
    return new ToolCall(id, "acme_note", Map.of("text", text)).withKind(ToolKind.RUNTIME);
  }

  private static ToolCall runtimeCall(String id, String name, Map<String, Object> arguments) {
    return new ToolCall(id, name, arguments).withKind(ToolKind.RUNTIME);
  }

  private static String text(RuntimeToolExecution execution) {
    final var result = execution.toolCallResult();
    assertThat(result.content()).singleElement().isInstanceOf(TextContent.class);
    return ((TextContent) result.content().getFirst()).text();
  }

  private List<RuntimeToolExecution> execute(
      RuntimeToolExecutor executor, AgentExecutionContext executionContext, ToolCall... calls) {
    return executor.execute(executionContext, NEW_INSTANCE, List.of(calls), Set.of());
  }

  @Test
  void runsARuntimeToolAndReturnsItsResult() {
    final var call = note("n1", "buy milk");

    final var executions = execute(executor, WITH_MEMORY, call);

    assertThat(executions)
        .singleElement()
        .satisfies(
            execution -> {
              assertThat(execution.call()).isEqualTo(call);
              assertThat(execution.toolCallResult().id()).isEqualTo("n1");
              assertThat(execution.toolCallResult().name()).isEqualTo("acme_note");
              assertThat(text(execution)).isEqualTo("Saved note: buy milk");
              assertThat(execution.toolCallResult().elementId()).isNull();
            });
  }

  @Test
  void keepsTheTimesTheCallStartedAndCompleted() {
    final var before = OffsetDateTime.now();

    final var executions = execute(executor, WITH_MEMORY, note("n1", "a"));

    final var after = OffsetDateTime.now();
    assertThat(executions)
        .singleElement()
        .satisfies(
            execution -> {
              assertThat(execution.startedAt()).isBetween(before, after);
              assertThat(execution.completedAt()).isBetween(execution.startedAt(), after);
              assertThat(execution.toolCallResult().completedAt())
                  .isEqualTo(execution.completedAt());
            });
  }

  @Test
  void returnsTheContentTheToolReturnedInTheOrderItReturnedIt() {
    final var document =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
            .create(
                DocumentCreationRequest.from("report".getBytes(StandardCharsets.UTF_8))
                    .contentType("text/plain")
                    .fileName("report.txt")
                    .build());
    final var content =
        List.<Content>of(
            TextContent.textContent("Here is the report"),
            ObjectContent.objectContent(Map.of("size", 6)),
            DocumentContent.documentContent(document));
    final var tool =
        StubRuntimeTool.of("acme_report", (call, context) -> RuntimeToolResult.success(content));

    final var executions =
        execute(executorOf(tool), WITH_MEMORY, runtimeCall("r1", "acme_report", Map.of()));

    assertThat(executions)
        .singleElement()
        .satisfies(
            execution ->
                assertThat(execution.toolCallResult().content())
                    .containsExactlyElementsOf(content));
  }

  @Test
  void runsTheCallsInTheOrderOfTheCallsOneAfterTheOther() {
    final var order = new ArrayList<String>();
    final var tool =
        StubRuntimeTool.of(
            "acme_order",
            (call, context) -> {
              order.add(call.id());
              return RuntimeToolResult.success(call.id());
            });
    final var calls =
        List.of(
            runtimeCall("c1", "acme_order", Map.of()),
            runtimeCall("c2", "acme_order", Map.of()),
            runtimeCall("c3", "acme_order", Map.of()));

    final var executions = executorOf(tool).execute(WITH_MEMORY, NEW_INSTANCE, calls, Set.of());

    assertThat(order).containsExactly("c1", "c2", "c3");
    assertThat(executions)
        .extracting(execution -> execution.toolCallResult().id())
        .containsExactly("c1", "c2", "c3");
    assertThat(executions).extracting(RuntimeToolExecution::completedAt).isSorted();
  }

  @Test
  void givesACallOfAToolThatIsNotEnabledWhenItRunsAnErrorResult() {
    final var executions = execute(executor, WITHOUT_MEMORY, note("n1", "a"));

    assertThat(executions)
        .singleElement()
        .satisfies(
            execution -> {
              assertThat(execution.toolCallResult().id()).isEqualTo("n1");
              assertThat(text(execution)).isEqualTo("The tool 'acme_note' is not enabled.");
              assertThat(execution.startedAt()).isNotNull();
              assertThat(execution.completedAt()).isNotNull();
            });
  }

  @Test
  void givesACallOfAnUnknownToolAnErrorResult() {
    final var executions =
        execute(executor, WITH_MEMORY, runtimeCall("u1", "acme_unknown", Map.of()));

    assertThat(executions)
        .singleElement()
        .satisfies(
            execution ->
                assertThat(text(execution)).isEqualTo("The tool 'acme_unknown' is not enabled."));
  }

  @Test
  void givesACallOfAnAgentWithoutTheRuntimeToolsFeatureAnErrorResult() {
    final var executions =
        executor.execute(WITH_MEMORY, AgentContext.empty(), List.of(note("n1", "a")), Set.of());

    assertThat(executions)
        .singleElement()
        .satisfies(execution -> assertThat(text(execution)).contains("not enabled"));
  }

  @Test
  void runsACustomToolOnlyForAgentsThatHaveTheFeatureItNeeds() {
    final var tool =
        StubRuntimeTool.of("acme_featured", (call, context) -> RuntimeToolResult.success("ok"))
            .enabledWhen(context -> context.agentContext().supports("acme"));
    final var call = runtimeCall("f1", "acme_featured", Map.of());
    final var withFeature = AgentContext.empty().withFeatures(List.of("runtimeTools", "acme"));

    final var without =
        executorOf(tool).execute(WITH_MEMORY, NEW_INSTANCE, List.of(call), Set.of());
    final var with = executorOf(tool).execute(WITH_MEMORY, withFeature, List.of(call), Set.of());

    assertThat(without)
        .singleElement()
        .satisfies(execution -> assertThat(text(execution)).contains("not enabled"));
    assertThat(with)
        .singleElement()
        .satisfies(execution -> assertThat(text(execution)).isEqualTo("ok"));
  }

  @Test
  void keepsTheMessageOfAnErrorResultOfATool() {
    final var executions = execute(executor, WITH_MEMORY, note("n1", " "));

    assertThat(executions)
        .singleElement()
        .satisfies(
            execution -> assertThat(text(execution)).isEqualTo("The note must not be blank."));
  }

  @Test
  void turnsARuntimeToolExceptionIntoAnErrorResultWithItsMessage() {
    final var executions = execute(executor, WITH_MEMORY, note("n1", "reject"));

    assertThat(executions)
        .singleElement()
        .satisfies(execution -> assertThat(text(execution)).isEqualTo("This note is not allowed."));
  }

  @Test
  void givesArgumentsOfTheWrongTypeAnErrorResultTheModelCanActOn() {
    record Args(int limit) {}
    final var tool =
        StubRuntimeTool.of(
            "acme_limited",
            (call, context) -> {
              call.argumentsAs(Args.class);
              return RuntimeToolResult.success("never");
            });

    final var executions =
        execute(
            executorOf(tool),
            WITH_MEMORY,
            runtimeCall("l1", "acme_limited", Map.of("limit", "many")));

    assertThat(executions)
        .singleElement()
        .satisfies(
            execution ->
                assertThat(text(execution))
                    .isEqualTo("Invalid arguments for tool 'acme_limited'."));
  }

  @Test
  void anUnexpectedExceptionOfAToolPropagates() {
    final var call = note("n1", "explode");

    assertThatThrownBy(() -> execute(executor, WITH_MEMORY, call))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("unexpected failure");
  }

  @Test
  void aToolThatReturnsNoResultFailsTheJob() {
    final var tool = StubRuntimeTool.of("acme_null", (call, context) -> null);
    final var call = runtimeCall("c1", "acme_null", Map.of());

    assertThatThrownBy(() -> execute(executorOf(tool), WITH_MEMORY, call))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("acme_null");
  }

  @Test
  void aToolReadsTheExecutionContextAndTheAgentContextItIsCalledWith() {
    final var seen = new ArrayList<Object>();
    final var reading =
        StubRuntimeTool.of(
            "acme_reading",
            (call, context) -> {
              seen.add(context.executionContext());
              seen.add(context.agentContext().features());
              seen.add(context.agentContext().properties());
              return RuntimeToolResult.success("read");
            });
    final var agentContext = NEW_INSTANCE.withProperty("hello", "world");

    executorOf(reading)
        .execute(
            WITH_MEMORY,
            agentContext,
            List.of(runtimeCall("r1", "acme_reading", Map.of())),
            Set.of());

    assertThat(seen)
        .containsExactly(WITH_MEMORY, AgentFeatures.ofNewInstance(), Map.of("hello", "world"));
  }

  private static AgentContext knowing(Document... documents) {
    return NEW_INSTANCE.withDocumentRegistry(
        DocumentRegistries.withDocuments(
            DocumentRegistry.empty(),
            Arrays.stream(documents)
                .map(document -> new IngestedDocument(document, DocumentSource.userMessage()))
                .toList()));
  }

  @Test
  void aToolReadsTheDocumentsTheAgentKnowsAndRestoresThem() {
    final var report = createDocument("report", "report.txt");
    final var seen = new ArrayList<Object>();
    final var reading =
        StubRuntimeTool.of(
            "acme_reading",
            (call, context) -> {
              seen.add(context.documents().all().stream().map(AgentDocument::id).toList());
              final var found =
                  context.documents().find(DocumentHandle.idFor(report)).orElseThrow();
              seen.add(new String(found.document().asByteArray(), StandardCharsets.UTF_8));
              seen.add(found.source());
              return RuntimeToolResult.success("read");
            });

    executorOf(reading)
        .execute(
            WITH_MEMORY,
            knowing(report),
            List.of(runtimeCall("r1", "acme_reading", Map.of())),
            Set.of());

    assertThat(seen)
        .containsExactly(
            List.of(DocumentHandle.idFor(report)), "report", DocumentSource.userMessage());
  }

  @Test
  void theCallsOfABatchSeeTheDocumentsKnownAtTheStartAndNotTheOnesAnotherCallReturned() {
    final var report = createDocument("report", "report.txt");
    final var counting =
        StubRuntimeTool.of(
            "acme_count",
            (call, context) ->
                RuntimeToolResult.success("documents: " + context.documents().all().size()));
    final var executor = executorOf(new SaveNoteTool(), counting);

    final var executions =
        executor.execute(
            WITH_MEMORY,
            knowing(report),
            List.of(
                runtimeCall("n1", SaveNoteTool.NAME, Map.of("text", "a")),
                runtimeCall("c1", "acme_count", Map.of()),
                runtimeCall("n2", SaveNoteTool.NAME, Map.of("text", "b"))),
            Set.of());

    // the second note is numbered like the first one: neither call saw the document of the other
    assertThat(executions.get(0).toolCallResult().content().getFirst())
        .isEqualTo(TextContent.textContent("Saved note #1"));
    assertThat(executions.get(1).toolCallResult().content())
        .containsExactly(TextContent.textContent("documents: 1"));
    assertThat(executions.get(2).toolCallResult().content().getFirst())
        .isEqualTo(TextContent.textContent("Saved note #1"));
  }

  @Test
  void returnsTheDocumentsOfAResultAsTheyAreAndRegistersNone() {
    final var agentContext = knowing();

    final var executions =
        executorOf(new SaveNoteTool())
            .execute(
                WITH_MEMORY,
                agentContext,
                List.of(runtimeCall("n1", SaveNoteTool.NAME, Map.of("text", "a"))),
                Set.of());

    assertThat(executions.getFirst().toolCallResult().content())
        .hasSize(2)
        .last()
        .isInstanceOf(DocumentContent.class);
    // the agent context is the one the executor was given
    assertThat(agentContext.documents()).isEmpty();
  }

  @Test
  void aToolReadsWhetherTheContentOfADocumentIsInTheConversationAsOfTheStartOfTheBatch() {
    final var read = createDocument("read", "read.txt");
    final var unread = createDocument("unread", "unread.txt");
    final var seen = new ArrayList<Boolean>();
    final var reading =
        StubRuntimeTool.of(
            "acme_reading",
            (call, context) -> {
              context
                  .documents()
                  .all()
                  .forEach(document -> seen.add(document.contentInConversation()));
              return RuntimeToolResult.success("read");
            });

    executorOf(reading)
        .execute(
            WITH_MEMORY,
            knowing(read, unread),
            List.of(runtimeCall("r1", "acme_reading", Map.of())),
            Set.of(DocumentHandle.idFor(read)));

    assertThat(seen).containsExactly(true, false);
  }

  @Test
  void theExecutionKeepsTheEffectsOfASuccessfulResultAndNoneOfAnErrorResult() {
    final var report = createDocument("report", "report.txt");
    final var id = DocumentHandle.idFor(report);
    final var loading =
        StubRuntimeTool.of(
            "acme_load",
            (call, context) -> RuntimeToolResult.success("ok").withDocumentContent(id));
    final var failing =
        StubRuntimeTool.of("acme_fail", (call, context) -> RuntimeToolResult.error("no"));

    final var executions =
        executorOf(loading, failing)
            .execute(
                WITH_MEMORY,
                knowing(report),
                List.of(
                    runtimeCall("l1", "acme_load", Map.of()),
                    runtimeCall("f1", "acme_fail", Map.of())),
                Set.of());

    assertThat(executions.get(0).result().documentContent()).containsExactly(id);
    assertThat(executions.get(1).result().documentContent()).isEmpty();
    assertThat(executions.get(1).result()).isInstanceOf(RuntimeToolResult.Error.class);
  }

  // document parameters

  private static final String BOGUS_FILE =
      "Parameter 'file' must be the <doc /> tag of a known document, exactly as you read it, got 'bogus'.";

  private static final ToolDefinition UPLOAD =
      definitionOf("upload", Map.of("file", RuntimeToolSchemas.document("The file")));

  private static final String UNKNOWN_BODY =
      "Parameter 'body' holds a <doc /> tag with the id 'nope'. That is not a known document."
          + " Pass the tag of a document exactly as you read it, or pass other text.";

  private static final ToolDefinition SEND =
      definitionOf("send", Map.of("body", RuntimeToolSchemas.textOrDocument("The body")));

  private static ToolDefinition definitionOf(String name, Map<String, Object> properties) {
    return ToolDefinition.builder()
        .name(name)
        .inputSchema(Map.of("type", "object", "properties", properties))
        .build();
  }

  private static AgentContext knowingWithDocumentsFeature(Document... documents) {
    return knowing(documents)
        .withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS));
  }

  private static String tagOf(Document document) {
    return "<doc id=\"" + DocumentHandle.idFor(document) + "\" />";
  }

  @Test
  void handsTheIdsOfTheDocumentParametersToARuntimeToolAfterCheckingThem() {
    final var report = createDocument("report", "report.txt");
    final var received = new ArrayList<Object>();
    record Args(DocumentId file, List<String> files) {}
    final var tool =
        StubRuntimeTool.of(
            definitionOf(
                "c8__read_file",
                Map.of(
                    "file",
                    RuntimeToolSchemas.document("The file"),
                    "files",
                    Map.of("type", "array", "items", Map.of("type", "string")))),
            (call, context) -> {
              final var args = call.argumentsAs(Args.class);
              received.add(args.file());
              received.add(args.files());
              return RuntimeToolResult.success("read");
            });

    final var executions =
        executorOf(tool)
            .execute(
                WITH_MEMORY,
                knowingWithDocumentsFeature(report),
                List.of(runtimeCall("r1", "c8__read_file", Map.of("file", tagOf(report)))),
                Set.of());

    assertThat(text(executions.getFirst())).isEqualTo("read");
    assertThat(received).containsExactly(DocumentHandle.idFor(report), null);
  }

  @Test
  void handsTheIdOfATagWithMoreAttributesAndKeepsTheOtherArguments() {
    final var report = createDocument("report", "report.txt");
    final var received = new ArrayList<Object>();
    final var tool =
        StubRuntimeTool.of(
            definitionOf("c8__read_file", Map.of("file", RuntimeToolSchemas.document("The file"))),
            (call, context) -> {
              received.add(call.arguments());
              return RuntimeToolResult.success("read");
            });
    final var arguments =
        Map.<String, Object>of(
            "file",
            "<doc id=\"" + DocumentHandle.idFor(report) + "\" fileName=\"report.txt\" />",
            "other",
            "kept");

    executorOf(tool)
        .execute(
            WITH_MEMORY,
            knowingWithDocumentsFeature(report),
            List.of(runtimeCall("r1", "c8__read_file", arguments)),
            Set.of());

    assertThat(received)
        .containsExactly(Map.of("file", DocumentHandle.idFor(report), "other", "kept"));
  }

  @Test
  void answersAValueThatIsNotATagOfARuntimeToolWithAnErrorAndDoesNotRunIt() {
    final var runs = new AtomicInteger();
    final var tool =
        StubRuntimeTool.of(
            definitionOf("c8__read_file", Map.of("file", RuntimeToolSchemas.document("The file"))),
            (call, context) -> {
              runs.incrementAndGet();
              return RuntimeToolResult.success("read");
            });
    final var call = runtimeCall("r1", "c8__read_file", Map.of("file", "bogus"));

    final var executions =
        executorOf(tool)
            .execute(WITH_MEMORY, knowingWithDocumentsFeature(), List.of(call), Set.of());

    assertThat(runs).hasValue(0);
    assertThat(executions)
        .singleElement()
        .satisfies(
            execution -> {
              assertThat(execution.call()).isEqualTo(call);
              assertThat(execution.toolCallResult().id()).isEqualTo("r1");
              assertThat(text(execution)).isEqualTo(BOGUS_FILE);
            });
  }

  @Test
  void answersTheCallOfAToolOfTheProcessThatWasRejectedForItsDocumentParameters() {
    final var call = runtimeCall("u1", "upload", Map.of("file", "bogus"));
    final var agentContext = knowingWithDocumentsFeature().withToolDefinitions(List.of(UPLOAD));

    final var executions = executorOf().execute(WITH_MEMORY, agentContext, List.of(call), Set.of());

    assertThat(executions)
        .singleElement()
        .satisfies(
            execution -> {
              assertThat(execution.toolCallResult().id()).isEqualTo("u1");
              assertThat(execution.toolCallResult().name()).isEqualTo("upload");
              assertThat(execution.toolCallResult().elementId()).isNull();
              assertThat(execution.toolCallResult().completedAt()).isNotNull();
              assertThat(text(execution)).isEqualTo(BOGUS_FILE);
            });
  }

  @Test
  void handsTheIdOfATagInAUnionParameterToARuntimeToolAndKeepsTextAsText() {
    final var report = createDocument("report", "report.txt");
    final var received = new ArrayList<Object>();
    record Args(Object body) {}
    final var tool =
        StubRuntimeTool.of(
            definitionOf("c8__send", Map.of("body", RuntimeToolSchemas.textOrDocument("The body"))),
            (call, context) -> {
              received.add(call.argumentsAs(Args.class).body());
              return RuntimeToolResult.success("sent");
            });

    executorOf(tool)
        .execute(
            WITH_MEMORY,
            knowingWithDocumentsFeature(report),
            List.of(
                runtimeCall("r1", "c8__send", Map.of("body", tagOf(report))),
                runtimeCall("r2", "c8__send", Map.of("body", "hello"))),
            Set.of());

    assertThat(received).containsExactly(DocumentHandle.idFor(report), "hello");
  }

  @Test
  void answersATagOfAnUnknownDocumentInAUnionParameterOfARuntimeToolWithAnErrorAndDoesNotRunIt() {
    final var runs = new AtomicInteger();
    final var tool =
        StubRuntimeTool.of(
            definitionOf("c8__send", Map.of("body", RuntimeToolSchemas.textOrDocument("The body"))),
            (call, context) -> {
              runs.incrementAndGet();
              return RuntimeToolResult.success("sent");
            });

    final var executions =
        executorOf(tool)
            .execute(
                WITH_MEMORY,
                knowingWithDocumentsFeature(),
                List.of(runtimeCall("r1", "c8__send", Map.of("body", "<doc id=\"nope\" />"))),
                Set.of());

    assertThat(runs).hasValue(0);
    assertThat(text(executions.getFirst())).isEqualTo(UNKNOWN_BODY);
  }

  @Test
  void answersTheCallOfAToolOfTheProcessRejectedForATagInAUnionParameter() {
    final var call = runtimeCall("s1", "send", Map.of("body", "<doc id=\"nope\" />"));
    final var agentContext = knowingWithDocumentsFeature().withToolDefinitions(List.of(SEND));

    final var executions = executorOf().execute(WITH_MEMORY, agentContext, List.of(call), Set.of());

    assertThat(text(executions.getFirst())).isEqualTo(UNKNOWN_BODY);
  }

  @Test
  void answersARejectedCallWithTheSameErrorWheneverItIsExecuted() {
    final var call = runtimeCall("u1", "upload", Map.of("file", "bogus"));
    final var agentContext = knowingWithDocumentsFeature().withToolDefinitions(List.of(UPLOAD));

    final var first = executorOf().execute(WITH_MEMORY, agentContext, List.of(call), Set.of());
    final var again = executorOf().execute(WITH_MEMORY, agentContext, List.of(call), Set.of());

    assertThat(text(first.getFirst())).isEqualTo(text(again.getFirst()));
  }

  @Test
  void aCallOfAToolOfTheProcessWithValidDocumentParametersIsNotAnsweredByTheAgent() {
    final var report = createDocument("report", "report.txt");
    final var call = runtimeCall("u1", "upload", Map.of("file", tagOf(report)));
    final var agentContext =
        knowingWithDocumentsFeature(report).withToolDefinitions(List.of(UPLOAD));

    final var executions = executorOf().execute(WITH_MEMORY, agentContext, List.of(call), Set.of());

    // no tool of the agent owns the name and nothing was rejected: the call is not enabled
    assertThat(text(executions.getFirst())).isEqualTo("The tool 'upload' is not enabled.");
  }

  @Test
  void anAgentInstanceWithoutTheDocumentsFeatureHandsTheArgumentsAsTheModelSentThem() {
    final var received = new ArrayList<Object>();
    final var tool =
        StubRuntimeTool.of(
            definitionOf("c8__read_file", Map.of("file", RuntimeToolSchemas.document("The file"))),
            (call, context) -> {
              received.add(call.arguments());
              return RuntimeToolResult.success("read");
            });

    final var executions =
        executorOf(tool)
            .execute(
                WITH_MEMORY,
                AgentContext.empty().withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS)),
                List.of(runtimeCall("r1", "c8__read_file", Map.of("file", "bogus"))),
                Set.of());

    assertThat(text(executions.getFirst())).isEqualTo("read");
    assertThat(received).containsExactly(Map.of("file", "bogus"));
  }

  @Test
  void anEmptyBatchHasNoExecutions() {
    assertThat(executor.execute(WITH_MEMORY, NEW_INSTANCE, List.of(), Set.of())).isEmpty();
  }
}
