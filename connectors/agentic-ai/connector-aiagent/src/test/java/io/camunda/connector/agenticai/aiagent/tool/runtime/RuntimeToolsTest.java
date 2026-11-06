/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import static io.camunda.connector.agenticai.aiagent.TestAgentExecutionContext.withConfiguration;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_CHAT_MODEL;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_SYSTEM_PROMPT;
import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.agent.ToolCallResultDocumentExtractor;
import io.camunda.connector.agenticai.aiagent.api.tool.DocumentSchemas;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolCall;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolException;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.document.DocumentParameterResolver;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.request.MemoryTemplateConfiguration;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.InlineDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class RuntimeToolsTest {

  private static final AgentExecutionContext PLAIN =
      withConfiguration(
          new AgentConfiguration(
              TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null));
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
  private static final AgentContext NEW_INSTANCE =
      AgentContext.empty().withFeatures(AgentFeatures.ofNewInstance());
  private static final AgentContext WITHOUT_FEATURES = AgentContext.empty();

  private static final String ALWAYS = "acme_always";
  private static final String WITH_MEMORY_ONLY = "acme_memory";

  private static final RuntimeTool NOTE_TOOL =
      StubRuntimeTool.of(
              "acme_note",
              (context, call) -> {
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

  private final RuntimeTools tools = toolsOf(NOTE_TOOL);

  private static RuntimeTools toolsOf(RuntimeTool... tools) {
    return new RuntimeTools(
        List.of(tools), TestObjectMapperSupplier.INSTANCE, new DocumentParameterResolver());
  }

  private static RuntimeTool tool(String name, Predicate<RuntimeToolContext> enabled) {
    return StubRuntimeTool.of(name, (context, call) -> RuntimeToolResult.success(name))
        .enabledWhen(enabled);
  }

  private static List<String> names(List<ToolDefinition> definitions) {
    return definitions.stream().map(ToolDefinition::name).toList();
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

  private static List<RuntimeToolExecution> execute(
      RuntimeTools tools, AgentExecutionContext executionContext, ToolCall... calls) {
    return tools.execute(executionContext, NEW_INSTANCE, List.of(calls));
  }

  private final RuntimeTools registered =
      toolsOf(
          tool(ALWAYS, context -> true),
          tool(
              WITH_MEMORY_ONLY,
              context -> context.executionContext().configuration().memory() != null));

  @Test
  void offersOnlyTheToolsEnabledForTheAgentExecution() {
    assertThat(names(registered.enabledDefinitions(PLAIN, NEW_INSTANCE))).containsExactly(ALWAYS);
    assertThat(names(registered.enabledDefinitions(WITH_MEMORY, NEW_INSTANCE)))
        .containsExactlyInAnyOrder(ALWAYS, WITH_MEMORY_ONLY);
  }

  @Test
  void offersNoToolToAnAgentWithoutTheRuntimeToolsFeature() {
    assertThat(registered.enabledDefinitions(WITH_MEMORY, WITHOUT_FEATURES)).isEmpty();
  }

  @Test
  void offersTheRuntimeDefinitionsWithTheRuntimeKind() {
    assertThat(registered.enabledDefinitions(WITH_MEMORY, NEW_INSTANCE))
        .extracting(ToolDefinition::kind)
        .containsOnly(ToolKind.RUNTIME);
  }

  @Test
  void aToolCanBeEnabledOnlyWithAFeatureOfTheAgentInstance() {
    final var featured =
        toolsOf(tool("acme_featured", context -> context.agentContext().supports("acme")));
    final var withFeature = AgentContext.empty().withFeatures(List.of("runtimeTools", "acme"));

    assertThat(featured.enabledDefinitions(PLAIN, NEW_INSTANCE)).isEmpty();
    assertThat(names(featured.enabledDefinitions(PLAIN, withFeature)))
        .containsExactly("acme_featured");
  }

  @Test
  void keepsTheRegistrationOrderOfTheEnabledTools() {
    final var ordered =
        toolsOf(
            tool("acme_c", context -> true),
            tool("acme_hidden", context -> false),
            tool("acme_a", context -> true),
            tool("acme_b", context -> true));

    assertThat(names(ordered.enabledDefinitions(PLAIN, NEW_INSTANCE)))
        .containsExactly("acme_c", "acme_a", "acme_b");
  }

  @Test
  void rejectsABlankToolName() {
    final var blank = StubRuntimeTool.of(" ", (context, call) -> null);

    assertThatThrownBy(() -> toolsOf(blank))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("non-blank");
  }

  @Test
  void rejectsAToolWhoseDefinitionDoesNotDeclareTheRuntimeKind() {
    final var process =
        StubRuntimeTool.of(
            ToolDefinition.builder().name("acme_process").inputSchema(Map.of()).build(),
            (context, call) -> null);

    assertThatThrownBy(() -> toolsOf(process))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("acme_process")
        .hasMessageContaining("RUNTIME");
  }

  @Test
  void rejectsDuplicateToolNames() {
    final var first = StubRuntimeTool.of("acme_lookup", (context, call) -> null);
    final var second = StubRuntimeTool.of("acme_lookup", (context, call) -> null);

    assertThatThrownBy(() -> toolsOf(first, second))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("acme_lookup");
  }

  @Test
  void readsTheDefinitionOfAToolOnceAndOffersTheCachedDefinition() {
    final var reads = new AtomicInteger();
    final var definition = ToolDefinition.runtime("acme_counted", "d", Map.of("type", "object"));
    final RuntimeTool counting =
        new RuntimeTool() {
          @Override
          public ToolDefinition definition() {
            reads.incrementAndGet();
            return definition;
          }

          @Override
          public RuntimeToolResult call(RuntimeToolContext context, RuntimeToolCall call) {
            return RuntimeToolResult.success("ok");
          }
        };
    final var counted = toolsOf(counting);

    final var first = counted.enabledDefinitions(PLAIN, NEW_INSTANCE);
    final var second = counted.enabledDefinitions(PLAIN, NEW_INSTANCE);

    assertThat(first).containsExactly(definition);
    assertThat(second).containsExactly(definition);
    assertThat(reads).hasValue(1);
  }

  @Test
  void executesARuntimeToolAndReturnsItsResult() {
    final var call = note("n1", "buy milk");

    final var executions = execute(tools, WITH_MEMORY, call);

    assertThat(executions)
        .singleElement()
        .satisfies(execution -> {
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

    final var executions = execute(tools, WITH_MEMORY, note("n1", "a"));

    final var after = OffsetDateTime.now();
    assertThat(executions)
        .singleElement()
        .satisfies(execution -> {
          assertThat(execution.startedAt()).isBetween(before, after);
          assertThat(execution.completedAt()).isBetween(execution.startedAt(), after);
          assertThat(execution.toolCallResult().completedAt()).isEqualTo(execution.completedAt());
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
        StubRuntimeTool.of("acme_report", (context, call) -> RuntimeToolResult.success(content));

    final var executions =
        execute(toolsOf(tool), WITH_MEMORY, runtimeCall("r1", "acme_report", Map.of()));

    assertThat(executions)
        .singleElement()
        .satisfies(execution ->
            assertThat(execution.toolCallResult().content()).containsExactlyElementsOf(content));
  }

  record Attachment(String title, Document file) {}

  @Test
  void returnsAnObjectAsPlainJsonValuesThatKeepTheDocumentsOfARecord() {
    final var document =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
            .create(
                DocumentCreationRequest.from("report".getBytes(StandardCharsets.UTF_8))
                    .contentType("text/plain")
                    .fileName("report.txt")
                    .build());
    final var tool =
        StubRuntimeTool.of(
            "acme_attach",
            (context, call) ->
                RuntimeToolResult.success(
                    List.of(
                        ObjectContent.objectContent(
                            Map.of("attachments", List.of(new Attachment("Report", document)))))));

    final var executions =
        execute(toolsOf(tool), WITH_MEMORY, runtimeCall("a1", "acme_attach", Map.of()));

    final var content = executions.getFirst().toolCallResult().content();
    assertThat(content)
        .containsExactly(
            ObjectContent.objectContent(
                Map.of("attachments", List.of(Map.of("title", "Report", "file", document)))));
    assertThat(ToolCallResultDocumentExtractor.documentsOf(content)).containsExactly(document);
  }

  @Test
  void executesTheCallsInTheOrderOfTheCallsOneAfterTheOther() {
    final var order = new ArrayList<String>();
    final var tool =
        StubRuntimeTool.of(
            "acme_order",
            (context, call) -> {
              order.add(call.id());
              return RuntimeToolResult.success(call.id());
            });
    final var calls =
        List.of(
            runtimeCall("c1", "acme_order", Map.of()),
            runtimeCall("c2", "acme_order", Map.of()),
            runtimeCall("c3", "acme_order", Map.of()));

    final var executions = toolsOf(tool).execute(WITH_MEMORY, NEW_INSTANCE, calls);

    assertThat(order).containsExactly("c1", "c2", "c3");
    assertThat(executions)
        .extracting(execution -> execution.toolCallResult().id())
        .containsExactly("c1", "c2", "c3");
    assertThat(executions).extracting(RuntimeToolExecution::completedAt).isSorted();
  }

  @Test
  void givesACallOfAToolThatIsNotEnabledWhenItIsExecutedAnErrorResult() {
    final var executions = execute(tools, PLAIN, note("n1", "a"));

    assertThat(executions)
        .singleElement()
        .satisfies(execution -> {
          assertThat(execution.toolCallResult().id()).isEqualTo("n1");
          assertThat(text(execution)).isEqualTo("The tool 'acme_note' is not available.");
          assertThat(execution.startedAt()).isNotNull();
          assertThat(execution.completedAt()).isNotNull();
        });
  }

  @Test
  void givesACallOfAToolThatIsNotRegisteredAnErrorResult() {
    final var executions = execute(tools, WITH_MEMORY, runtimeCall("u1", "acme_unknown", Map.of()));

    assertThat(executions)
        .singleElement()
        .satisfies(execution ->
            assertThat(text(execution)).isEqualTo("The tool 'acme_unknown' is not available."));
  }

  @Test
  void givesACallOfAnAgentWithoutTheRuntimeToolsFeatureAnErrorResult() {
    final var executions = tools.execute(WITH_MEMORY, WITHOUT_FEATURES, List.of(note("n1", "a")));

    assertThat(executions)
        .singleElement()
        .satisfies(execution -> assertThat(text(execution)).contains("is not available"));
  }

  @Test
  void executesACustomToolOnlyForAgentsThatHaveTheFeatureItNeeds() {
    final var tool =
        StubRuntimeTool.of("acme_featured", (context, call) -> RuntimeToolResult.success("ok"))
            .enabledWhen(context -> context.agentContext().supports("acme"));
    final var call = runtimeCall("f1", "acme_featured", Map.of());
    final var withFeature = AgentContext.empty().withFeatures(List.of("runtimeTools", "acme"));

    final var without = toolsOf(tool).execute(WITH_MEMORY, NEW_INSTANCE, List.of(call));
    final var with = toolsOf(tool).execute(WITH_MEMORY, withFeature, List.of(call));

    assertThat(without)
        .singleElement()
        .satisfies(execution -> assertThat(text(execution)).contains("is not available"));
    assertThat(with)
        .singleElement()
        .satisfies(execution -> assertThat(text(execution)).isEqualTo("ok"));
  }

  @Test
  void keepsTheMessageOfAnErrorResultOfATool() {
    final var executions = execute(tools, WITH_MEMORY, note("n1", " "));

    assertThat(executions)
        .singleElement()
        .satisfies(
            execution -> assertThat(text(execution)).isEqualTo("The note must not be blank."));
  }

  @Test
  void turnsARuntimeToolExceptionIntoAnErrorResultWithItsMessage() {
    final var executions = execute(tools, WITH_MEMORY, note("n1", "reject"));

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
            (context, call) -> {
              call.argumentsAs(Args.class);
              return RuntimeToolResult.success("never");
            });

    final var executions =
        execute(
            toolsOf(tool), WITH_MEMORY, runtimeCall("l1", "acme_limited", Map.of("limit", "many")));

    assertThat(executions)
        .singleElement()
        .satisfies(execution ->
            assertThat(text(execution)).isEqualTo("Invalid arguments for tool 'acme_limited'."));
  }

  @Test
  void anUnexpectedExceptionOfAToolPropagates() {
    final var call = note("n1", "explode");

    assertThatThrownBy(() -> execute(tools, WITH_MEMORY, call))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("unexpected failure");
  }

  @Test
  void aToolThatReturnsNoResultFailsTheJob() {
    final var tool = StubRuntimeTool.of("acme_null", (context, call) -> null);
    final var call = runtimeCall("c1", "acme_null", Map.of());

    assertThatThrownBy(() -> execute(toolsOf(tool), WITH_MEMORY, call))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("acme_null");
  }

  @Test
  void aToolReadsTheExecutionContextAndTheAgentContextItIsCalledWith() {
    final var seen = new ArrayList<Object>();
    final var reading =
        StubRuntimeTool.of(
            "acme_reading",
            (context, call) -> {
              seen.add(context.executionContext());
              seen.add(context.agentContext().features());
              seen.add(context.agentContext().properties());
              return RuntimeToolResult.success("read");
            });
    final var agentContext = NEW_INSTANCE.withProperty("hello", "world");

    toolsOf(reading)
        .execute(WITH_MEMORY, agentContext, List.of(runtimeCall("r1", "acme_reading", Map.of())));

    assertThat(seen)
        .containsExactly(WITH_MEMORY, AgentFeatures.ofNewInstance(), Map.of("hello", "world"));
  }

  @Test
  void anEmptyBatchHasNoExecutions() {
    assertThat(tools.execute(WITH_MEMORY, NEW_INSTANCE, List.of())).isEmpty();
  }

  @Test
  void returnsTheDocumentsOfAResultAsTheyAreAndRegistersNone() {
    final List<Content> content =
        List.of(
            TextContent.textContent("Saved the note"),
            DocumentContent.documentContent(new InlineDocument("a", "note-1.txt", "text/plain")));
    final var notes =
        StubRuntimeTool.of("acme_notes", (context, call) -> RuntimeToolResult.success(content));

    final var executions =
        toolsOf(notes)
            .execute(WITH_MEMORY, NEW_INSTANCE, List.of(runtimeCall("n1", "acme_notes", Map.of())));

    assertThat(executions)
        .singleElement()
        .satisfies(execution -> assertThat(execution.result().content()).isEqualTo(content));
    // the agent context is the one the tools were given
    assertThat(NEW_INSTANCE.documents().all()).isEmpty();
  }

  // document parameters

  private static final String BOGUS_FILE = rejection("file", "bogus");

  private static final String UNKNOWN_BODY = rejection("body", "doc_000000000000");

  private static String rejection(String path, String id) {
    return ("Parameter '%s' has the id '%s', but the conversation has no document with this id."
            + " Pass the handle of a document from the conversation: a JSON object such as"
            + " {\"type\":\"camunda.agenticai.document\",\"id\":\"doc_…\"}.")
        .formatted(path, id);
  }

  private static ToolDefinition definitionOf(String name, Map<String, Object> properties) {
    return ToolDefinition.runtime(name, null, Map.of("type", "object", "properties", properties));
  }

  private static AgentContext knowing(Document... documents) {
    return NEW_INSTANCE.withDocuments(
        AgentDocuments.empty()
            .register(
                Arrays.stream(documents)
                    .map(document ->
                        AgentDocument.of(
                            document, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID)))
                    .toList()));
  }

  private static AgentContext knowingWithDocumentsFeature(Document... documents) {
    return knowing(documents)
        .withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS));
  }

  private static Map<String, Object> handleOf(Document document) {
    return Map.of("type", "camunda.agenticai.document", "id", AgentDocumentId.of(document).value());
  }

  @Test
  void handsTheIdsOfTheDocumentParametersToARuntimeToolAfterCheckingThem() {
    final var report = createDocument("report", "report.txt");
    final var received = new ArrayList<Object>();
    record Args(AgentDocumentId file, List<String> files) {}
    final var tool =
        StubRuntimeTool.of(
            definitionOf(
                "c8__read_file",
                Map.of(
                    "file",
                    DocumentSchemas.document("The file"),
                    "files",
                    Map.of("type", "array", "items", Map.of("type", "string")))),
            (context, call) -> {
              final var args = call.argumentsAs(Args.class);
              received.add(args.file());
              received.add(args.files());
              return RuntimeToolResult.success("read");
            });

    final var executions =
        toolsOf(tool)
            .execute(
                WITH_MEMORY,
                knowingWithDocumentsFeature(report),
                List.of(runtimeCall("r1", "c8__read_file", Map.of("file", handleOf(report)))));

    assertThat(text(executions.getFirst())).isEqualTo("read");
    assertThat(received).containsExactly(AgentDocumentId.of(report), null);
  }

  @Test
  void handsTheIdOfAHandleWithMoreFieldsAndKeepsTheOtherArguments() {
    final var report = createDocument("report", "report.txt");
    final var received = new ArrayList<Object>();
    final var tool =
        StubRuntimeTool.of(
            definitionOf("c8__read_file", Map.of("file", DocumentSchemas.document("The file"))),
            (context, call) -> {
              received.add(call.arguments());
              return RuntimeToolResult.success("read");
            });
    final var arguments =
        Map.<String, Object>of(
            "file",
            Map.of(
                "type",
                "camunda.agenticai.document",
                "id",
                AgentDocumentId.of(report).value(),
                "fileName",
                "report.txt"),
            "other",
            "kept");

    toolsOf(tool)
        .execute(
            WITH_MEMORY,
            knowingWithDocumentsFeature(report),
            List.of(runtimeCall("r1", "c8__read_file", arguments)));

    assertThat(received)
        .containsExactly(Map.of("file", AgentDocumentId.of(report), "other", "kept"));
  }

  @Test
  void aCallWithAnInvalidDocumentParameterFailsAndDoesNotRunTheTool() {
    final var runs = new AtomicInteger();
    final var tool =
        StubRuntimeTool.of(
            definitionOf("c8__read_file", Map.of("file", DocumentSchemas.document("The file"))),
            (context, call) -> {
              runs.incrementAndGet();
              return RuntimeToolResult.success("read");
            });
    final var call = runtimeCall("r1", "c8__read_file", Map.of("file", "bogus"));

    assertThatThrownBy(() ->
            toolsOf(tool)
                .execute(
                    WITH_MEMORY,
                    knowingWithDocumentsFeature(createDocument("known", "known.txt")),
                    List.of(call)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "The call 'r1' of the tool 'c8__read_file' is admitted, but its document parameters are invalid: "
                + BOGUS_FILE);
    assertThat(runs).hasValue(0);
  }

  @Test
  void handsTheIdOfAHandleInAUnionParameterToARuntimeToolAndKeepsTextAsText() {
    final var report = createDocument("report", "report.txt");
    final var received = new ArrayList<Object>();
    record Args(Object body) {}
    final var tool =
        StubRuntimeTool.of(
            definitionOf("c8__send", Map.of("body", DocumentSchemas.textOrDocument("The body"))),
            (context, call) -> {
              received.add(call.argumentsAs(Args.class).body());
              return RuntimeToolResult.success("sent");
            });

    toolsOf(tool)
        .execute(
            WITH_MEMORY,
            knowingWithDocumentsFeature(report),
            List.of(
                runtimeCall("r1", "c8__send", Map.of("body", handleOf(report))),
                runtimeCall("r2", "c8__send", Map.of("body", "hello"))));

    assertThat(received).containsExactly(AgentDocumentId.of(report), "hello");
  }

  @Test
  void aCallWithAHandleOfAnUnknownDocumentInAUnionParameterFailsAndDoesNotRunTheTool() {
    final var runs = new AtomicInteger();
    final var tool =
        StubRuntimeTool.of(
            definitionOf("c8__send", Map.of("body", DocumentSchemas.textOrDocument("The body"))),
            (context, call) -> {
              runs.incrementAndGet();
              return RuntimeToolResult.success("sent");
            });
    final var call =
        runtimeCall(
            "r1",
            "c8__send",
            Map.of("body", Map.of("type", "camunda.agenticai.document", "id", "doc_000000000000")));

    assertThatThrownBy(() ->
            toolsOf(tool)
                .execute(
                    WITH_MEMORY,
                    knowingWithDocumentsFeature(createDocument("known", "known.txt")),
                    List.of(call)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "The call 'r1' of the tool 'c8__send' is admitted, but its document parameters are invalid: "
                + UNKNOWN_BODY);
    assertThat(runs).hasValue(0);
  }

  @Test
  void anAgentInstanceWithoutTheDocumentsFeatureHandsTheArgumentsAsTheModelSentThem() {
    final var received = new ArrayList<Object>();
    final var tool =
        StubRuntimeTool.of(
            definitionOf("c8__read_file", Map.of("file", DocumentSchemas.document("The file"))),
            (context, call) -> {
              received.add(call.arguments());
              return RuntimeToolResult.success("read");
            });

    final var executions =
        toolsOf(tool)
            .execute(
                WITH_MEMORY,
                NEW_INSTANCE,
                List.of(runtimeCall("r1", "c8__read_file", Map.of("file", "bogus"))));

    assertThat(text(executions.getFirst())).isEqualTo("read");
    assertThat(received).containsExactly(Map.of("file", "bogus"));
  }
}
