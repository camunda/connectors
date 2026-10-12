/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.agenticai.aiagent.tool.runtime;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_CHAT_MODEL;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_SYSTEM_PROMPT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.document.ReturnDocumentTool;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuntimeToolRegistryImplTest {

  private static final AgentConfiguration PLAIN =
      new AgentConfiguration(TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null)
          .withFeatures(AgentContext.newInstanceFeatures());
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
  private static final AgentConfiguration WITHOUT_FEATURES = PLAIN.withFeatures(Set.of());
  private static final AgentConfiguration RUNTIME_TOOLS_ONLY =
      PLAIN.withFeatures(Set.of(AgentContext.FEATURE_RUNTIME_TOOLS));
  private static final AgentConfiguration DOCUMENTS_ONLY =
      PLAIN.withFeatures(Set.of(AgentContext.FEATURE_DOCUMENTS));

  private final RuntimeToolRegistry registry =
      new RuntimeToolRegistryImpl(List.of(new ReturnDocumentTool(), SaveNoteTool.create()));

  @Test
  void reservesTheNamesOfAllRegisteredToolsWhetherAvailableOrNot() {
    assertThat(registry.toolNames())
        .containsExactlyInAnyOrder(ReturnDocumentTool.NAME, SaveNoteTool.NAME);
  }

  @Test
  void offersOnlyTheToolsAvailableForTheConfiguration() {
    assertThat(registry.availableToolDefinitions(PLAIN))
        .extracting(definition -> definition.name())
        .containsExactly(ReturnDocumentTool.NAME);
    assertThat(registry.availableToolDefinitions(WITH_MEMORY))
        .extracting(definition -> definition.name())
        .containsExactlyInAnyOrder(ReturnDocumentTool.NAME, SaveNoteTool.NAME);
  }

  @Test
  void offersNoToolToAnAgentWithoutTheRuntimeToolsFeature() {
    assertThat(registry.availableTools(WITHOUT_FEATURES)).isEmpty();
    assertThat(registry.availableToolDefinitions(WITHOUT_FEATURES)).isEmpty();
    assertThat(registry.findAvailable(ReturnDocumentTool.NAME, WITHOUT_FEATURES)).isEmpty();
  }

  @Test
  void offersOnlyTheToolsThatDoNotNeedDocumentsToAnAgentWithOnlyTheRuntimeToolsFeature() {
    final var withMemory = WITH_MEMORY.withFeatures(Set.of(AgentContext.FEATURE_RUNTIME_TOOLS));

    assertThat(registry.availableToolDefinitions(RUNTIME_TOOLS_ONLY)).isEmpty();
    assertThat(registry.availableToolDefinitions(withMemory))
        .extracting(definition -> definition.name())
        .containsExactly(SaveNoteTool.NAME);
    assertThat(registry.findAvailable(ReturnDocumentTool.NAME, RUNTIME_TOOLS_ONLY)).isEmpty();
  }

  @Test
  void treatsTheDocumentsFeatureAsImplyingTheRuntimeToolsFeature() {
    assertThat(registry.availableToolDefinitions(DOCUMENTS_ONLY))
        .extracting(definition -> definition.name())
        .containsExactly(ReturnDocumentTool.NAME);
  }

  @Test
  void findsAToolOnlyWhileItIsAvailable() {
    assertThat(registry.findAvailable(SaveNoteTool.NAME, WITH_MEMORY)).isPresent();
    assertThat(registry.findAvailable(SaveNoteTool.NAME, PLAIN)).isEmpty();
    assertThat(registry.findAvailable("unknown", WITH_MEMORY)).isEmpty();
  }

  @Test
  void acceptsAToolNameOutsideTheBuiltInPrefix() {
    final var custom = RuntimeTool.builder("acme_lookup").handler((call, context) -> null);

    assertThat(new RuntimeToolRegistryImpl(List.of(custom)).toolNames())
        .containsExactly("acme_lookup");
  }

  @Test
  void rejectsABlankToolName() {
    final var blank = RuntimeTool.builder(" ").handler((call, context) -> null);

    assertThatThrownBy(() -> new RuntimeToolRegistryImpl(List.of(blank)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("non-blank");
  }

  @Test
  void rejectsDuplicateToolNames() {
    assertThatThrownBy(
            () ->
                new RuntimeToolRegistryImpl(List.of(SaveNoteTool.create(), SaveNoteTool.create())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("c8__save_note");
  }
}
