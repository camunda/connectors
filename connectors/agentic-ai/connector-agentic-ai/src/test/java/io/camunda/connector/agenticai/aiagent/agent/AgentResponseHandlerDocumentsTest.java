/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_CHAT_MODEL;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_SYSTEM_PROMPT;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.localtool.ReturnDocumentTool;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.ReturnedDocument;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.document.SelectedDocument;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.request.AgentTaskResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentResponseHandlerDocumentsTest {

  private final Document report = createDocument("report", "report.txt");
  private final Document summary = createDocument("summary", "summary.txt");
  private final String reportId = DocumentHandle.idFor(report);
  private final String summaryId = DocumentHandle.idFor(summary);
  private final DocumentRegistry registry =
      DocumentRegistry.empty()
          .withDocuments(
              List.of(
                  new IngestedDocument(report, DocumentSource.toolCall("search", "call-1", null)),
                  new IngestedDocument(summary, DocumentSource.userMessage(null))));

  private AgentResponseHandler responseHandler;

  @BeforeEach
  void setUp() {
    final var gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
    when(gatewayToolHandlers.transformToolCalls(any(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    responseHandler = new AgentResponseHandlerImpl(new ObjectMapper(), gatewayToolHandlers);
  }

  @Test
  void returnsTheReferencesOfTheSelectedDocumentsByKeyInSelectionOrderAndClearsTheSelection() {
    final var response =
        responseHandler.createResponse(
            conversation(
                true,
                selection(
                    "summary", new SelectedDocument(summaryId, "Short version"),
                    "report", new SelectedDocument(reportId, null)),
                assistantMessage("done")));

    assertThat(response.documents().keySet()).containsExactly("summary", "report");
    assertThat(response.documents().get("summary"))
        .isEqualTo(
            new ReturnedDocument(
                registry.findById(summaryId).orElseThrow().reference(), "Short version"));
    assertThat(response.documents().get("report"))
        .isEqualTo(
            new ReturnedDocument(registry.findById(reportId).orElseThrow().reference(), null));
    assertThat(response.documents().get("summary").document())
        .containsEntry("camunda.document.type", "camunda")
        .containsKeys("storeId", "documentId", "contentHash");
    assertThat(response.context().returnDocuments()).isEmpty();
    assertThat(response.responseText()).isEqualTo("done");
  }

  @Test
  void returnsNoDocumentsWhenTheSelectionIsEmpty() {
    final var response =
        responseHandler.createResponse(conversation(true, selection(), assistantMessage("done")));

    assertThat(response.documents()).isEmpty();
  }

  @Test
  void returnsNoDocumentsWhenNotOptedInAndStillClearsTheSelection() {
    final var response =
        responseHandler.createResponse(
            conversation(
                false,
                selection("report", new SelectedDocument(reportId, null)),
                assistantMessage("done")));

    assertThat(response.documents()).isEmpty();
    assertThat(response.context().returnDocuments()).isEmpty();
  }

  @Test
  void keepsTheSelectionWhileToolCallsAreHandedToTheProcess() {
    final var toolCall = new ToolCall("call-2", "getWeather", Map.of("location", "MUC"));

    final var response =
        responseHandler.createResponse(
            conversation(
                true,
                selection("report", new SelectedDocument(reportId, null)),
                assistantMessage("checking", List.of(toolCall))));

    assertThat(response.toolCalls()).hasSize(1);
    assertThat(response.documents()).isEmpty();
    assertThat(response.context().returnDocuments()).containsOnlyKeys("report");
  }

  @Test
  void handsOnlyTheCallsThePendingLocalResultsDoNotAnswerToTheProcess() {
    final var localCall = new ToolCall("call-1", ReturnDocumentTool.NAME, Map.of());
    final var realCall = new ToolCall("call-2", "getWeather", Map.of("location", "MUC"));
    final var local =
        ToolCallResult.builder().id("call-1").name(ReturnDocumentTool.NAME).content("ok").build();

    final var response =
        responseHandler.createResponse(
            conversation(
                    true,
                    selection("report", new SelectedDocument(reportId, null)),
                    assistantMessage("both", List.of(localCall, realCall)))
                .withPendingLocalToolCallResults(List.of(local)));

    assertThat(response.toolCalls())
        .extracting(call -> call.metadata().id())
        .containsExactly("call-2");
    // the batch is not final, the selection stays and no documents are returned yet
    assertThat(response.documents()).isEmpty();
    assertThat(response.context().returnDocuments()).containsOnlyKeys("report");
    assertThat(response.context().pendingLocalToolCallResults()).containsExactly(local);
  }

  @Test
  void skipsSelectedDocumentsThatAreNotInTheRegistry() {
    final var response =
        responseHandler.createResponse(
            conversation(
                true,
                selection(
                    "gone", new SelectedDocument("gone", null),
                    "report", new SelectedDocument(reportId, null)),
                assistantMessage("done")));

    assertThat(response.documents()).containsOnlyKeys("report");
  }

  private static LinkedHashMap<String, SelectedDocument> selection(Object... keysAndValues) {
    final var selection = new LinkedHashMap<String, SelectedDocument>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      selection.put((String) keysAndValues[i], (SelectedDocument) keysAndValues[i + 1]);
    }
    return selection;
  }

  private AgentConversation conversation(
      boolean includeDocuments,
      LinkedHashMap<String, SelectedDocument> selection,
      AssistantMessage assistantMessage) {
    final var config =
        new AgentConfiguration(
            TEST_CHAT_MODEL,
            TEST_SYSTEM_PROMPT,
            null,
            null,
            null,
            null,
            new AgentTaskResponseConfiguration(null, null, includeDocuments));
    final var context =
        AgentContext.builder()
            .state(AgentState.READY)
            .documents(registry.entries())
            .returnDocuments(selection)
            .build();
    return AgentConversation.rehydrate(
            config, context, TurnReconstructor.reconstruct(List.of()), null, List.of())
        .ingest(assistantMessage, AgentMetrics.empty());
  }

  private static Document createDocument(String content, String fileName) {
    return new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
        .create(
            DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .fileName(fileName)
                .build());
  }
}
