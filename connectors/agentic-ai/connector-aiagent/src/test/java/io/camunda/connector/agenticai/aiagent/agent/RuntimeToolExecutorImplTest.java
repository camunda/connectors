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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolException;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.request.MemoryTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistryImpl;
import io.camunda.connector.agenticai.aiagent.tool.runtime.StubRuntimeTool;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        new RuntimeToolRegistryImpl(List.of(tools)), TestObjectMapperSupplier.INSTANCE);
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

  private List<RuntimeToolExecution> execute(
      RuntimeToolExecutor executor, AgentExecutionContext executionContext, ToolCall... calls) {
    return executor.execute(executionContext, NEW_INSTANCE, List.of(calls));
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
              assertThat(execution.result().id()).isEqualTo("n1");
              assertThat(execution.result().name()).isEqualTo("acme_note");
              assertThat(text(execution)).isEqualTo("Saved note: buy milk");
              assertThat(execution.result().elementId()).isNull();
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
        StubRuntimeTool.of("acme_report", (call, context) -> RuntimeToolResult.success(content));

    final var executions =
        execute(executorOf(tool), WITH_MEMORY, runtimeCall("r1", "acme_report", Map.of()));

    assertThat(executions)
        .singleElement()
        .satisfies(
            execution ->
                assertThat(execution.result().content()).containsExactlyElementsOf(content));
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

    final var executions = executorOf(tool).execute(WITH_MEMORY, NEW_INSTANCE, calls);

    assertThat(order).containsExactly("c1", "c2", "c3");
    assertThat(executions)
        .extracting(execution -> execution.result().id())
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
              assertThat(execution.result().id()).isEqualTo("n1");
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
        executor.execute(WITH_MEMORY, AgentContext.empty(), List.of(note("n1", "a")));

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

    final var without = executorOf(tool).execute(WITH_MEMORY, NEW_INSTANCE, List.of(call));
    final var with = executorOf(tool).execute(WITH_MEMORY, withFeature, List.of(call));

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
        .execute(WITH_MEMORY, agentContext, List.of(runtimeCall("r1", "acme_reading", Map.of())));

    assertThat(seen)
        .containsExactly(WITH_MEMORY, AgentFeatures.ofNewInstance(), Map.of("hello", "world"));
  }

  @Test
  void anEmptyBatchHasNoExecutions() {
    assertThat(executor.execute(WITH_MEMORY, NEW_INSTANCE, List.of())).isEmpty();
  }
}
