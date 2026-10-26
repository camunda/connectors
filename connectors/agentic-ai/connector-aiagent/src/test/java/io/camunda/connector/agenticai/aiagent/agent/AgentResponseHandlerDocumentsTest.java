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
import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.conversation.AgentConversation;
import io.camunda.connector.agenticai.aiagent.conversation.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistries;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.ReturnedDocument;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.api.document.Document;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentResponseHandlerDocumentsTest {

  private final Document report = createDocument("report", "report.txt");
  private final Document summary = createDocument("summary", "summary.txt");
  private final DocumentId reportId = DocumentHandle.idFor(report);
  private final DocumentId summaryId = DocumentHandle.idFor(summary);
  private final DocumentRegistry registry =
      DocumentRegistries.withDocuments(
          DocumentRegistry.empty(),
          List.of(
              new IngestedDocument(report, DocumentSource.toolCall("search", "call-1")),
              new IngestedDocument(summary, DocumentSource.userMessage())));

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
  void returnsTheReferencesOfTheResponseDocumentsByKeyAndKeepsThemInTheContext() {
    final var response =
        createResponse(
            conversation(
                responseDocuments(
                    new ResponseDocumentSelection("summary", summaryId, "Short version"),
                    new ResponseDocumentSelection("report", reportId, null)),
                assistantMessage("done")));

    assertThat(response.responseDocuments()).containsOnlyKeys("summary", "report");
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
    assertThat(response.context().responseDocuments())
        .extracting(ResponseDocumentSelection::key)
        .containsExactly("summary", "report");
    assertThat(response.responseText()).isEqualTo("done");
  }

  @Test
  void aLaterFinalResponseCarriesTheEarlierDocumentsAndAReplacedKeyKeepsItsPosition() {
    final var first =
        createResponse(
            conversation(
                responseDocuments(
                    new ResponseDocumentSelection("summary", summaryId, "Short version"),
                    new ResponseDocumentSelection("report", reportId, null)),
                assistantMessage("first")));
    assertThat(first.responseDocuments()).containsOnlyKeys("summary", "report");

    // the next turn replaces the first key and adds a new one on top of the kept responseDocuments
    final var next =
        first
            .context()
            .withResponseDocument(new ResponseDocumentSelection("summary", reportId, "Replaced"))
            .withResponseDocument(new ResponseDocumentSelection("extra", summaryId, null))
            .responseDocuments();

    final var second = createResponse(conversation(next, assistantMessage("second")));

    assertThat(second.responseDocuments()).containsOnlyKeys("summary", "report", "extra");
    assertThat(second.responseDocuments().get("summary").description()).isEqualTo("Replaced");
    assertThat(second.responseDocuments().get("summary").document())
        .isEqualTo(registry.findById(reportId).orElseThrow().reference());
    assertThat(second.context().responseDocuments())
        .extracting(ResponseDocumentSelection::key)
        .containsExactly("summary", "report", "extra");
  }

  @Test
  void returnsNoDocumentsWhenTheResponseDocumentsAreEmpty() {
    final var response =
        createResponse(conversation(responseDocuments(), assistantMessage("done")));

    assertThat(response.responseDocuments()).isEmpty();
  }

  @Test
  void keepsTheResponseDocumentsWhileToolCallsAreHandedToTheProcess() {
    final var toolCall = new ToolCall("call-2", "getWeather", Map.of("location", "MUC"));

    final var response =
        createResponse(
            conversation(
                responseDocuments(new ResponseDocumentSelection("report", reportId, null)),
                assistantMessage("checking", List.of(toolCall))));

    assertThat(response.toolCalls()).hasSize(1);
    assertThat(response.responseDocuments()).isEmpty();
    assertThat(response.context().responseDocuments())
        .extracting(ResponseDocumentSelection::key)
        .containsExactly("report");
  }

  @Test
  void keepsTheResponseDocumentsOfAMixedBatchAndHandsOnlyTheProcessCallToTheProcess() {
    final var runtimeCall =
        new ToolCall("call-1", "acme_lookup", Map.of()).withKind(ToolKind.RUNTIME);
    final var processCall = new ToolCall("call-2", "getWeather", Map.of("location", "MUC"));

    final var response =
        createResponse(
            conversation(
                responseDocuments(new ResponseDocumentSelection("report", reportId, null)),
                assistantMessage("both", List.of(runtimeCall, processCall))));

    assertThat(response.toolCalls())
        .extracting(call -> call.metadata().id())
        .containsExactly("call-2");
    // the batch is not final: no documents are returned yet, and the selections stay
    assertThat(response.responseDocuments()).isEmpty();
    assertThat(response.context().responseDocuments())
        .extracting(ResponseDocumentSelection::key)
        .containsExactly("report");
  }

  @Test
  void returnsNoDocumentsForAnAgentInstanceWithoutTheDocumentsFeature() {
    final var config =
        new AgentConfiguration(TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null);
    final var context =
        AgentContext.builder()
            .state(AgentState.READY)
            .documents(registry.entries())
            .responseDocuments(List.of(new ResponseDocumentSelection("report", reportId, null)))
            .build();
    final var conversation =
        AgentConversation.rehydrate(
                config, context, TurnReconstructor.reconstruct(List.of()), null, List.of())
            .ingest(assistantMessage("done"), AgentMetrics.empty());

    assertThat(createResponse(conversation).responseDocuments()).isEmpty();
  }

  @Test
  void skipsResponseDocumentsThatAreNotInTheRegistry() {
    final var response =
        createResponse(
            conversation(
                responseDocuments(
                    new ResponseDocumentSelection("gone", DocumentId.of("gone"), null),
                    new ResponseDocumentSelection("report", reportId, null)),
                assistantMessage("done")));

    assertThat(response.responseDocuments()).containsOnlyKeys("report");
  }

  private AgentResponse createResponse(AgentConversation conversation) {
    return responseHandler.createResponse(conversation);
  }

  private static List<ResponseDocumentSelection> responseDocuments(
      ResponseDocumentSelection... selections) {
    return List.of(selections);
  }

  private AgentConversation conversation(
      List<ResponseDocumentSelection> responseDocuments, AssistantMessage assistantMessage) {
    final var config =
        new AgentConfiguration(TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null);
    final var context =
        AgentContext.builder()
            .state(AgentState.READY)
            .features(AgentFeatures.ofNewInstance())
            .documents(registry.entries())
            .responseDocuments(responseDocuments)
            .build();
    return AgentConversation.rehydrate(
            config, context, TurnReconstructor.reconstruct(List.of()), null, List.of())
        .ingest(assistantMessage, AgentMetrics.empty());
  }
}
