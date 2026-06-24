/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SandboxToolDefinitionsTest {

  private static final String ELEMENT_ID = "Sandbox_Gateway_1";

  @Test
  void returnsExactlyFiveDefinitions() {
    final var defs = SandboxToolDefinitions.sandboxToolDefinitions(ELEMENT_ID);
    assertThat(defs).hasSize(5);
  }

  @Test
  void definitionsHaveExpectedNames() {
    final var defs = SandboxToolDefinitions.sandboxToolDefinitions(ELEMENT_ID);
    assertThat(defs)
        .extracting(ToolDefinition::name)
        .containsExactly(
            SandboxToolNames.BASH,
            SandboxToolNames.FS_READ,
            SandboxToolNames.FS_WRITE,
            SandboxToolNames.EXPORT_DOCUMENT,
            SandboxToolNames.IMPORT_DOCUMENT);
  }

  @Test
  void allDefinitionsAreSandboxTools() {
    final var defs = SandboxToolDefinitions.sandboxToolDefinitions(ELEMENT_ID);
    assertThat(defs).allSatisfy(def -> assertThat(def.isSandboxTool()).isTrue());
  }

  @Test
  void allDefinitionsCarryElementId() {
    final var defs = SandboxToolDefinitions.sandboxToolDefinitions(ELEMENT_ID);
    assertThat(defs)
        .allSatisfy(
            def ->
                assertThat(def.metadata())
                    .containsEntry(ToolDefinition.METADATA_ELEMENT_ID, ELEMENT_ID));
  }

  @Test
  void bashDefinitionHasRequiredCommandProperty() {
    final var bash =
        findByName(
            SandboxToolDefinitions.sandboxToolDefinitions(ELEMENT_ID), SandboxToolNames.BASH);
    assertThat(bash.description()).isNotBlank();
    assertRequiredFields(bash, List.of("command"));
    assertPropertyExists(bash, "command");
  }

  @Test
  void fsReadDefinitionHasRequiredPathProperty() {
    final var fsRead =
        findByName(
            SandboxToolDefinitions.sandboxToolDefinitions(ELEMENT_ID), SandboxToolNames.FS_READ);
    assertThat(fsRead.description()).isNotBlank();
    assertRequiredFields(fsRead, List.of("path"));
    assertPropertyExists(fsRead, "path");
  }

  @Test
  void fsWriteDefinitionHasRequiredPathAndContentProperties() {
    final var fsWrite =
        findByName(
            SandboxToolDefinitions.sandboxToolDefinitions(ELEMENT_ID), SandboxToolNames.FS_WRITE);
    assertThat(fsWrite.description()).isNotBlank();
    assertRequiredFields(fsWrite, List.of("path", "content"));
    assertPropertyExists(fsWrite, "path");
    assertPropertyExists(fsWrite, "content");
  }

  @Test
  void exportDocumentDefinitionHasRequiredPathProperty() {
    final var exportDoc =
        findByName(
            SandboxToolDefinitions.sandboxToolDefinitions(ELEMENT_ID),
            SandboxToolNames.EXPORT_DOCUMENT);
    assertThat(exportDoc.description()).isNotBlank();
    assertRequiredFields(exportDoc, List.of("path"));
    assertPropertyExists(exportDoc, "path");
  }

  @Test
  void importDocumentDefinitionHasRequiredIdProperty() {
    final var importDoc =
        findByName(
            SandboxToolDefinitions.sandboxToolDefinitions(ELEMENT_ID),
            SandboxToolNames.IMPORT_DOCUMENT);
    assertThat(importDoc.description()).isNotBlank();
    assertRequiredFields(importDoc, List.of("id"));
    assertPropertyExists(importDoc, "id");
    // path is optional (not in required)
    assertPropertyExists(importDoc, "path");
  }

  @Test
  void differentElementIdsProduceDifferentMetadata() {
    final var defs1 = SandboxToolDefinitions.sandboxToolDefinitions("Element_A");
    final var defs2 = SandboxToolDefinitions.sandboxToolDefinitions("Element_B");
    assertThat(defs1.getFirst().metadata())
        .containsEntry(ToolDefinition.METADATA_ELEMENT_ID, "Element_A");
    assertThat(defs2.getFirst().metadata())
        .containsEntry(ToolDefinition.METADATA_ELEMENT_ID, "Element_B");
  }

  private static ToolDefinition findByName(List<ToolDefinition> defs, String name) {
    return defs.stream()
        .filter(d -> name.equals(d.name()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Definition not found: " + name));
  }

  @SuppressWarnings("unchecked")
  private static void assertRequiredFields(ToolDefinition def, List<String> expectedRequired) {
    final var required = (List<String>) def.inputSchema().get("required");
    assertThat(required).containsExactlyElementsOf(expectedRequired);
  }

  @SuppressWarnings("unchecked")
  private static void assertPropertyExists(ToolDefinition def, String propertyName) {
    final var properties = (Map<String, Object>) def.inputSchema().get("properties");
    assertThat(properties).containsKey(propertyName);
  }
}
