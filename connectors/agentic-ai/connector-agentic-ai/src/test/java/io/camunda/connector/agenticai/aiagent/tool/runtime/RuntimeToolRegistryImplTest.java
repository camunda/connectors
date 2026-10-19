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

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.runtime.builtin.ReturnDocumentTool;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
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
              new MemoryConfiguration(null, 10),
              null,
              null,
              null));

  private static final AgentContext NEW_INSTANCE =
      AgentContext.empty().withFeatures(AgentContext.newInstanceFeatures());
  private static final AgentContext WITHOUT_FEATURES = AgentContext.empty();
  private static final AgentContext RUNTIME_TOOLS_ONLY = withFeatures(Set.of("runtimeTools"));
  private static final AgentContext DOCUMENTS_ONLY = withFeatures(Set.of("documents"));

  private final RuntimeToolRegistry registry =
      new RuntimeToolRegistryImpl(
          List.of(new ReturnDocumentTool(TestObjectMapperSupplier.INSTANCE), new SaveNoteTool()));

  private static AgentContext withFeatures(Set<String> features) {
    return AgentContext.empty().withFeatures(new LinkedHashSet<>(features));
  }

  private static RuntimeTool tool(
      String name, BiPredicate<AgentExecutionContext, AgentContext> availability) {
    return StubRuntimeTool.of(
            name, (call, executionContext, conversation) -> RuntimeToolResult.success(name))
        .availableWhen(availability);
  }

  private static List<String> names(List<ToolDefinition> definitions) {
    return definitions.stream().map(ToolDefinition::name).toList();
  }

  @Test
  void reservesTheNamesOfAllRegisteredToolsWhetherAvailableOrNot() {
    assertThat(registry.toolNames())
        .containsExactlyInAnyOrder(ReturnDocumentTool.NAME, SaveNoteTool.NAME);
  }

  @Test
  void offersOnlyTheToolsAvailableForTheAgentExecution() {
    assertThat(names(registry.availableToolDefinitions(PLAIN, NEW_INSTANCE)))
        .containsExactly(ReturnDocumentTool.NAME);
    assertThat(names(registry.availableToolDefinitions(WITH_MEMORY, NEW_INSTANCE)))
        .containsExactlyInAnyOrder(ReturnDocumentTool.NAME, SaveNoteTool.NAME);
  }

  @Test
  void offersNoToolToAnAgentWithoutTheRuntimeToolsFeature() {
    assertThat(registry.availableTools(WITH_MEMORY, WITHOUT_FEATURES)).isEmpty();
    assertThat(registry.availableToolDefinitions(WITH_MEMORY, WITHOUT_FEATURES)).isEmpty();
    assertThat(registry.findAvailable(ReturnDocumentTool.NAME, WITH_MEMORY, WITHOUT_FEATURES))
        .isEmpty();
  }

  @Test
  void offersOnlyTheToolsThatDoNotNeedDocumentsToAnAgentWithOnlyTheRuntimeToolsFeature() {
    assertThat(registry.availableToolDefinitions(PLAIN, RUNTIME_TOOLS_ONLY)).isEmpty();
    assertThat(names(registry.availableToolDefinitions(WITH_MEMORY, RUNTIME_TOOLS_ONLY)))
        .containsExactly(SaveNoteTool.NAME);
    assertThat(registry.findAvailable(ReturnDocumentTool.NAME, PLAIN, RUNTIME_TOOLS_ONLY))
        .isEmpty();
  }

  @Test
  void treatsTheDocumentsFeatureAsImplyingTheRuntimeToolsFeature() {
    assertThat(names(registry.availableToolDefinitions(PLAIN, DOCUMENTS_ONLY)))
        .containsExactly(ReturnDocumentTool.NAME);
  }

  @Test
  void findsAToolOnlyWhileItIsAvailable() {
    assertThat(registry.findAvailable(SaveNoteTool.NAME, WITH_MEMORY, NEW_INSTANCE)).isPresent();
    assertThat(registry.findAvailable(SaveNoteTool.NAME, PLAIN, NEW_INSTANCE)).isEmpty();
    assertThat(registry.findAvailable("unknown", WITH_MEMORY, NEW_INSTANCE)).isEmpty();
  }

  @Test
  void aToolCanBeAvailableOnlyWithAFeatureOfTheAgentInstance() {
    final var featured =
        new RuntimeToolRegistryImpl(
            List.of(tool("acme_featured", (execution, agent) -> agent.supports("acme"))));

    assertThat(featured.findAvailable("acme_featured", PLAIN, withFeatures(Set.of("runtimeTools"))))
        .isEmpty();
    assertThat(
            featured.findAvailable(
                "acme_featured", PLAIN, withFeatures(Set.of("runtimeTools", "acme"))))
        .isPresent();
  }

  @Test
  void aToolCanBeAvailableOnlyWithAConfigurationValue() {
    final var configured =
        new RuntimeToolRegistryImpl(
            List.of(
                tool(
                    "acme_configured",
                    (execution, agent) -> execution.configuration().memory() != null)));

    assertThat(configured.availableTools(PLAIN, NEW_INSTANCE)).isEmpty();
    assertThat(configured.availableTools(WITH_MEMORY, NEW_INSTANCE)).hasSize(1);
  }

  @Test
  void keepsTheRegistrationOrderOfTheAvailableTools() {
    final var ordered =
        new RuntimeToolRegistryImpl(
            List.of(
                tool("acme_c", (execution, agent) -> true),
                tool("acme_hidden", (execution, agent) -> false),
                tool("acme_a", (execution, agent) -> true),
                tool("acme_b", (execution, agent) -> true)));

    assertThat(names(ordered.availableToolDefinitions(PLAIN, NEW_INSTANCE)))
        .containsExactly("acme_c", "acme_a", "acme_b");
    assertThat(ordered.availableTools(PLAIN, NEW_INSTANCE))
        .extracting(tool -> tool.definition().name())
        .containsExactly("acme_c", "acme_a", "acme_b");
    assertThat(ordered.toolNames())
        .containsExactlyInAnyOrder("acme_a", "acme_b", "acme_c", "acme_hidden");
  }

  @Test
  void acceptsAToolNameOutsideTheBuiltInPrefix() {
    final var custom =
        StubRuntimeTool.of("acme_lookup", (call, executionContext, conversation) -> null);

    assertThat(new RuntimeToolRegistryImpl(List.of(custom)).toolNames())
        .containsExactly("acme_lookup");
  }

  @Test
  void rejectsABlankToolName() {
    final var blank = StubRuntimeTool.of(" ", (call, executionContext, conversation) -> null);

    assertThatThrownBy(() -> new RuntimeToolRegistryImpl(List.of(blank)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("non-blank");
  }

  @Test
  void rejectsDuplicateToolNames() {
    assertThatThrownBy(
            () -> new RuntimeToolRegistryImpl(List.of(new SaveNoteTool(), new SaveNoteTool())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("c8__save_note");
  }
}
