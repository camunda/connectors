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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.document.ReturnDocumentTool;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.ReturnedDocument;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
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
import org.mockito.ArgumentCaptor;

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

  private GatewayToolHandlerRegistry gatewayToolHandlers;
  private AgentResponseHandler responseHandler;

  @BeforeEach
  void setUp() {
    gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
    when(gatewayToolHandlers.transformToolCalls(any(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    responseHandler = new AgentResponseHandlerImpl(new ObjectMapper(), gatewayToolHandlers);
  }

  @Test
  void returnsTheReferencesOfTheResponseDocumentsByKeyInOrderAndKeepsThemInTheContext() {
    final var response =
        responseHandler.createResponse(
            conversation(
                responseDocuments(
                    "summary", new ResponseDocumentSelection(summaryId, "Short version"),
                    "report", new ResponseDocumentSelection(reportId, null)),
                assistantMessage("done")));

    assertThat(response.responseDocuments().keySet()).containsExactly("summary", "report");
    assertThat(response.responseDocuments().get("summary"))
        .isEqualTo(
            new ReturnedDocument(
                registry.findById(summaryId).orElseThrow().reference(), "Short version"));
    assertThat(response.responseDocuments().get("report"))
        .isEqualTo(
            new ReturnedDocument(registry.findById(reportId).orElseThrow().reference(), null));
    assertThat(response.responseDocuments().get("summary").document())
        .containsEntry("camunda.document.type", "camunda")
        .containsKeys("storeId", "documentId", "contentHash");
    assertThat(response.context().responseDocuments()).containsOnlyKeys("summary", "report");
    assertThat(response.responseText()).isEqualTo("done");
  }

  @Test
  void aLaterFinalResponseCarriesTheEarlierDocumentsInOrderAndAReplacedKeyKeepsItsPosition() {
    final var first =
        responseHandler.createResponse(
            conversation(
                responseDocuments(
                    "summary", new ResponseDocumentSelection(summaryId, "Short version"),
                    "report", new ResponseDocumentSelection(reportId, null)),
                assistantMessage("first")));
    assertThat(first.responseDocuments().keySet()).containsExactly("summary", "report");

    // the next turn replaces the first key and adds a new one on top of the kept responseDocuments
    final var next = new LinkedHashMap<>(first.context().responseDocuments());
    next.put("summary", new ResponseDocumentSelection(reportId, "Replaced"));
    next.put("extra", new ResponseDocumentSelection(summaryId, null));

    final var second =
        responseHandler.createResponse(conversation(next, assistantMessage("second")));

    assertThat(second.responseDocuments().keySet()).containsExactly("summary", "report", "extra");
    assertThat(second.responseDocuments().get("summary").description()).isEqualTo("Replaced");
    assertThat(second.responseDocuments().get("summary").document())
        .isEqualTo(registry.findById(reportId).orElseThrow().reference());
    assertThat(second.context().responseDocuments().keySet())
        .containsExactly("summary", "report", "extra");
  }

  @Test
  void returnsNoDocumentsWhenTheResponseDocumentsAreEmpty() {
    final var response =
        responseHandler.createResponse(conversation(responseDocuments(), assistantMessage("done")));

    assertThat(response.responseDocuments()).isEmpty();
  }

  @Test
  void keepsTheResponseDocumentsWhileToolCallsAreHandedToTheProcess() {
    final var toolCall = new ToolCall("call-2", "getWeather", Map.of("location", "MUC"));

    final var response =
        responseHandler.createResponse(
            conversation(
                responseDocuments("report", new ResponseDocumentSelection(reportId, null)),
                assistantMessage("checking", List.of(toolCall))));

    assertThat(response.toolCalls()).hasSize(1);
    assertThat(response.responseDocuments()).isEmpty();
    assertThat(response.context().responseDocuments()).containsOnlyKeys("report");
  }

  @Test
  void handsOnlyTheCallsThePendingRuntimeResultsDoNotAnswerToTheProcess() {
    final var runtimeCall = new ToolCall("call-1", ReturnDocumentTool.NAME, Map.of());
    final var realCall = new ToolCall("call-2", "getWeather", Map.of("location", "MUC"));
    final var runtimeResult =
        ToolCallResult.builder().id("call-1").name(ReturnDocumentTool.NAME).content("ok").build();

    final var response =
        responseHandler.createResponse(
            conversation(
                    responseDocuments("report", new ResponseDocumentSelection(reportId, null)),
                    assistantMessage("both", List.of(runtimeCall, realCall)))
                .withPendingRuntimeToolCallResults(List.of(runtimeResult)));

    assertThat(response.toolCalls())
        .extracting(call -> call.metadata().id())
        .containsExactly("call-2");
    // the batch is not final, the responseDocuments stays and no documents are returned yet
    assertThat(response.responseDocuments()).isEmpty();
    assertThat(response.context().responseDocuments()).containsOnlyKeys("report");
    assertThat(response.context().pendingRuntimeToolCallResults()).containsExactly(runtimeResult);
  }

  @Test
  void skipsResponseDocumentsThatAreNotInTheRegistry() {
    final var response =
        responseHandler.createResponse(
            conversation(
                responseDocuments(
                    "gone", new ResponseDocumentSelection("gone", null),
                    "report", new ResponseDocumentSelection(reportId, null)),
                assistantMessage("done")));

    assertThat(response.responseDocuments()).containsOnlyKeys("report");
  }

  @Test
  void handsTheCallsOfGatewayToolsToTheGatewayHandlersWithoutResolvingTheirDocuments() {
    final var upload =
        new ToolDefinition(
            "upload",
            "Uploads a document",
            Map.of(
                "type",
                "object",
                "properties",
                Map.of("document", DocumentParameterSchema.documentSchema("The document"))));
    final var mcpTool = gatewayToolDefinition("MCP_files___read");
    final var a2aTool = gatewayToolDefinition("A2A_assistant");
    final var uploadCall = new ToolCall("call-1", "upload", Map.of("document", reportId));
    // arguments that look like the id of a known document and like its tag
    final var mcpCall =
        new ToolCall(
            "call-2",
            mcpTool.name(),
            Map.of("name", reportId, "nested", Map.of("text", docTag(summaryId))));
    final var a2aCall = new ToolCall("call-3", a2aTool.name(), Map.of("text", docTag(reportId)));

    responseHandler.createResponse(
        conversation(
            responseDocuments(),
            assistantMessage("go", List.of(uploadCall, mcpCall, a2aCall)),
            List.of(upload, mcpTool, a2aTool)));

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<List<ToolCall>> handed = ArgumentCaptor.forClass(List.class);
    verify(gatewayToolHandlers).transformToolCalls(any(), handed.capture());
    assertThat(handed.getValue()).hasSize(3);
    assertThat(handed.getValue().get(0).arguments())
        .containsEntry("document", registry.findById(reportId).orElseThrow().reference());
    assertThat(handed.getValue().get(1)).isEqualTo(mcpCall);
    assertThat(handed.getValue().get(2)).isEqualTo(a2aCall);
  }

  private static ToolDefinition gatewayToolDefinition(String name) {
    return new ToolDefinition(
        name,
        "A tool behind a gateway",
        Map.of("type", "object", "properties", Map.of("text", Map.of("type", "string"))));
  }

  private static String docTag(String documentId) {
    return "<doc id=\"%s\" />".formatted(documentId);
  }

  private static LinkedHashMap<String, ResponseDocumentSelection> responseDocuments(
      Object... keysAndValues) {
    final var responseDocuments = new LinkedHashMap<String, ResponseDocumentSelection>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      responseDocuments.put(
          (String) keysAndValues[i], (ResponseDocumentSelection) keysAndValues[i + 1]);
    }
    return responseDocuments;
  }

  private AgentConversation conversation(
      LinkedHashMap<String, ResponseDocumentSelection> responseDocuments,
      AssistantMessage assistantMessage) {
    return conversation(responseDocuments, assistantMessage, List.of());
  }

  private AgentConversation conversation(
      LinkedHashMap<String, ResponseDocumentSelection> responseDocuments,
      AssistantMessage assistantMessage,
      List<ToolDefinition> toolDefinitions) {
    final var config =
        new AgentConfiguration(TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null)
            .withFeatures(AgentContext.newInstanceFeatures())
            .withToolDefinitions(toolDefinitions);
    final var context =
        AgentContext.builder()
            .state(AgentState.READY)
            .features(AgentContext.newInstanceFeatures())
            .documents(registry.entries())
            .responseDocuments(responseDocuments)
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
