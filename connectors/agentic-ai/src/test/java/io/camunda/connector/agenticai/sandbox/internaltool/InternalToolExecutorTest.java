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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InternalToolExecutorTest {

  private InternalToolRegistry registry;
  private InternalToolExecutor executor;
  private InMemorySandboxProvider provider;
  private SandboxSession session;

  @BeforeEach
  void setUp() {
    provider = new InMemorySandboxProvider();
    session = provider.create(SandboxSpec.defaults());
    registry =
        new InternalToolRegistry(
            List.of(new BashToolHandler(), new FsReadToolHandler(), new FsWriteToolHandler()));
    executor = new InternalToolExecutor(registry);
  }

  // --- Dispatch ---

  @Test
  void execute_bashCall_shouldDispatchToSession() {
    provider = new InMemorySandboxProvider(req -> new ExecResult(0, "hello from bash", "", false));
    session = provider.create(SandboxSpec.defaults());

    ToolCall call =
        ToolCall.builder()
            .id("id1")
            .name(InternalToolNames.BASH)
            .arguments(Map.of("command", "echo hello"))
            .build();

    List<ToolCallResult> results = executor.execute(List.of(call), session);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).id()).isEqualTo("id1");
    assertThat(results.get(0).content()).asString().contains("hello from bash");
    assertThat(results.get(0).content()).asString().contains("exit_code: 0");
  }

  @Test
  void execute_unknownTool_shouldReturnErrorResult() {
    ToolCall call =
        ToolCall.builder().id("id-unknown").name("nonexistent_tool").arguments(Map.of()).build();

    List<ToolCallResult> results = executor.execute(List.of(call), session);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).id()).isEqualTo("id-unknown");
    assertThat(results.get(0).content()).asString().contains("Unknown internal tool");
    assertThat(results.get(0).content()).asString().contains("nonexistent_tool");
  }

  @Test
  void execute_allResultsTaggedWithExecutedBy() {
    session.fs().write("/workspace/hello.txt", "hello".getBytes(StandardCharsets.UTF_8));

    ToolCall bashCall =
        ToolCall.builder()
            .id("b1")
            .name(InternalToolNames.BASH)
            .arguments(Map.of("command", "echo hi"))
            .build();
    ToolCall readCall =
        ToolCall.builder()
            .id("r1")
            .name(InternalToolNames.FS_READ)
            .arguments(Map.of("path", "/workspace/hello.txt"))
            .build();

    List<ToolCallResult> results = executor.execute(List.of(bashCall, readCall), session);

    assertThat(results)
        .allSatisfy(
            r ->
                assertThat(r.properties())
                    .containsEntry(
                        InternalToolExecutor.PROPERTY_EXECUTED_BY,
                        InternalToolExecutor.EXECUTED_BY_SANDBOX));
  }

  @Test
  void execute_multipleCallsPreserveOrder() {
    provider = new InMemorySandboxProvider(req -> new ExecResult(0, req.command(), "", false));
    session = provider.create(SandboxSpec.defaults());

    ToolCall call1 =
        ToolCall.builder()
            .id("c1")
            .name(InternalToolNames.BASH)
            .arguments(Map.of("command", "cmd1"))
            .build();
    ToolCall call2 =
        ToolCall.builder()
            .id("c2")
            .name(InternalToolNames.BASH)
            .arguments(Map.of("command", "cmd2"))
            .build();

    List<ToolCallResult> results = executor.execute(List.of(call1, call2), session);

    assertThat(results).hasSize(2);
    assertThat(results.get(0).id()).isEqualTo("c1");
    assertThat(results.get(1).id()).isEqualTo("c2");
  }
}
