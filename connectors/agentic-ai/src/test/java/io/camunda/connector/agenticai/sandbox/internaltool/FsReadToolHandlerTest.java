/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.internaltool;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.sandbox.provider.fake.InMemorySandboxProvider;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FsReadToolHandlerTest {

  private InMemorySandboxProvider provider;
  private SandboxSession session;

  @BeforeEach
  void setUp() {
    provider = new InMemorySandboxProvider();
    session = provider.create(SandboxSpec.defaults());
  }

  private static ToolCall readCall(String path) {
    return ToolCall.builder()
        .id("read-1")
        .name(InternalToolNames.FS_READ)
        .arguments(Map.of("path", path))
        .build();
  }

  // --- Happy path ---

  @Test
  void execute_textFile_shouldReturnContent() {
    session.fs().write("/workspace/hello.txt", "Hello, world!".getBytes(StandardCharsets.UTF_8));
    FsReadToolHandler handler = new FsReadToolHandler();

    ToolCallResult result = handler.execute(readCall("/workspace/hello.txt"), session);

    assertThat(result.content()).isEqualTo("Hello, world!");
    assertThat(result.id()).isEqualTo("read-1");
    assertThat(result.name()).isEqualTo(InternalToolNames.FS_READ);
  }

  @Test
  void execute_multilineTextFile_shouldReturnFullContent() {
    String text = "line1\nline2\nline3\n";
    session.fs().write("/workspace/file.txt", text.getBytes(StandardCharsets.UTF_8));
    FsReadToolHandler handler = new FsReadToolHandler();

    ToolCallResult result = handler.execute(readCall("/workspace/file.txt"), session);

    assertThat(result.content()).isEqualTo(text);
  }

  // --- Binary file marker ---

  @Test
  void execute_binaryFile_shouldReturnMarkerNotRawBytes() {
    // PDF-like binary content with NUL byte
    byte[] binaryBytes = new byte[] {0x25, 0x50, 0x44, 0x46, 0x00, 0x01, 0x02};
    session.fs().write("/workspace/report.pdf", binaryBytes);
    FsReadToolHandler handler = new FsReadToolHandler();

    ToolCallResult result = handler.execute(readCall("/workspace/report.pdf"), session);

    String content = (String) result.content();
    assertThat(content).contains("binary");
    assertThat(content).contains("export_document");
    // Raw bytes must NOT be returned to the LLM
    assertThat(result.content()).isNotEqualTo(new String(binaryBytes, StandardCharsets.UTF_8));
  }

  @Test
  void execute_binaryFile_markerContainsSizeAndContentType() {
    byte[] binaryBytes = new byte[] {0x25, 0x50, 0x44, 0x46, 0x00};
    session.fs().write("/workspace/doc.pdf", binaryBytes);
    FsReadToolHandler handler = new FsReadToolHandler();

    ToolCallResult result = handler.execute(readCall("/workspace/doc.pdf"), session);

    String content = (String) result.content();
    assertThat(content).contains("binary");
    assertThat(content).contains("export_document");
  }

  // --- Oversized file ---

  @Test
  void execute_oversizedTextFile_shouldReturnOversizedMarker() {
    // maxReadBytes = 10; file is 100 bytes
    FsReadToolHandler handler = new FsReadToolHandler(10);
    byte[] bigContent = "B".repeat(100).getBytes(StandardCharsets.UTF_8);
    session.fs().write("/workspace/big.txt", bigContent);

    ToolCallResult result = handler.execute(readCall("/workspace/big.txt"), session);

    String content = (String) result.content();
    assertThat(content).contains("too large");
    assertThat(content).doesNotContain("B".repeat(100));
  }

  // --- Missing file ---

  @Test
  void execute_missingFile_shouldReturnErrorContent_notThrowException() {
    FsReadToolHandler handler = new FsReadToolHandler();

    ToolCallResult result = handler.execute(readCall("/workspace/nonexistent.txt"), session);

    assertThat(result.content()).asString().contains("Error:");
    assertThat(result.content()).asString().containsIgnoringCase("not found");
    assertThat(result.id()).isEqualTo("read-1");
  }

  // --- Missing argument ---

  @Test
  void execute_missingPath_shouldReturnError() {
    FsReadToolHandler handler = new FsReadToolHandler();
    ToolCall call =
        ToolCall.builder().id("r-nop").name(InternalToolNames.FS_READ).arguments(Map.of()).build();

    ToolCallResult result = handler.execute(call, session);

    assertThat(result.content()).asString().contains("Error:");
  }

  // --- executedBy tag ---

  @Test
  void execute_resultAlwaysTaggedExecutedBySandbox() {
    session.fs().write("/workspace/f.txt", "x".getBytes(StandardCharsets.UTF_8));
    FsReadToolHandler handler = new FsReadToolHandler();

    ToolCallResult result = handler.execute(readCall("/workspace/f.txt"), session);

    assertThat(result.properties())
        .containsEntry(
            InternalToolExecutor.PROPERTY_EXECUTED_BY, InternalToolExecutor.EXECUTED_BY_SANDBOX);
  }

  // --- Tool definition ---

  @Test
  void definition_shouldHaveCorrectName() {
    FsReadToolHandler handler = new FsReadToolHandler();

    assertThat(handler.definition().name()).isEqualTo(InternalToolNames.FS_READ);
    assertThat(handler.definition().description()).contains("export_document");
    @SuppressWarnings("unchecked")
    Map<String, Object> props =
        (Map<String, Object>) handler.definition().inputSchema().get("properties");
    assertThat(props).containsKey("path");
  }
}
