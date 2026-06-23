/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.internaltool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.model.document.DocumentRegistryEntry;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.sandbox.provider.fake.InMemorySandboxProvider;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSpec;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportDocumentToolHandlerTest {

  private static final long MAX_BYTES = 1024;

  private static final String DOC_ID = "doc-abc123";
  private static final String FILE_NAME = "report.pdf";
  private static final String CONTENT_TYPE = "application/pdf";

  @Mock private DocumentFactory mockDocumentFactory;
  @Mock private Document mockDocument;

  private InMemorySandboxProvider provider;
  private SandboxSession session;

  @BeforeEach
  void setUp() {
    provider = new InMemorySandboxProvider();
    session = provider.create(SandboxSpec.defaults());
  }

  /** Build a minimal registry entry (CamundaDocumentReference). */
  private static DocumentRegistryEntry entry(String id, String fileName, String contentType) {
    var ref = new CamundaDocumentReferenceModel("default", id, null, null);
    return new DocumentRegistryEntry(id, ref, fileName, contentType);
  }

  private static DocumentRegistry registryWith(DocumentRegistryEntry... entries) {
    return DocumentRegistry.of(List.of(entries));
  }

  private static InternalToolContext ctxWith(DocumentRegistry registry) {
    return new InternalToolContext(List.of(), null, registry);
  }

  private static ToolCall importCall(String id) {
    return ToolCall.builder()
        .id("import-1")
        .name(InternalToolNames.IMPORT_DOCUMENT)
        .arguments(Map.of("id", id))
        .build();
  }

  private static ToolCall importCallWithPath(String id, String path) {
    return ToolCall.builder()
        .id("import-2")
        .name(InternalToolNames.IMPORT_DOCUMENT)
        .arguments(Map.of("id", id, "path", path))
        .build();
  }

  // ---------------------------------------------------------------------------
  // (a) Success — in-registry id resolves, bytes written to default <workDir>/<fileName>
  // ---------------------------------------------------------------------------

  @Test
  void execute_inRegistryId_writesToDefaultWorkDirFileName() {
    byte[] content = "PDF content".getBytes(StandardCharsets.UTF_8);
    when(mockDocumentFactory.resolve(any())).thenReturn(mockDocument);
    when(mockDocument.asByteArray()).thenReturn(content);

    var registry = registryWith(entry(DOC_ID, FILE_NAME, CONTENT_TYPE));
    var handler = new ImportDocumentToolHandler(mockDocumentFactory, MAX_BYTES);

    var result = handler.execute(importCall(DOC_ID), session, ctxWith(registry));

    assertThat(result.id()).isEqualTo("import-1");
    assertThat(result.name()).isEqualTo(InternalToolNames.IMPORT_DOCUMENT);
    assertThat(result.content()).asString().doesNotContain("Error:");
    assertThat(result.content()).asString().contains(FILE_NAME);
    assertThat(result.content()).asString().contains(String.valueOf(content.length));
    assertThat(result.content()).asString().contains(CONTENT_TYPE);

    // Verify the file was written to the expected path
    String expectedPath = session.workDir() + "/" + FILE_NAME;
    assertThat(result.content()).asString().contains(expectedPath);
    assertThat(session.fs().read(expectedPath)).isEqualTo(content);
  }

  // ---------------------------------------------------------------------------
  // (b) Explicit path is honored
  // ---------------------------------------------------------------------------

  @Test
  void execute_explicitPath_writesToExplicitPath() {
    byte[] content = "data".getBytes(StandardCharsets.UTF_8);
    when(mockDocumentFactory.resolve(any())).thenReturn(mockDocument);
    when(mockDocument.asByteArray()).thenReturn(content);

    var registry = registryWith(entry(DOC_ID, FILE_NAME, CONTENT_TYPE));
    var handler = new ImportDocumentToolHandler(mockDocumentFactory, MAX_BYTES);

    String explicitPath = "/custom/path/imported.pdf";
    var result =
        handler.execute(importCallWithPath(DOC_ID, explicitPath), session, ctxWith(registry));

    assertThat(result.content()).asString().doesNotContain("Error:");
    assertThat(result.content()).asString().contains(explicitPath);
    assertThat(session.fs().read(explicitPath)).isEqualTo(content);

    // Default path should NOT have the file
    String defaultPath = session.workDir() + "/" + FILE_NAME;
    org.junit.jupiter.api.Assertions.assertThrows(
        Exception.class, () -> session.fs().read(defaultPath));
  }

  // ---------------------------------------------------------------------------
  // (c) Id NOT in registry → error result listing available handles
  // ---------------------------------------------------------------------------

  @Test
  void execute_idNotInRegistry_returnsErrorListingAvailableHandles() {
    var entry1 = entry("doc-aaa", "file1.txt", "text/plain");
    var entry2 = entry("doc-bbb", "file2.pdf", "application/pdf");
    var registry = registryWith(entry1, entry2);
    var handler = new ImportDocumentToolHandler(mockDocumentFactory, MAX_BYTES);

    var result = handler.execute(importCall("doc-unknown"), session, ctxWith(registry));

    assertThat(result.content()).asString().contains("Error:");
    assertThat(result.content()).asString().contains("doc-unknown");
    assertThat(result.content()).asString().contains("doc-aaa");
    assertThat(result.content()).asString().contains("doc-bbb");
    verify(mockDocumentFactory, never()).resolve(any());
  }

  @Test
  void execute_emptyRegistry_returnsErrorWithNoneMessage() {
    var registry = DocumentRegistry.empty();
    var handler = new ImportDocumentToolHandler(mockDocumentFactory, MAX_BYTES);

    var result = handler.execute(importCall("any-id"), session, ctxWith(registry));

    assertThat(result.content()).asString().contains("Error:");
    assertThat(result.content()).asString().containsIgnoringCase("none");
    verify(mockDocumentFactory, never()).resolve(any());
  }

  // ---------------------------------------------------------------------------
  // (d) Over-cap document → refused
  // ---------------------------------------------------------------------------

  @Test
  void execute_documentTooLarge_returnsErrorAndDoesNotWriteToFs() {
    // Max = 10, document = 100 bytes
    var handler = new ImportDocumentToolHandler(mockDocumentFactory, 10);
    byte[] bigContent = "X".repeat(100).getBytes(StandardCharsets.UTF_8);
    when(mockDocumentFactory.resolve(any())).thenReturn(mockDocument);
    when(mockDocument.asByteArray()).thenReturn(bigContent);

    var registry = registryWith(entry(DOC_ID, FILE_NAME, CONTENT_TYPE));
    var result = handler.execute(importCall(DOC_ID), session, ctxWith(registry));

    assertThat(result.content()).asString().contains("Error:");
    assertThat(result.content()).asString().containsIgnoringCase("too large");

    // File must NOT be on the FS
    String expectedPath = session.workDir() + "/" + FILE_NAME;
    org.junit.jupiter.api.Assertions.assertThrows(
        Exception.class, () -> session.fs().read(expectedPath));
  }

  // ---------------------------------------------------------------------------
  // (e) Missing/blank id → error
  // ---------------------------------------------------------------------------

  @Test
  void execute_missingId_returnsError() {
    var handler = new ImportDocumentToolHandler(mockDocumentFactory, MAX_BYTES);
    var call =
        ToolCall.builder()
            .id("i-nop")
            .name(InternalToolNames.IMPORT_DOCUMENT)
            .arguments(Map.of())
            .build();

    var result = handler.execute(call, session, ctxWith(DocumentRegistry.empty()));

    assertThat(result.content()).asString().contains("Error:");
    verify(mockDocumentFactory, never()).resolve(any());
  }

  @Test
  void execute_blankId_returnsError() {
    var handler = new ImportDocumentToolHandler(mockDocumentFactory, MAX_BYTES);
    var call =
        ToolCall.builder()
            .id("i-blank")
            .name(InternalToolNames.IMPORT_DOCUMENT)
            .arguments(Map.of("id", "   "))
            .build();

    var result = handler.execute(call, session, ctxWith(DocumentRegistry.empty()));

    assertThat(result.content()).asString().contains("Error:");
    verify(mockDocumentFactory, never()).resolve(any());
  }

  // ---------------------------------------------------------------------------
  // executedBy tag always present
  // ---------------------------------------------------------------------------

  @Test
  void execute_resultAlwaysTaggedExecutedBySandbox() {
    byte[] content = "x".getBytes(StandardCharsets.UTF_8);
    when(mockDocumentFactory.resolve(any())).thenReturn(mockDocument);
    when(mockDocument.asByteArray()).thenReturn(content);

    var registry = registryWith(entry(DOC_ID, FILE_NAME, CONTENT_TYPE));
    var handler = new ImportDocumentToolHandler(mockDocumentFactory, MAX_BYTES);

    var result = handler.execute(importCall(DOC_ID), session, ctxWith(registry));

    assertThat(result.properties())
        .containsEntry(
            InternalToolExecutor.PROPERTY_EXECUTED_BY, InternalToolExecutor.EXECUTED_BY_SANDBOX);
  }

  @Test
  void execute_errorResultAlsoTaggedExecutedBySandbox() {
    var handler = new ImportDocumentToolHandler(mockDocumentFactory, MAX_BYTES);
    var call =
        ToolCall.builder()
            .id("i-err")
            .name(InternalToolNames.IMPORT_DOCUMENT)
            .arguments(Map.of())
            .build();

    var result = handler.execute(call, session, ctxWith(DocumentRegistry.empty()));

    assertThat(result.properties())
        .containsEntry(
            InternalToolExecutor.PROPERTY_EXECUTED_BY, InternalToolExecutor.EXECUTED_BY_SANDBOX);
  }

  // ---------------------------------------------------------------------------
  // Tool definition
  // ---------------------------------------------------------------------------

  @Test
  void definition_shouldHaveCorrectNameAndSchema() {
    var handler = new ImportDocumentToolHandler(mockDocumentFactory, MAX_BYTES);

    assertThat(handler.name()).isEqualTo(InternalToolNames.IMPORT_DOCUMENT);
    assertThat(handler.definition().name()).isEqualTo(InternalToolNames.IMPORT_DOCUMENT);
    assertThat(handler.definition().description()).isNotBlank();
    assertThat(handler.definition().isSandboxTool()).isTrue();

    @SuppressWarnings("unchecked")
    Map<String, Object> props =
        (Map<String, Object>) handler.definition().inputSchema().get("properties");
    assertThat(props).containsKey("id");
    assertThat(props).containsKey("path");

    @SuppressWarnings("unchecked")
    List<String> required = (List<String>) handler.definition().inputSchema().get("required");
    assertThat(required).contains("id");
    assertThat(required).doesNotContain("path");
  }
}
