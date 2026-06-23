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
import io.camunda.connector.agenticai.sandbox.spi.ExecResult;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSpec;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class BashToolHandlerTest {

  private SandboxSession session(
      Function<io.camunda.connector.agenticai.sandbox.spi.ExecRequest, ExecResult> execFn) {
    return new InMemorySandboxProvider(execFn).create(SandboxSpec.defaults());
  }

  private static ToolCall bashCall(String command) {
    return ToolCall.builder()
        .id("bash-1")
        .name(InternalToolNames.BASH)
        .arguments(Map.of("command", command))
        .build();
  }

  // --- Happy path ---

  @Test
  void execute_successfulCommand_shouldContainExitCodeAndStdout() {
    BashToolHandler handler = new BashToolHandler();
    SandboxSession session = session(req -> new ExecResult(0, "hello world\n", "", false));

    ToolCallResult result =
        handler.execute(bashCall("echo hello world"), session, InternalToolContext.empty());

    assertThat(result.content()).asString().contains("exit_code: 0");
    assertThat(result.content()).asString().contains("hello world");
  }

  @Test
  void execute_nonZeroExit_shouldReturnExitCodeAndStderr() {
    BashToolHandler handler = new BashToolHandler();
    SandboxSession session =
        session(req -> new ExecResult(1, "", "command not found: foo\n", false));

    ToolCallResult result = handler.execute(bashCall("foo"), session, InternalToolContext.empty());

    assertThat(result.content()).asString().contains("exit_code: 1");
    assertThat(result.content()).asString().contains("command not found: foo");
    // Non-zero exit is NOT an exception -- it is returned as content
    assertThat(result.content()).asString().doesNotContain("Error:");
  }

  @Test
  void execute_nonZeroExit_preservesToolCallId() {
    BashToolHandler handler = new BashToolHandler();
    SandboxSession session = session(req -> new ExecResult(2, "", "permission denied", false));

    ToolCallResult result =
        handler.execute(bashCall("restricted-cmd"), session, InternalToolContext.empty());

    assertThat(result.id()).isEqualTo("bash-1");
    assertThat(result.name()).isEqualTo(InternalToolNames.BASH);
  }

  // --- Truncation ---

  @Test
  void execute_outputExceedsCap_shouldTruncateWithMarker() {
    // Cap at 10 bytes; produce 100 bytes of output
    BashToolHandler handler = new BashToolHandler(60, 10);
    String longOutput = "A".repeat(100);
    SandboxSession session = session(req -> new ExecResult(0, longOutput, "", false));

    ToolCallResult result =
        handler.execute(bashCall("big-cmd"), session, InternalToolContext.empty());

    String content = (String) result.content();
    assertThat(content).contains("truncated");
    // The full 100-char repetition must not appear verbatim
    assertThat(content).doesNotContain("A".repeat(100));
  }

  @Test
  void execute_pretruncatedByProvider_shouldAddMarker() {
    BashToolHandler handler = new BashToolHandler();
    // Fake: SPI reports that it already truncated the output
    SandboxSession session = session(req -> new ExecResult(0, "partial output", "", true));

    ToolCallResult result =
        handler.execute(bashCall("large-cmd"), session, InternalToolContext.empty());

    assertThat(result.content()).asString().contains("truncated");
  }

  // --- Binary stdout ---

  @Test
  void execute_binaryStdout_nulByte_shouldReturnMarkerNotRawBytes() {
    BashToolHandler handler = new BashToolHandler();
    // Build a string with a NUL character at runtime -- triggers binary detection
    String binaryOutput = "before" + '\u0000' + "after";
    SandboxSession session = session(req -> new ExecResult(0, binaryOutput, "", false));

    ToolCallResult result =
        handler.execute(bashCall("cat binary.bin"), session, InternalToolContext.empty());

    assertThat(result.content()).asString().contains("binary");
    assertThat(result.content()).asString().contains("not shown");
    // The raw binary content must not reach the LLM
    assertThat(result.content()).asString().doesNotContain("beforeafter");
  }

  // --- Missing argument ---

  @Test
  void execute_missingCommand_shouldReturnError() {
    BashToolHandler handler = new BashToolHandler();
    SandboxSession session = session(req -> new ExecResult(0, "", "", false));

    ToolCall call =
        ToolCall.builder().id("no-cmd").name(InternalToolNames.BASH).arguments(Map.of()).build();
    ToolCallResult result = handler.execute(call, session, InternalToolContext.empty());

    assertThat(result.content()).asString().contains("Error:");
    assertThat(result.id()).isEqualTo("no-cmd");
  }

  // --- executedBy tag ---

  @Test
  void execute_resultAlwaysTaggedExecutedBySandbox() {
    BashToolHandler handler = new BashToolHandler();
    SandboxSession session = session(req -> new ExecResult(0, "ok", "", false));

    ToolCallResult result = handler.execute(bashCall("ls"), session, InternalToolContext.empty());

    assertThat(result.properties())
        .containsEntry(
            InternalToolExecutor.PROPERTY_EXECUTED_BY, InternalToolExecutor.EXECUTED_BY_SANDBOX);
  }

  // --- Tool definition ---

  @Test
  void definition_shouldHaveCorrectNameAndSchema() {
    BashToolHandler handler = new BashToolHandler();

    assertThat(handler.definition().name()).isEqualTo(InternalToolNames.BASH);
    assertThat(handler.definition().description()).isNotBlank();
    assertThat(handler.definition().inputSchema()).containsKey("properties");
    @SuppressWarnings("unchecked")
    Map<String, Object> props =
        (Map<String, Object>) handler.definition().inputSchema().get("properties");
    assertThat(props).containsKey("command");
    assertThat(handler.definition().inputSchema()).containsKey("required");
  }

  @Test
  void definition_shouldBeMarkedAsSandboxTool() {
    BashToolHandler handler = new BashToolHandler();

    assertThat(handler.definition().isSandboxTool()).isTrue();
    assertThat(handler.definition().metadata())
        .containsEntry(
            io.camunda.connector.agenticai.model.tool.ToolDefinition.METADATA_SANDBOX_TOOL, true);
  }
}
