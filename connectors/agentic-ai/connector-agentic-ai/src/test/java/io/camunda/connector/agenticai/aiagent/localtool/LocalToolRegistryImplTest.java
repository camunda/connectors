/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.agenticai.aiagent.localtool;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_CHAT_MODEL;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_SYSTEM_PROMPT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentTaskResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalToolRegistryImplTest {

  private static final AgentConfiguration PLAIN =
      new AgentConfiguration(TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null);
  private static final AgentConfiguration WITH_MEMORY =
      new AgentConfiguration(
          TEST_CHAT_MODEL,
          TEST_SYSTEM_PROMPT,
          null,
          new MemoryConfiguration(null, 10),
          null,
          null,
          null);
  private static final AgentConfiguration RETURNING_DOCUMENTS =
      new AgentConfiguration(
          TEST_CHAT_MODEL,
          TEST_SYSTEM_PROMPT,
          null,
          null,
          null,
          null,
          new AgentTaskResponseConfiguration(null, null, true));

  private final LocalToolRegistry registry =
      new LocalToolRegistryImpl(List.of(new ReturnDocumentsTool(), SaveNoteTool.create()));

  @Test
  void reservesTheNamesOfAllRegisteredToolsWhetherAvailableOrNot() {
    assertThat(registry.toolNames())
        .containsExactlyInAnyOrder(ReturnDocumentsTool.NAME, SaveNoteTool.NAME);
  }

  @Test
  void offersOnlyTheToolsAvailableForTheConfiguration() {
    assertThat(registry.availableToolDefinitions(PLAIN)).isEmpty();
    assertThat(registry.availableToolDefinitions(WITH_MEMORY))
        .extracting(definition -> definition.name())
        .containsExactly(SaveNoteTool.NAME);
    assertThat(registry.availableToolDefinitions(RETURNING_DOCUMENTS))
        .extracting(definition -> definition.name())
        .containsExactly(ReturnDocumentsTool.NAME);
  }

  @Test
  void findsAToolOnlyWhileItIsAvailable() {
    assertThat(registry.findAvailable(SaveNoteTool.NAME, WITH_MEMORY)).isPresent();
    assertThat(registry.findAvailable(SaveNoteTool.NAME, PLAIN)).isEmpty();
    assertThat(registry.findAvailable("unknown", WITH_MEMORY)).isEmpty();
  }

  @Test
  void findsTheDefinitionOfARegisteredToolWhetherAvailableOrNot() {
    assertThat(registry.findDefinition(SaveNoteTool.NAME))
        .hasValueSatisfying(
            definition -> assertThat(definition.name()).isEqualTo(SaveNoteTool.NAME));
    assertThat(registry.findDefinition("unknown")).isEmpty();
  }

  @Test
  void rejectsDuplicateToolNames() {
    assertThatThrownBy(
            () -> new LocalToolRegistryImpl(List.of(SaveNoteTool.create(), SaveNoteTool.create())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("save_note");
  }
}
