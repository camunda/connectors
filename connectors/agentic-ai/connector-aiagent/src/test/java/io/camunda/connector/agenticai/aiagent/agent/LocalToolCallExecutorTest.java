/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
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
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallEffect;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolExecution;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeTools;
import io.camunda.connector.api.document.Document;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalToolCallExecutorTest {

  private static final AgentContext AGENT_CONTEXT =
      AgentContext.builder().state(AgentState.READY).build();

  private static final Document KNOWN_DOCUMENT = createDocument("known", "known.txt");
  private static final AgentDocumentId KNOWN_ID = AgentDocumentId.of(KNOWN_DOCUMENT);
  private static final AgentContext AGENT_CONTEXT_WITH_DOCUMENT =
      AGENT_CONTEXT
          .withFeatures(List.of(AgentFeatures.DOCUMENTS))
          .withDocuments(
              AgentDocuments.empty().register(KNOWN_DOCUMENT, DocumentOrigin.userMessage()));

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
    executor = new LocalToolCallExecutor(runtimeTools);
  }

  private static ToolCallBatch.Entry entryOf(List<ToolCallBatch.Entry> entries, ToolCall call) {
    return entries.stream()
        .filter(entry -> call.id().equals(entry.result().id()))
        .findFirst()
        .orElseThrow();
  }

  private static RuntimeToolExecution runtimeExecution(RuntimeToolResult result) {
    final var now = OffsetDateTime.parse("2026-10-05T10:00:00Z");
    return new RuntimeToolExecution(RUNTIME_CALL, result, now, now);
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
    when(runtimeTools.execute(executionContext, AGENT_CONTEXT, Set.of(), runtimeCalls))
        .thenReturn(executions);

    var entries = executor.execute(executionContext, AGENT_CONTEXT, Set.of(), runtimeCalls);

    verify(runtimeTools, times(1)).execute(executionContext, AGENT_CONTEXT, Set.of(), runtimeCalls);
    verifyNoMoreInteractions(runtimeTools);
    assertThat(entries)
        .containsExactly(
            ToolCallBatch.Entry.from(executions.get(0).toolCallResult()),
            ToolCallBatch.Entry.from(executions.get(1).toolCallResult()));
  }

  @Test
  void passesTheIdsOfTheDocumentsWithContentOnAndKeepsTheEffectsOfAResult() {
    var withContent = Set.of(KNOWN_ID);
    var execution =
        runtimeExecution(RuntimeToolResult.success("loaded").withDocumentContent(KNOWN_ID));
    when(runtimeTools.execute(
            executionContext, AGENT_CONTEXT_WITH_DOCUMENT, withContent, List.of(RUNTIME_CALL)))
        .thenReturn(List.of(execution));

    var entries =
        executor.execute(
            executionContext, AGENT_CONTEXT_WITH_DOCUMENT, withContent, List.of(RUNTIME_CALL));

    assertThat(entries)
        .containsExactly(
            ToolCallBatch.Entry.from(
                execution.toolCallResult(),
                List.of(new ToolCallEffect.AddDocumentContent(KNOWN_ID))));
  }

  @Test
  void anErrorResultHasNoEffects() {
    when(runtimeTools.execute(executionContext, AGENT_CONTEXT, Set.of(), List.of(RUNTIME_CALL)))
        .thenReturn(List.of(runtimeExecution(RuntimeToolResult.error("no"))));

    var entries =
        executor.execute(executionContext, AGENT_CONTEXT, Set.of(), List.of(RUNTIME_CALL));

    assertThat(entries).singleElement().satisfies(entry -> assertThat(entry.effects()).isEmpty());
  }

  @Test
  void aResultMayNameAndReferenceTheDocumentsItReturns() {
    var returned = createDocument("returned", "returned.txt");
    var id = AgentDocumentId.of(returned);
    var execution =
        runtimeExecution(
            RuntimeToolResult.success(
                    List.of(DocumentContent.documentContent(returned), AgentDocumentContent.of(id)))
                .withDocumentContent(returned));
    when(runtimeTools.execute(executionContext, AGENT_CONTEXT, Set.of(), List.of(RUNTIME_CALL)))
        .thenReturn(List.of(execution));

    var entries =
        executor.execute(executionContext, AGENT_CONTEXT, Set.of(), List.of(RUNTIME_CALL));

    assertThat(entries)
        .singleElement()
        .satisfies(entry ->
            assertThat(entry.effects()).containsExactly(new ToolCallEffect.AddDocumentContent(id)));
  }

  @Test
  void anEffectThatNamesAnUnknownDocumentIsADefectOfTheTool() {
    when(runtimeTools.execute(executionContext, AGENT_CONTEXT, Set.of(), List.of(RUNTIME_CALL)))
        .thenReturn(
            List.of(
                runtimeExecution(RuntimeToolResult.success("a").withDocumentContent(KNOWN_ID))));

    assertThatThrownBy(() ->
            executor.execute(executionContext, AGENT_CONTEXT, Set.of(), List.of(RUNTIME_CALL)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The runtime tool 'getWeather' refers to the document '%s', which the agent does not know and the result does not return"
                .formatted(KNOWN_ID));
  }

  @Test
  void keepsTheSelectionOfAKnownDocumentForTheResponse() {
    var selection = new ResponseDocumentSelection("report", KNOWN_ID, "The report");
    var execution =
        runtimeExecution(
            RuntimeToolResult.success("ok").withResponseDocument("report", KNOWN_ID, "The report"));
    when(runtimeTools.execute(
            executionContext, AGENT_CONTEXT_WITH_DOCUMENT, Set.of(), List.of(RUNTIME_CALL)))
        .thenReturn(List.of(execution));

    var entries =
        executor.execute(
            executionContext, AGENT_CONTEXT_WITH_DOCUMENT, Set.of(), List.of(RUNTIME_CALL));

    assertThat(entries)
        .singleElement()
        .satisfies(entry ->
            assertThat(entry.effects())
                .containsExactly(new ToolCallEffect.SelectResponseDocument(selection)));
  }

  @Test
  void aResultMayReturnADocumentAndSelectItForTheResponse() {
    var returned = createDocument("returned", "returned.txt");
    var id = AgentDocumentId.of(returned);
    var execution =
        runtimeExecution(
            RuntimeToolResult.success(List.of(DocumentContent.documentContent(returned)))
                .withResponseDocument("created", id, null));
    when(runtimeTools.execute(executionContext, AGENT_CONTEXT, Set.of(), List.of(RUNTIME_CALL)))
        .thenReturn(List.of(execution));

    var entries =
        executor.execute(executionContext, AGENT_CONTEXT, Set.of(), List.of(RUNTIME_CALL));

    assertThat(entries)
        .singleElement()
        .satisfies(entry ->
            assertThat(entry.effects())
                .containsExactly(
                    new ToolCallEffect.SelectResponseDocument(
                        new ResponseDocumentSelection("created", id, null))));
  }

  @Test
  void aSelectionOfAnUnknownDocumentIsADefectOfTheTool() {
    when(runtimeTools.execute(executionContext, AGENT_CONTEXT, Set.of(), List.of(RUNTIME_CALL)))
        .thenReturn(
            List.of(
                runtimeExecution(
                    RuntimeToolResult.success("a").withResponseDocument("k", KNOWN_ID, null))));

    assertThatThrownBy(() ->
            executor.execute(executionContext, AGENT_CONTEXT, Set.of(), List.of(RUNTIME_CALL)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The runtime tool 'getWeather' refers to the document '%s', which the agent does not know and the result does not return"
                .formatted(KNOWN_ID));
  }

  @Test
  void aReferenceToAnUnknownDocumentIsADefectOfTheTool() {
    when(runtimeTools.execute(executionContext, AGENT_CONTEXT, Set.of(), List.of(RUNTIME_CALL)))
        .thenReturn(
            List.of(
                runtimeExecution(
                    RuntimeToolResult.success(List.of(AgentDocumentContent.of(KNOWN_ID))))));

    assertThatThrownBy(() ->
            executor.execute(executionContext, AGENT_CONTEXT, Set.of(), List.of(RUNTIME_CALL)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The runtime tool 'getWeather' refers to the document '%s', which the agent does not know and the result does not return"
                .formatted(KNOWN_ID));
  }

  @Test
  void aBlockWithTheDeliveryContentIsADefectOfTheTool() {
    var block = AgentDocumentContent.of(KNOWN_ID).withDelivery(DocumentDelivery.CONTENT);
    when(runtimeTools.execute(
            executionContext, AGENT_CONTEXT_WITH_DOCUMENT, Set.of(), List.of(RUNTIME_CALL)))
        .thenReturn(List.of(runtimeExecution(RuntimeToolResult.success(List.of(block)))));

    assertThatThrownBy(() ->
            executor.execute(
                executionContext, AGENT_CONTEXT_WITH_DOCUMENT, Set.of(), List.of(RUNTIME_CALL)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The runtime tool 'getWeather' returned the content of the document '%s'; the content of a document is added with an effect only"
                .formatted(KNOWN_ID));
  }

  @Test
  void aRejectedCallFailsBeforeAnyRuntimeToolRuns() {
    var calls = List.of(RUNTIME_CALL, REJECTED_CALL);

    assertThatThrownBy(() -> executor.execute(executionContext, AGENT_CONTEXT, Set.of(), calls))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("rejected-1")
        .hasMessageContaining("REJECTED");

    verifyNoInteractions(runtimeTools);
  }

  @Test
  void aRejectedCallOfARuntimeToolFailsBeforeAnyRuntimeToolRuns() {
    var calls = List.of(RUNTIME_CALL.withRejection("The call is not allowed."));

    assertThatThrownBy(() -> executor.execute(executionContext, AGENT_CONTEXT, Set.of(), calls))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("runtime-1");

    verifyNoInteractions(runtimeTools);
  }

  @Test
  void noCallsGiveNoEntriesAndNoRuntimeToolCall() {
    var entries = executor.execute(executionContext, AGENT_CONTEXT, Set.of(), List.of());

    assertThat(entries).isEmpty();
    verifyNoInteractions(runtimeTools);
  }

  @Test
  void aProcessCallFailsBeforeAnyRuntimeToolRuns() {
    var calls = List.of(RUNTIME_CALL, PROCESS_CALL);

    assertThatThrownBy(() -> executor.execute(executionContext, AGENT_CONTEXT, Set.of(), calls))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("process-1")
        .hasMessageContaining("someTask");

    verifyNoInteractions(runtimeTools);
  }
}
