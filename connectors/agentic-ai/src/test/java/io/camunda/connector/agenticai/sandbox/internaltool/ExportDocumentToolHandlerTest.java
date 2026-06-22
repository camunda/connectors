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

import io.camunda.connector.agenticai.aiagent.agent.ToolCallResultDocumentExtractor;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistryImpl;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.sandbox.provider.fake.InMemorySandboxProvider;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSpec;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExportDocumentToolHandlerTest {

  private static final long MAX_BYTES = 1024;

  private final InMemoryDocumentStore documentStore = InMemoryDocumentStore.INSTANCE;
  private final DocumentFactory realDocumentFactory = new DocumentFactoryImpl(documentStore);

  @Mock private DocumentFactory mockDocumentFactory;
  @Mock private Document mockDocument;

  private InMemorySandboxProvider provider;
  private SandboxSession session;

  @BeforeEach
  void setUp() {
    documentStore.clear();
    provider = new InMemorySandboxProvider();
    session = provider.create(SandboxSpec.defaults());
  }

  private static ToolCall exportCall(String path) {
    return ToolCall.builder()
        .id("export-1")
        .name(InternalToolNames.EXPORT_DOCUMENT)
        .arguments(Map.of("path", path))
        .build();
  }

  // --- Happy path ---

  @Test
  void execute_textFile_shouldReturnSummaryAndDocument() {
    byte[] content = "Hello, world!".getBytes(StandardCharsets.UTF_8);
    session.fs().write("/workspace/hello.txt", content);

    when(mockDocumentFactory.create(any())).thenReturn(mockDocument);

    ExportDocumentToolHandler handler =
        new ExportDocumentToolHandler(mockDocumentFactory, MAX_BYTES);

    ToolCallResult result = handler.execute(exportCall("/workspace/hello.txt"), session);

    assertThat(result.id()).isEqualTo("export-1");
    assertThat(result.name()).isEqualTo(InternalToolNames.EXPORT_DOCUMENT);

    // Content must be a List
    assertThat(result.content()).isInstanceOf(List.class);
    @SuppressWarnings("unchecked")
    List<Object> contentList = (List<Object>) result.content();
    assertThat(contentList).hasSize(2);

    // First element: summary string
    assertThat(contentList.get(0)).isInstanceOf(String.class);
    String summary = (String) contentList.get(0);
    assertThat(summary).contains("/workspace/hello.txt");
    assertThat(summary).contains(String.valueOf(content.length));
    assertThat(summary).contains("text/plain");

    // Second element: the raw Document
    assertThat(contentList.get(1)).isSameAs(mockDocument);
  }

  @Test
  void execute_textFile_shouldCallDocumentFactoryWithCorrectContentTypeAndFileName() {
    byte[] content = "{}".getBytes(StandardCharsets.UTF_8);
    session.fs().write("/workspace/data.json", content);

    when(mockDocumentFactory.create(any())).thenReturn(mockDocument);

    ExportDocumentToolHandler handler =
        new ExportDocumentToolHandler(mockDocumentFactory, MAX_BYTES);

    handler.execute(exportCall("/workspace/data.json"), session);

    ArgumentCaptor<DocumentCreationRequest> captor =
        ArgumentCaptor.forClass(DocumentCreationRequest.class);
    verify(mockDocumentFactory).create(captor.capture());
    DocumentCreationRequest req = captor.getValue();
    assertThat(req.contentType()).isEqualTo("application/json");
    assertThat(req.fileName()).isEqualTo("data.json");
  }

  @Test
  void execute_binaryFile_shouldAlsoExportSuccessfully() {
    // Binary file (PDF-like, contains NUL byte)
    byte[] binaryBytes = new byte[] {0x25, 0x50, 0x44, 0x46, 0x00, 0x01};
    session.fs().write("/workspace/report.pdf", binaryBytes);

    when(mockDocumentFactory.create(any())).thenReturn(mockDocument);

    ExportDocumentToolHandler handler =
        new ExportDocumentToolHandler(mockDocumentFactory, MAX_BYTES);

    ToolCallResult result = handler.execute(exportCall("/workspace/report.pdf"), session);

    assertThat(result.content()).isInstanceOf(List.class);
    @SuppressWarnings("unchecked")
    List<Object> contentList = (List<Object>) result.content();
    assertThat(contentList).hasSize(2);
    assertThat(contentList.get(1)).isSameAs(mockDocument);

    ArgumentCaptor<DocumentCreationRequest> captor =
        ArgumentCaptor.forClass(DocumentCreationRequest.class);
    verify(mockDocumentFactory).create(captor.capture());
    assertThat(captor.getValue().contentType()).isEqualTo("application/pdf");
    assertThat(captor.getValue().fileName()).isEqualTo("report.pdf");
  }

  // --- Extraction integration (the safety net) ---

  @Test
  void execute_extractionIntegration_documentWalkerFindsExportedDocument() {
    byte[] content = "report content".getBytes(StandardCharsets.UTF_8);
    session.fs().write("/workspace/out.txt", content);

    ExportDocumentToolHandler handler =
        new ExportDocumentToolHandler(realDocumentFactory, MAX_BYTES);

    ToolCallResult result = handler.execute(exportCall("/workspace/out.txt"), session);

    // Construct a real extractor with an empty registry (no gateway handlers → walker fallback)
    ToolCallResultDocumentExtractor extractor =
        new ToolCallResultDocumentExtractor(new GatewayToolHandlerRegistryImpl(List.of()));

    List<ToolCallResultDocumentExtractor.ToolCallDocuments> extracted =
        extractor.extractDocuments(List.of(result));

    assertThat(extracted).hasSize(1);
    assertThat(extracted.getFirst().documents()).hasSize(1);

    // The extracted document should be a real Document
    Document extractedDoc = extracted.getFirst().documents().getFirst();
    assertThat(extractedDoc).isNotNull();
    assertThat(extractedDoc.reference()).isNotNull();
  }

  // --- Size guard ---

  @Test
  void execute_fileTooLarge_shouldReturnErrorAndNotCreateDocument() {
    // maxDocumentBytes = 10; file is 100 bytes
    ExportDocumentToolHandler handler = new ExportDocumentToolHandler(mockDocumentFactory, 10);
    byte[] bigContent = "X".repeat(100).getBytes(StandardCharsets.UTF_8);
    session.fs().write("/workspace/big.bin", bigContent);

    ToolCallResult result = handler.execute(exportCall("/workspace/big.bin"), session);

    assertThat(result.content()).asString().contains("Error:");
    assertThat(result.content()).asString().contains("too large");
    verify(mockDocumentFactory, never()).create(any());
  }

  // --- Missing/blank path ---

  @Test
  void execute_missingPath_shouldReturnError() {
    ExportDocumentToolHandler handler =
        new ExportDocumentToolHandler(mockDocumentFactory, MAX_BYTES);
    ToolCall call =
        ToolCall.builder()
            .id("e-nop")
            .name(InternalToolNames.EXPORT_DOCUMENT)
            .arguments(Map.of())
            .build();

    ToolCallResult result = handler.execute(call, session);

    assertThat(result.content()).asString().contains("Error:");
    verify(mockDocumentFactory, never()).create(any());
  }

  @Test
  void execute_blankPath_shouldReturnError() {
    ExportDocumentToolHandler handler =
        new ExportDocumentToolHandler(mockDocumentFactory, MAX_BYTES);
    ToolCall call =
        ToolCall.builder()
            .id("e-blank")
            .name(InternalToolNames.EXPORT_DOCUMENT)
            .arguments(Map.of("path", "   "))
            .build();

    ToolCallResult result = handler.execute(call, session);

    assertThat(result.content()).asString().contains("Error:");
    verify(mockDocumentFactory, never()).create(any());
  }

  // --- Missing file ---

  @Test
  void execute_missingFile_shouldReturnError() {
    ExportDocumentToolHandler handler =
        new ExportDocumentToolHandler(mockDocumentFactory, MAX_BYTES);

    ToolCallResult result = handler.execute(exportCall("/workspace/nonexistent.txt"), session);

    assertThat(result.content()).asString().contains("Error:");
    verify(mockDocumentFactory, never()).create(any());
  }

  // --- executedBy tag ---

  @Test
  void execute_resultAlwaysTaggedExecutedBySandbox() {
    byte[] content = "x".getBytes(StandardCharsets.UTF_8);
    session.fs().write("/workspace/f.txt", content);

    when(mockDocumentFactory.create(any())).thenReturn(mockDocument);

    ExportDocumentToolHandler handler =
        new ExportDocumentToolHandler(mockDocumentFactory, MAX_BYTES);

    ToolCallResult result = handler.execute(exportCall("/workspace/f.txt"), session);

    assertThat(result.properties())
        .containsEntry(
            InternalToolExecutor.PROPERTY_EXECUTED_BY, InternalToolExecutor.EXECUTED_BY_SANDBOX);
  }

  // --- Tool definition ---

  @Test
  void definition_shouldHaveCorrectNameAndSchema() {
    ExportDocumentToolHandler handler =
        new ExportDocumentToolHandler(mockDocumentFactory, MAX_BYTES);

    assertThat(handler.name()).isEqualTo(InternalToolNames.EXPORT_DOCUMENT);
    assertThat(handler.definition().name()).isEqualTo(InternalToolNames.EXPORT_DOCUMENT);
    assertThat(handler.definition().description()).isNotBlank();

    @SuppressWarnings("unchecked")
    Map<String, Object> props =
        (Map<String, Object>) handler.definition().inputSchema().get("properties");
    assertThat(props).containsKey("path");

    @SuppressWarnings("unchecked")
    List<String> required = (List<String>) handler.definition().inputSchema().get("required");
    assertThat(required).contains("path");
  }
}
