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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolCall;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.request.MemoryTemplateConfiguration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class RuntimeToolRegistryImplTest {

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

  private final RuntimeToolRegistry registry =
      new RuntimeToolRegistryImpl(
          List.of(
              tool(ALWAYS, context -> true),
              tool(
                  WITH_MEMORY_ONLY,
                  context -> context.executionContext().configuration().memory() != null)));

  private static RuntimeTool tool(String name, Predicate<RuntimeToolContext> enabled) {
    return StubRuntimeTool.of(name, (context, call) -> RuntimeToolResult.success(name))
        .enabledWhen(enabled);
  }

  private static RuntimeToolContext context(
      AgentExecutionContext executionContext, AgentContext agentContext) {
    return new RuntimeToolContextImpl(executionContext, agentContext);
  }

  private static List<String> names(List<ToolDefinition> definitions) {
    return definitions.stream().map(ToolDefinition::name).toList();
  }

  @Test
  void reservesTheNamesOfAllRegisteredToolsWhetherEnabledOrNot() {
    assertThat(registry.toolNames()).containsExactlyInAnyOrder(ALWAYS, WITH_MEMORY_ONLY);
  }

  @Test
  void offersOnlyTheToolsEnabledForTheAgentExecution() {
    assertThat(names(registry.enabledToolDefinitions(context(PLAIN, NEW_INSTANCE))))
        .containsExactly(ALWAYS);
    assertThat(names(registry.enabledToolDefinitions(context(WITH_MEMORY, NEW_INSTANCE))))
        .containsExactlyInAnyOrder(ALWAYS, WITH_MEMORY_ONLY);
  }

  @Test
  void offersNoToolToAnAgentWithoutTheRuntimeToolsFeature() {
    assertThat(registry.enabledToolDefinitions(context(WITH_MEMORY, WITHOUT_FEATURES))).isEmpty();
    assertThat(registry.findEnabled(context(WITH_MEMORY, WITHOUT_FEATURES), ALWAYS)).isEmpty();
  }

  @Test
  void findsAToolOnlyWhileItIsEnabled() {
    assertThat(registry.findEnabled(context(WITH_MEMORY, NEW_INSTANCE), WITH_MEMORY_ONLY))
        .isPresent();
    assertThat(registry.findEnabled(context(PLAIN, NEW_INSTANCE), WITH_MEMORY_ONLY)).isEmpty();
    assertThat(registry.findEnabled(context(WITH_MEMORY, NEW_INSTANCE), "unknown")).isEmpty();
  }

  @Test
  void aToolCanBeEnabledOnlyWithAFeatureOfTheAgentInstance() {
    final var featured =
        new RuntimeToolRegistryImpl(
            List.of(tool("acme_featured", context -> context.agentContext().supports("acme"))));

    assertThat(featured.findEnabled(context(PLAIN, NEW_INSTANCE), "acme_featured")).isEmpty();
    assertThat(
            featured.findEnabled(
                context(PLAIN, AgentContext.empty().withFeatures(List.of("runtimeTools", "acme"))),
                "acme_featured"))
        .isPresent();
  }

  @Test
  void keepsTheRegistrationOrderOfTheEnabledTools() {
    final var ordered =
        new RuntimeToolRegistryImpl(
            List.of(
                tool("acme_c", context -> true),
                tool("acme_hidden", context -> false),
                tool("acme_a", context -> true),
                tool("acme_b", context -> true)));

    assertThat(names(ordered.enabledToolDefinitions(context(PLAIN, NEW_INSTANCE))))
        .containsExactly("acme_c", "acme_a", "acme_b");
    assertThat(ordered.toolNames())
        .containsExactlyInAnyOrder("acme_a", "acme_b", "acme_c", "acme_hidden");
  }

  @Test
  void rejectsABlankToolName() {
    final var blank = StubRuntimeTool.of(" ", (context, call) -> null);

    assertThatThrownBy(() -> new RuntimeToolRegistryImpl(List.of(blank)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("non-blank");
  }

  @Test
  void rejectsAToolWhoseDefinitionDoesNotDeclareTheRuntimeKind() {
    final var process =
        StubRuntimeTool.of(
            ToolDefinition.builder().name("acme_process").inputSchema(Map.of()).build(),
            (context, call) -> null);

    assertThatThrownBy(() -> new RuntimeToolRegistryImpl(List.of(process)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("acme_process")
        .hasMessageContaining("RUNTIME");
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
    final var counted = new RuntimeToolRegistryImpl(List.of(counting));

    final var first = counted.enabledToolDefinitions(context(PLAIN, NEW_INSTANCE));
    final var second = counted.enabledToolDefinitions(context(PLAIN, NEW_INSTANCE));

    assertThat(first).containsExactly(definition);
    assertThat(second).containsExactly(definition);
    assertThat(reads).hasValue(1);
  }

  @Test
  void offersTheRuntimeDefinitionsWithTheRuntimeKind() {
    assertThat(registry.enabledToolDefinitions(context(WITH_MEMORY, NEW_INSTANCE)))
        .extracting(ToolDefinition::kind)
        .containsOnly(ToolKind.RUNTIME);
  }

  @Test
  void rejectsDuplicateToolNames() {
    final var first = StubRuntimeTool.of("acme_lookup", (context, call) -> null);
    final var second = StubRuntimeTool.of("acme_lookup", (context, call) -> null);

    assertThatThrownBy(() -> new RuntimeToolRegistryImpl(List.of(first, second)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("acme_lookup");
  }
}
