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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
  void allDefinitionsHaveSandboxGatewayType() {
    final var defs = SandboxToolDefinitions.sandboxToolDefinitions(ELEMENT_ID);
    assertThat(defs).allSatisfy(def -> assertThat("sandbox".equals(def.gatewayType())).isTrue());
  }

  @Test
  void eachDefinitionCarriesCorrectOperation() {
    final var defs = SandboxToolDefinitions.sandboxToolDefinitions(ELEMENT_ID);
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

  // -------------------------------------------------------------------------
  // Four-arg overload (with handle + workDir + catalog)
  // -------------------------------------------------------------------------

  @Test
  void fourArgOverload_returnsExactlyFiveDefinitions() {
    final var catalog =
        List.of(
            SkillCatalogEntry.builder()
                .name("my-skill")
                .description("does something")
                .location("loc1")
                .build());
    final var defs =
        SandboxToolDefinitions.sandboxToolDefinitions(ELEMENT_ID, "handle-abc", "/ws", catalog);
    assertThat(defs).hasSize(5);
  }

  @Test
  void fourArgOverload_allDefinitionsCarryHandleAndWorkDir() {
    final var defs =
        SandboxToolDefinitions.sandboxToolDefinitions(ELEMENT_ID, "handle-123", "/workspace", null);
    assertThat(defs)
        .allSatisfy(
            def ->
                assertThat(def.metadata())
                    .containsEntry(ToolDefinition.METADATA_ELEMENT_ID, ELEMENT_ID)
                    .containsEntry(SandboxToolDefinitions.METADATA_HANDLE, "handle-123")
                    .containsEntry(SandboxToolDefinitions.METADATA_WORK_DIR, "/workspace"));
  }

  @Test
  void fourArgOverload_catalogPresentInMetadata_whenNonEmpty() {
    final var catalog =
        List.of(
            SkillCatalogEntry.builder()
                .name("my-skill")
                .description("desc")
                .location("loc")
                .build());
    final var defs = SandboxToolDefinitions.sandboxToolDefinitions(ELEMENT_ID, "h", "/w", catalog);
    assertThat(defs)
        .allSatisfy(
            def -> assertThat(def.metadata()).containsKey(SandboxToolDefinitions.METADATA_CATALOG));
  }

  @ParameterizedTest
  @MethodSource("nullOrEmptyCatalog")
  void fourArgOverload_catalogAbsentFromMetadata_whenNullOrEmpty(List<SkillCatalogEntry> catalog) {
    final var defs = SandboxToolDefinitions.sandboxToolDefinitions(ELEMENT_ID, "h", "/w", catalog);
    assertThat(defs)
        .allSatisfy(
            def ->
                assertThat(def.metadata())
                    .doesNotContainKey(SandboxToolDefinitions.METADATA_CATALOG));
  }

  static Stream<Arguments> nullOrEmptyCatalog() {
    return Stream.of(Arguments.of((Object) null), Arguments.of(List.of()));
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
