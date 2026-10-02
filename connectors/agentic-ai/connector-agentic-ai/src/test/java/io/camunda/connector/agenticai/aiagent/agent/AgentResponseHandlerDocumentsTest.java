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
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.request.AgentTaskResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
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
                  new IngestedDocument(report, "call-1", "search"),
                  new IngestedDocument(summary, null, null)));

  private AgentResponseHandler responseHandler;

  @BeforeEach
  void setUp() {
    final var gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
    when(gatewayToolHandlers.transformToolCalls(any(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    responseHandler = new AgentResponseHandlerImpl(new ObjectMapper(), gatewayToolHandlers);
  }

  @Test
  void returnsTheReferencesOfTheSelectedDocumentsInSelectionOrderAndClearsTheSelection() {
    final var response =
        responseHandler.createResponse(
            conversation(true, List.of(summaryId, reportId), assistantMessage("done")));

    assertThat(response.documents())
        .containsExactly(
            registry.findById(summaryId).orElseThrow().reference(),
            registry.findById(reportId).orElseThrow().reference());
    assertThat(response.documents().getFirst())
        .containsEntry("camunda.document.type", "camunda")
        .containsKeys("storeId", "documentId", "contentHash");
    assertThat(response.context().returnDocumentIds()).isEmpty();
    assertThat(response.responseText()).isEqualTo("done");
  }

  @Test
  void returnsNoDocumentsWhenTheSelectionIsEmpty() {
    final var response =
        responseHandler.createResponse(conversation(true, List.of(), assistantMessage("done")));

    assertThat(response.documents()).isEmpty();
  }

  @Test
  void returnsNoDocumentsWhenNotOptedInAndStillClearsTheSelection() {
    final var response =
        responseHandler.createResponse(
            conversation(false, List.of(reportId), assistantMessage("done")));

    assertThat(response.documents()).isEmpty();
    assertThat(response.context().returnDocumentIds()).isEmpty();
  }

  @Test
  void keepsTheSelectionWhileToolCallsAreHandedToTheProcess() {
    final var toolCall = new ToolCall("call-2", "getWeather", Map.of("location", "MUC"));

    final var response =
        responseHandler.createResponse(
            conversation(true, List.of(reportId), assistantMessage("checking", List.of(toolCall))));

    assertThat(response.toolCalls()).hasSize(1);
    assertThat(response.documents()).isEmpty();
    assertThat(response.context().returnDocumentIds()).containsExactly(reportId);
  }

  @Test
  void skipsSelectedIdsThatAreNotInTheRegistry() {
    final var response =
        responseHandler.createResponse(
            conversation(true, List.of("gone", reportId), assistantMessage("done")));

    assertThat(response.documents())
        .containsExactly(registry.findById(reportId).orElseThrow().reference());
  }

  private AgentConversation conversation(
      boolean includeDocuments, List<String> selection, AssistantMessage assistantMessage) {
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
            .returnDocumentIds(selection)
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
