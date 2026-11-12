/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.testutil.TestDocuments;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolExecution;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeTools;
import io.camunda.connector.agenticai.testutil.TestDocumentHandles;
import io.camunda.connector.api.document.Document;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalToolCallExecutorTest {

  private static final AgentContext AGENT_CONTEXT =
      AgentContext.builder().state(AgentState.READY).build();

  private static final ToolCall RUNTIME_CALL =
      new ToolCall("runtime-1", "getWeather", Map.of("city", "Berlin")).withKind(ToolKind.RUNTIME);
  private static final ToolCall SECOND_RUNTIME_CALL =
      new ToolCall("runtime-2", "getDateTime", Map.of()).withKind(ToolKind.RUNTIME);
  private static final ToolCall REJECTED_CALL =
      new ToolCall("rejected-1", "unknownTool", Map.of())
          .withRejection("The tool 'unknownTool' does not exist.");
  private static final ToolCall PROCESS_CALL = new ToolCall("process-1", "someTask", Map.of());

  private RuntimeTools runtimeTools;
  private AgentExecutionContext executionContext;
  private LocalToolCallExecutor executor;

  @BeforeEach
  void setUp() {
    runtimeTools = mock(RuntimeTools.class);
    executionContext = mock(AgentExecutionContext.class);
    executor = new LocalToolCallExecutor(runtimeTools, TestDocumentHandles.objectHandleRenderer());
  }

  private static ToolCallBatch.Entry entryOf(List<ToolCallBatch.Entry> entries, ToolCall call) {
    return entries.stream()
        .filter(entry -> call.id().equals(entry.result().id()))
        .findFirst()
        .orElseThrow();
  }

  private static RuntimeToolExecution execution(ToolCall call, String text) {
    final var now = OffsetDateTime.parse("2026-10-05T10:00:00Z");
    return new RuntimeToolExecution(call, RuntimeToolResult.success(text), now, now);
  }

  @Test
  void executesTheRuntimeCallsOnce() {
    var runtimeCalls = List.of(RUNTIME_CALL, SECOND_RUNTIME_CALL);
    var executions =
        List.of(execution(RUNTIME_CALL, "Sunny"), execution(SECOND_RUNTIME_CALL, "15:00"));
    when(runtimeTools.execute(executionContext, AGENT_CONTEXT, runtimeCalls))
        .thenReturn(executions);

    var entries = executor.execute(executionContext, AGENT_CONTEXT, runtimeCalls);

    verify(runtimeTools, times(1)).execute(executionContext, AGENT_CONTEXT, runtimeCalls);
    verifyNoMoreInteractions(runtimeTools);
    assertThat(entries)
        .containsExactly(
            ToolCallBatch.Entry.from(executions.get(0).toolCallResult()),
            ToolCallBatch.Entry.from(executions.get(1).toolCallResult()));
  }

  @Test
  void aRejectedCallFailsBeforeAnyRuntimeToolRuns() {
    var calls = List.of(RUNTIME_CALL, REJECTED_CALL);

    assertThatThrownBy(() -> executor.execute(executionContext, AGENT_CONTEXT, calls))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("rejected-1")
        .hasMessageContaining("REJECTED");

    verifyNoInteractions(runtimeTools);
  }

  @Test
  void aRejectedCallOfARuntimeToolFailsBeforeAnyRuntimeToolRuns() {
    var calls = List.of(RUNTIME_CALL.withRejection("The call is not allowed."));

    assertThatThrownBy(() -> executor.execute(executionContext, AGENT_CONTEXT, calls))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("runtime-1");

    verifyNoInteractions(runtimeTools);
  }

  @Test
  void noCallsGiveNoEntriesAndNoRuntimeToolCall() {
    var entries = executor.execute(executionContext, AGENT_CONTEXT, List.of());

    assertThat(entries).isEmpty();
    verifyNoInteractions(runtimeTools);
  }

  @Test
  void aProcessCallFailsBeforeAnyRuntimeToolRuns() {
    var calls = List.of(RUNTIME_CALL, PROCESS_CALL);

    assertThatThrownBy(() -> executor.execute(executionContext, AGENT_CONTEXT, calls))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("process-1")
        .hasMessageContaining("someTask");

    verifyNoInteractions(runtimeTools);
  }

  private record Attachment(String name, Document file) {}

  @Test
  void aTrackedInstanceRegistersTheDocumentInATypedRecordOfAResult() {
    final var tracked =
        AgentContext.builder()
            .state(AgentState.READY)
            .features(List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS))
            .build();
    final var document = TestDocuments.createDocument("content", "report.txt");
    final var content = ObjectContent.objectContent(new Attachment("report", document));
    final var now = OffsetDateTime.parse("2026-10-05T10:00:00Z");
    final var calls = List.of(RUNTIME_CALL);
    when(runtimeTools.execute(executionContext, tracked, calls))
        .thenReturn(
            List.of(
                new RuntimeToolExecution(
                    RUNTIME_CALL, RuntimeToolResult.success(List.of(content)), now, now)));

    final var entries = executor.execute(executionContext, tracked, calls);

    assertThat(entries)
        .containsExactly(
            new ToolCallBatch.Entry(
                ToolCallResultContent.builder()
                    .id("runtime-1")
                    .name("getWeather")
                    .content(List.of(content))
                    .completedAt(now)
                    .build(),
                List.of(document)));
  }

  @Test
  void anUntrackedInstanceFindsNoDocumentInATypedRecordOfAResult() {
    final var document = TestDocuments.createDocument("content", "report.txt");
    final var content = ObjectContent.objectContent(new Attachment("report", document));
    final var now = OffsetDateTime.parse("2026-10-05T10:00:00Z");
    final var calls = List.of(RUNTIME_CALL);
    when(runtimeTools.execute(executionContext, AGENT_CONTEXT, calls))
        .thenReturn(
            List.of(
                new RuntimeToolExecution(
                    RUNTIME_CALL, RuntimeToolResult.success(List.of(content)), now, now)));

    final var entries = executor.execute(executionContext, AGENT_CONTEXT, calls);

    assertThat(entries)
        .containsExactly(
            new ToolCallBatch.Entry(
                ToolCallResultContent.builder()
                    .id("runtime-1")
                    .name("getWeather")
                    .content(List.of(content))
                    .completedAt(now)
                    .build(),
                List.of()));
  }
}
