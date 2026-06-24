/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SandboxToolDefinitionsTest {

  @Test
  void returnsExactlyFiveDefinitions() {
    final var defs = SandboxToolDefinitions.sandboxToolDefinitions();
    assertThat(defs).hasSize(5);
  }

  @Test
  void definitionsHaveExpectedNames() {
    final var defs = SandboxToolDefinitions.sandboxToolDefinitions();
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
  void allDefinitionsHaveSandboxGatewayType() {
    final var defs = SandboxToolDefinitions.sandboxToolDefinitions();
    assertThat(defs).allSatisfy(def -> assertThat("sandbox".equals(def.gatewayType())).isTrue());
  }

  @Test
  void eachDefinitionCarriesCorrectOperation() {
    final var defs = SandboxToolDefinitions.sandboxToolDefinitions();
    final var bash = findByName(defs, SandboxToolNames.BASH);
    final var fsRead = findByName(defs, SandboxToolNames.FS_READ);
    final var fsWrite = findByName(defs, SandboxToolNames.FS_WRITE);
    final var exportDoc = findByName(defs, SandboxToolNames.EXPORT_DOCUMENT);
    final var importDoc = findByName(defs, SandboxToolNames.IMPORT_DOCUMENT);

    assertThat(bash.metadata())
        .containsEntry(SandboxToolDefinitions.METADATA_OPERATION, SandboxOperation.BASH);
    assertThat(fsRead.metadata())
        .containsEntry(SandboxToolDefinitions.METADATA_OPERATION, SandboxOperation.FS_READ);
    assertThat(fsWrite.metadata())
        .containsEntry(SandboxToolDefinitions.METADATA_OPERATION, SandboxOperation.FS_WRITE);
    assertThat(exportDoc.metadata())
        .containsEntry(SandboxToolDefinitions.METADATA_OPERATION, SandboxOperation.EXPORT_DOCUMENT);
    assertThat(importDoc.metadata())
        .containsEntry(SandboxToolDefinitions.METADATA_OPERATION, SandboxOperation.IMPORT_DOCUMENT);
  }

  @Test
  void allDefinitionsCarryOnlyGatewayTypeAndOperation() {
    // Per-tool metadata must NOT contain elementId, handle, workDir, or catalog —
    // those are stored once in SandboxState (agentContext.properties["sandbox"]).
    final var defs = SandboxToolDefinitions.sandboxToolDefinitions();
    assertThat(defs)
        .allSatisfy(
            def ->
                assertThat(def.metadata())
                    .containsOnlyKeys(
                        ToolDefinition.METADATA_GATEWAY_TYPE,
                        SandboxToolDefinitions.METADATA_OPERATION));
  }

  @Test
  void bashDefinitionHasRequiredCommandProperty() {
    final var bash =
        findByName(SandboxToolDefinitions.sandboxToolDefinitions(), SandboxToolNames.BASH);
    assertThat(bash.description()).isNotBlank();
    assertRequiredFields(bash, List.of("command"));
    assertPropertyExists(bash, "command");
  }

  @Test
  void fsReadDefinitionHasRequiredPathProperty() {
    final var fsRead =
        findByName(SandboxToolDefinitions.sandboxToolDefinitions(), SandboxToolNames.FS_READ);
    assertThat(fsRead.description()).isNotBlank();
    assertRequiredFields(fsRead, List.of("path"));
    assertPropertyExists(fsRead, "path");
  }

  @Test
  void fsWriteDefinitionHasRequiredPathAndContentProperties() {
    final var fsWrite =
        findByName(SandboxToolDefinitions.sandboxToolDefinitions(), SandboxToolNames.FS_WRITE);
    assertThat(fsWrite.description()).isNotBlank();
    assertRequiredFields(fsWrite, List.of("path", "content"));
    assertPropertyExists(fsWrite, "path");
    assertPropertyExists(fsWrite, "content");
  }

  @Test
  void exportDocumentDefinitionHasRequiredPathProperty() {
    final var exportDoc =
        findByName(
            SandboxToolDefinitions.sandboxToolDefinitions(), SandboxToolNames.EXPORT_DOCUMENT);
    assertThat(exportDoc.description()).isNotBlank();
    assertRequiredFields(exportDoc, List.of("path"));
    assertPropertyExists(exportDoc, "path");
  }

  @Test
  void importDocumentDefinitionHasRequiredIdProperty() {
    final var importDoc =
        findByName(
            SandboxToolDefinitions.sandboxToolDefinitions(), SandboxToolNames.IMPORT_DOCUMENT);
    assertThat(importDoc.description()).isNotBlank();
    assertRequiredFields(importDoc, List.of("id"));
    assertPropertyExists(importDoc, "id");
    // path is optional (not in required)
    assertPropertyExists(importDoc, "path");
  }

  @Test
  void multipleCallsProduceEqualDefinitions() {
    // The fixed tool defs are stateless; calling sandboxToolDefinitions() twice
    // must produce structurally equal results.
    final var defs1 = SandboxToolDefinitions.sandboxToolDefinitions();
    final var defs2 = SandboxToolDefinitions.sandboxToolDefinitions();
    assertThat(defs1).usingRecursiveComparison().isEqualTo(defs2);
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
