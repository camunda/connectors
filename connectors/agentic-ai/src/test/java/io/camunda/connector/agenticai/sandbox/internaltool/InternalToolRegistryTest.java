/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.internaltool;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InternalToolRegistryTest {

  private InternalToolRegistry registry;

  @BeforeEach
  void setUp() {
    registry =
        new InternalToolRegistry(
            List.of(new BashToolHandler(), new FsReadToolHandler(), new FsWriteToolHandler()));
  }

  @Test
  void toolNames_shouldContainAllThreeHandlers() {
    assertThat(registry.toolNames())
        .containsExactlyInAnyOrder(
            InternalToolNames.BASH, InternalToolNames.FS_READ, InternalToolNames.FS_WRITE);
  }

  @Test
  void toolDefinitions_shouldReturnDefinitionForEachHandler() {
    assertThat(registry.toolDefinitions()).hasSize(3);
    assertThat(registry.toolDefinitions())
        .extracting(td -> td.name())
        .containsExactlyInAnyOrder(
            InternalToolNames.BASH, InternalToolNames.FS_READ, InternalToolNames.FS_WRITE);
  }

  @Test
  void isInternalTool_knownName_shouldReturnTrue() {
    assertThat(registry.isInternalTool(InternalToolNames.BASH)).isTrue();
    assertThat(registry.isInternalTool(InternalToolNames.FS_READ)).isTrue();
    assertThat(registry.isInternalTool(InternalToolNames.FS_WRITE)).isTrue();
  }

  @Test
  void isInternalTool_unknownName_shouldReturnFalse() {
    assertThat(registry.isInternalTool("some_external_tool")).isFalse();
    assertThat(registry.isInternalTool("")).isFalse();
  }

  @Test
  void isInternalTool_reservedButUnregisteredNames_shouldReturnFalse() {
    // export_document, load_skill, and import_document are reserved constants but not registered in
    // this registry
    assertThat(registry.isInternalTool(InternalToolNames.EXPORT_DOCUMENT)).isFalse();
    assertThat(registry.isInternalTool(InternalToolNames.LOAD_SKILL)).isFalse();
    assertThat(registry.isInternalTool(InternalToolNames.IMPORT_DOCUMENT)).isFalse();
  }

  @Test
  void isInternalToolCall_shouldDelegateToToolName() {
    ToolCall bashCall =
        ToolCall.builder().id("1").name(InternalToolNames.BASH).arguments(Map.of()).build();
    ToolCall externalCall =
        ToolCall.builder().id("2").name("some_bpmn_element").arguments(Map.of()).build();

    assertThat(registry.isInternalToolCall(bashCall)).isTrue();
    assertThat(registry.isInternalToolCall(externalCall)).isFalse();
  }

  @Test
  void toolDefinitions_allInternalToolsAreMarkedAsSandboxTools() {
    assertThat(registry.toolDefinitions())
        .allSatisfy(td -> assertThat(td.isSandboxTool()).isTrue());
  }

  @Test
  void nonSandboxToolDefinition_hasEmptyMetadataAndIsSandboxToolReturnsFalse() {
    ToolDefinition nonSandbox =
        ToolDefinition.builder()
            .name("my_bpmn_tool")
            .description("A regular BPMN tool")
            .inputSchema(Map.of())
            .build();

    assertThat(nonSandbox.isSandboxTool()).isFalse();
    assertThat(nonSandbox.metadata()).isEmpty();
  }
}
