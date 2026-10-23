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

import io.camunda.connector.agenticai.aiagent.api.tool.AgentDocument;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolCall;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolException;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentIngestion;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentWorkspace;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.request.MemoryTemplateConfiguration;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class RuntimeToolsImplTest {

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
    return new RuntimeToolsImpl(List.of(tools), TestObjectMapperSupplier.INSTANCE);
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
    final var result = execution.result();
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
          assertThat(execution.result().id()).isEqualTo("n1");
          assertThat(execution.result().name()).isEqualTo("acme_note");
          assertThat(text(execution)).isEqualTo("Saved note: buy milk");
          assertThat(execution.result().elementId()).isNull();
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
          assertThat(execution.result().completedAt()).isEqualTo(execution.completedAt());
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
            assertThat(execution.result().content()).containsExactlyElementsOf(content));
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
        .extracting(execution -> execution.result().id())
        .containsExactly("c1", "c2", "c3");
    assertThat(executions).extracting(RuntimeToolExecution::completedAt).isSorted();
  }

  @Test
  void givesACallOfAToolThatIsNotEnabledWhenItIsExecutedAnErrorResult() {
    final var executions = execute(tools, PLAIN, note("n1", "a"));

    assertThat(executions)
        .singleElement()
        .satisfies(execution -> {
          assertThat(execution.result().id()).isEqualTo("n1");
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

  private static AgentContext knowing(Document... documents) {
    return NEW_INSTANCE.withDocumentWorkspace(
        DocumentIngestion.withDocuments(
            DocumentWorkspace.empty(),
            Arrays.stream(documents)
                .map(document -> new IngestedDocument(document, DocumentOrigin.userMessage()))
                .toList()));
  }

  @Test
  void aToolCanBeEnabledOnlyWhileTheAgentKnowsADocument() {
    final var needsDocuments =
        toolsOf(tool("acme_documents", context -> !context.documents().all().isEmpty()));
    final var knowingADocument = knowing(createDocument("report", "report.txt"));

    assertThat(needsDocuments.enabledDefinitions(PLAIN, NEW_INSTANCE)).isEmpty();
    assertThat(needsDocuments.enabledDefinitions(PLAIN, knowingADocument)).hasSize(1);
  }

  @Test
  void aToolReadsTheDocumentsTheAgentKnowsAndRestoresThem() {
    final var report = createDocument("report", "report.txt");
    final var seen = new ArrayList<Object>();
    final var reading =
        StubRuntimeTool.of(
            "acme_reading",
            (context, call) -> {
              seen.add(context.documents().all().stream().map(AgentDocument::id).toList());
              final var found =
                  context.documents().find(DocumentHandle.idFor(report)).orElseThrow();
              seen.add(new String(found.document().asByteArray(), StandardCharsets.UTF_8));
              seen.add(found.source());
              return RuntimeToolResult.success("read");
            });

    toolsOf(reading)
        .execute(
            WITH_MEMORY, knowing(report), List.of(runtimeCall("r1", "acme_reading", Map.of())));

    assertThat(seen)
        .containsExactly(
            List.of(DocumentHandle.idFor(report)), "report", DocumentOrigin.userMessage());
  }

  @Test
  void theCallsOfABatchSeeTheDocumentsKnownAtTheStartAndNotTheOnesAnotherCallReturned() {
    final var report = createDocument("report", "report.txt");
    final var counting =
        StubRuntimeTool.of(
            "acme_count",
            (context, call) ->
                RuntimeToolResult.success("documents: " + context.documents().all().size()));
    final var batch = toolsOf(new SaveNoteTool(), counting);

    final var executions =
        batch.execute(
            WITH_MEMORY,
            knowing(report),
            List.of(
                runtimeCall("n1", SaveNoteTool.NAME, Map.of("text", "a")),
                runtimeCall("c1", "acme_count", Map.of()),
                runtimeCall("n2", SaveNoteTool.NAME, Map.of("text", "b"))));

    // the second note is numbered like the first one: neither call saw the document of the other
    assertThat(executions.get(0).result().content().getFirst())
        .isEqualTo(TextContent.textContent("Saved note #1"));
    assertThat(executions.get(1).result().content())
        .containsExactly(TextContent.textContent("documents: 1"));
    assertThat(executions.get(2).result().content().getFirst())
        .isEqualTo(TextContent.textContent("Saved note #1"));
  }

  @Test
  void returnsTheDocumentsOfAResultAsTheyAreAndRegistersNone() {
    final var agentContext = knowing();

    final var executions =
        toolsOf(new SaveNoteTool())
            .execute(
                WITH_MEMORY,
                agentContext,
                List.of(runtimeCall("n1", SaveNoteTool.NAME, Map.of("text", "a"))));

    assertThat(executions.getFirst().result().content())
        .hasSize(2)
        .last()
        .isInstanceOf(DocumentContent.class);
    // the agent context is the one the tools were given
    assertThat(agentContext.documents()).isEmpty();
  }
}
