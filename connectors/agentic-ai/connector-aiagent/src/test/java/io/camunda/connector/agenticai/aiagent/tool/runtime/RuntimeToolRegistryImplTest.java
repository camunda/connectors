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
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.request.MemoryTemplateConfiguration;
import java.util.List;
import java.util.Map;
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
    return StubRuntimeTool.of(name, (call, context) -> RuntimeToolResult.success(name))
        .enabledWhen(enabled);
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
    assertThat(names(registry.enabledToolDefinitions(PLAIN, NEW_INSTANCE))).containsExactly(ALWAYS);
    assertThat(names(registry.enabledToolDefinitions(WITH_MEMORY, NEW_INSTANCE)))
        .containsExactlyInAnyOrder(ALWAYS, WITH_MEMORY_ONLY);
  }

  @Test
  void offersNoToolToAnAgentWithoutTheRuntimeToolsFeature() {
    assertThat(registry.enabledTools(WITH_MEMORY, WITHOUT_FEATURES)).isEmpty();
    assertThat(registry.enabledToolDefinitions(WITH_MEMORY, WITHOUT_FEATURES)).isEmpty();
    assertThat(registry.findEnabled(WITH_MEMORY, WITHOUT_FEATURES, ALWAYS)).isEmpty();
  }

  @Test
  void findsAToolOnlyWhileItIsEnabled() {
    assertThat(registry.findEnabled(WITH_MEMORY, NEW_INSTANCE, WITH_MEMORY_ONLY)).isPresent();
    assertThat(registry.findEnabled(PLAIN, NEW_INSTANCE, WITH_MEMORY_ONLY)).isEmpty();
    assertThat(registry.findEnabled(WITH_MEMORY, NEW_INSTANCE, "unknown")).isEmpty();
  }

  @Test
  void aToolCanBeEnabledOnlyWithAFeatureOfTheAgentInstance() {
    final var featured =
        new RuntimeToolRegistryImpl(
            List.of(tool("acme_featured", context -> context.agentContext().supports("acme"))));

    assertThat(featured.findEnabled(PLAIN, NEW_INSTANCE, "acme_featured")).isEmpty();
    assertThat(
            featured.findEnabled(
                PLAIN,
                AgentContext.empty().withFeatures(List.of("runtimeTools", "acme")),
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

    assertThat(names(ordered.enabledToolDefinitions(PLAIN, NEW_INSTANCE)))
        .containsExactly("acme_c", "acme_a", "acme_b");
    assertThat(ordered.enabledTools(PLAIN, NEW_INSTANCE))
        .extracting(tool -> tool.definition().name())
        .containsExactly("acme_c", "acme_a", "acme_b");
    assertThat(ordered.toolNames())
        .containsExactlyInAnyOrder("acme_a", "acme_b", "acme_c", "acme_hidden");
  }

  @Test
  void rejectsABlankToolName() {
    final var blank = StubRuntimeTool.of(" ", (call, context) -> null);

    assertThatThrownBy(() -> new RuntimeToolRegistryImpl(List.of(blank)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("non-blank");
  }

  @Test
  void rejectsDuplicateToolNames() {
    final var first = StubRuntimeTool.of("acme_lookup", (call, context) -> null);
    final var second = StubRuntimeTool.of("acme_lookup", (call, context) -> null);

    assertThatThrownBy(() -> new RuntimeToolRegistryImpl(List.of(first, second)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("acme_lookup");
  }

  @Test
  void aNameOfARegisteredToolIsARuntimeToolWhetherEnabledOrNot() {
    // WITH_MEMORY_ONLY is not enabled for a plain agent, but the registry owns its name
    assertThat(registry.kindOf(NEW_INSTANCE, ALWAYS)).isEqualTo(ToolKind.RUNTIME);
    assertThat(registry.kindOf(NEW_INSTANCE, WITH_MEMORY_ONLY)).isEqualTo(ToolKind.RUNTIME);
  }

  @Test
  void anyOtherNameIsAProcessTool() {
    assertThat(registry.kindOf(NEW_INSTANCE, "getWeather")).isEqualTo(ToolKind.PROCESS);
  }

  @Test
  void everyNameIsAProcessToolForAnAgentWithoutTheRuntimeToolsFeature() {
    assertThat(registry.kindOf(WITHOUT_FEATURES, ALWAYS)).isEqualTo(ToolKind.PROCESS);
  }

  @Test
  void tagsEveryToolCallOfAMessageWithTheKindOfItsTool() {
    final var message =
        AssistantMessage.builder()
            .toolCalls(
                List.of(
                    new ToolCall("c1", ALWAYS, Map.of()),
                    new ToolCall("c2", "getWeather", Map.of()),
                    new ToolCall("c3", WITH_MEMORY_ONLY, Map.of("a", "b"))))
            .build();

    final var tagged = registry.tagToolCalls(NEW_INSTANCE, message);

    assertThat(tagged.toolCalls())
        .extracting(ToolCall::id, ToolCall::kind)
        .containsExactly(
            tuple("c1", ToolKind.RUNTIME),
            tuple("c2", ToolKind.PROCESS),
            tuple("c3", ToolKind.RUNTIME));
    assertThat(tagged.toolCalls().get(2).arguments()).containsEntry("a", "b");
    assertThat(tagged.id()).isEqualTo(message.id());
  }

  @Test
  void tagsNothingForAnAgentWithoutTheRuntimeToolsFeatureAndReturnsTheSameMessage() {
    final var message =
        AssistantMessage.builder().toolCalls(List.of(new ToolCall("c1", ALWAYS, Map.of()))).build();

    assertThat(registry.tagToolCalls(WITHOUT_FEATURES, message)).isSameAs(message);
  }

  @Test
  void returnsTheSameMessageWhenItHasNoToolCalls() {
    final var message = AssistantMessage.builder().build();

    assertThat(registry.tagToolCalls(NEW_INSTANCE, message)).isSameAs(message);
  }
}
