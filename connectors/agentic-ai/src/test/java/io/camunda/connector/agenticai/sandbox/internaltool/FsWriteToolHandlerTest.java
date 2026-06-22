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

class FsWriteToolHandlerTest {

  private InMemorySandboxProvider provider;
  private SandboxSession session;

  @BeforeEach
  void setUp() {
    provider = new InMemorySandboxProvider();
    session = provider.create(SandboxSpec.defaults());
  }

  private static ToolCall writeCall(String path, String content) {
    return ToolCall.builder()
        .id("write-1")
        .name(InternalToolNames.FS_WRITE)
        .arguments(Map.of("path", path, "content", content))
        .build();
  }

  // --- Happy path ---

  @Test
  void execute_writeAndReadBack_contentMatches() {
    FsWriteToolHandler handler = new FsWriteToolHandler();
    String path = "/workspace/output.txt";
    String content = "Hello from the LLM!";

    ToolCallResult result =
        handler.execute(writeCall(path, content), session, InternalToolContext.empty());

    // Write should succeed
    assertThat(result.content()).asString().doesNotContain("Error:");
    // File should be readable with correct content
    byte[] stored = session.fs().read(path);
    assertThat(new String(stored, StandardCharsets.UTF_8)).isEqualTo(content);
  }

  @Test
  void execute_successResult_containsBytesAndPath() {
    FsWriteToolHandler handler = new FsWriteToolHandler();

    ToolCallResult result =
        handler.execute(writeCall("/workspace/f.txt", "abc"), session, InternalToolContext.empty());

    assertThat(result.content()).asString().contains("3"); // 3 bytes
    assertThat(result.content()).asString().contains("/workspace/f.txt");
    assertThat(result.id()).isEqualTo("write-1");
    assertThat(result.name()).isEqualTo(InternalToolNames.FS_WRITE);
  }

  @Test
  void execute_overwritesExistingFile() {
    FsWriteToolHandler handler = new FsWriteToolHandler();
    session.fs().write("/workspace/f.txt", "old content".getBytes(StandardCharsets.UTF_8));

    handler.execute(
        writeCall("/workspace/f.txt", "new content"), session, InternalToolContext.empty());

    byte[] stored = session.fs().read("/workspace/f.txt");
    assertThat(new String(stored, StandardCharsets.UTF_8)).isEqualTo("new content");
  }

  // --- Missing arguments ---

  @Test
  void execute_missingPath_shouldReturnError() {
    FsWriteToolHandler handler = new FsWriteToolHandler();
    ToolCall call =
        ToolCall.builder()
            .id("w-nop")
            .name(InternalToolNames.FS_WRITE)
            .arguments(Map.of("content", "data"))
            .build();

    ToolCallResult result = handler.execute(call, session, InternalToolContext.empty());

    assertThat(result.content()).asString().contains("Error:");
  }

  @Test
  void execute_missingContent_shouldReturnError() {
    FsWriteToolHandler handler = new FsWriteToolHandler();
    ToolCall call =
        ToolCall.builder()
            .id("w-noc")
            .name(InternalToolNames.FS_WRITE)
            .arguments(Map.of("path", "/workspace/f.txt"))
            .build();

    ToolCallResult result = handler.execute(call, session, InternalToolContext.empty());

    assertThat(result.content()).asString().contains("Error:");
  }

  // --- executedBy tag ---

  @Test
  void execute_resultAlwaysTaggedExecutedBySandbox() {
    FsWriteToolHandler handler = new FsWriteToolHandler();

    ToolCallResult result =
        handler.execute(writeCall("/workspace/tag.txt", "x"), session, InternalToolContext.empty());

    assertThat(result.properties())
        .containsEntry(
            InternalToolExecutor.PROPERTY_EXECUTED_BY, InternalToolExecutor.EXECUTED_BY_SANDBOX);
  }

  // --- Tool definition ---

  @Test
  void definition_shouldHaveCorrectName() {
    FsWriteToolHandler handler = new FsWriteToolHandler();

    assertThat(handler.definition().name()).isEqualTo(InternalToolNames.FS_WRITE);
    assertThat(handler.definition().description()).isNotBlank();
    @SuppressWarnings("unchecked")
    Map<String, Object> props =
        (Map<String, Object>) handler.definition().inputSchema().get("properties");
    assertThat(props).containsKey("path");
    assertThat(props).containsKey("content");
  }
}
