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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.conversation.AgentConversation;
import io.camunda.connector.agenticai.aiagent.conversation.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.document.DocumentParameterResolver;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.connector.runtime.core.intrinsic.DisabledIntrinsicFunctionExecutor;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentResponseHandlerDocumentsTest {

  private final Document report = createDocument("report", "report.txt");
  private final Document summary = createDocument("summary", "summary.txt");
  private final AgentDocumentId reportId = AgentDocumentId.of(report);
  private final AgentDocumentId summaryId = AgentDocumentId.of(summary);
  private final AgentDocuments agentDocuments =
      AgentDocuments.empty()
          .register(
              List.of(
                  AgentDocument.of(report, AgentDocumentOrigin.toolCall("search", "call-1")),
                  AgentDocument.of(
                      summary, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID))));

  private final ObjectMapper objectMapper = new ObjectMapper();
  private GatewayToolHandlerRegistry gatewayToolHandlers;
  private AgentResponseHandler responseHandler;

  @BeforeEach
  void setUp() {
    gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
    when(gatewayToolHandlers.transformToolCalls(any(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    responseHandler =
        new AgentResponseHandlerImpl(
            objectMapper,
            gatewayToolHandlers,
            new DocumentParameterResolver(TestObjectMapperSupplier.INSTANCE));
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
        .isEqualTo(new ReturnedDocument(referenceOf(summaryId), "Short version"));
    assertThat(response.responseDocuments().get("report"))
        .isEqualTo(new ReturnedDocument(referenceOf(reportId), null));
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
        .isEqualTo(referenceOf(reportId));
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
            .documents(agentDocuments)
            .responseDocuments(List.of(new ResponseDocumentSelection("report", reportId, null)))
            .build();
    final var conversation =
        AgentConversation.rehydrate(
                config, context, TurnReconstructor.reconstruct(List.of()), null, List.of())
            .ingest(assistantMessage("done"), AgentMetrics.empty());

    assertThat(createResponse(conversation).responseDocuments()).isEmpty();
  }

  @Test
  void aResponseDocumentThatIsNotInTheAgentDocumentsIsAnInvariantViolation() {
    final var conversation =
        conversation(
            responseDocuments(
                new ResponseDocumentSelection("gone", AgentDocumentId.of("gone"), null),
                new ResponseDocumentSelection("report", reportId, null)),
            assistantMessage("done"));

    assertThatThrownBy(() -> createResponse(conversation))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("'gone'");
  }

  @Test
  void keepsTheCustomPropertiesOfAResponseDocumentAsDataWithTheMapperOfTheRuntime() {
    // the mapper of the runtime reads documents and intrinsic functions in any object it converts
    final var runtimeMapper =
        new ObjectMapper()
            .registerModule(
                new JacksonModuleDocumentDeserializer(
                    mock(DocumentFactory.class), new DisabledIntrinsicFunctionExecutor()));
    final var handler =
        new AgentResponseHandlerImpl(
            runtimeMapper, gatewayToolHandlers, new DocumentParameterResolver(runtimeMapper));
    final var withFunctionLikeProperty =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
            .create(
                DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                    .contentType("text/plain")
                    .fileName("data.txt")
                    .customProperties(
                        Map.of(
                            "transform",
                            Map.of("camunda.function.type", "base64", "params", List.of("x"))))
                    .build());
    final var agentDocument =
        AgentDocument.of(
            withFunctionLikeProperty, AgentDocumentOrigin.toolCall("search", "call-1"));
    final var config =
        new AgentConfiguration(TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null);
    final var context =
        AgentContext.builder()
            .state(AgentState.READY)
            .features(AgentFeatures.ofNewInstance())
            .documents(AgentDocuments.empty().register(List.of(agentDocument)))
            .responseDocuments(
                List.of(new ResponseDocumentSelection("data", agentDocument.id(), null)))
            .build();
    final var conversation =
        AgentConversation.rehydrate(
                config, context, TurnReconstructor.reconstruct(List.of()), null, List.of())
            .ingest(assistantMessage("done"), AgentMetrics.empty());

    final var response = handler.createResponse(conversation);

    assertThat(response.responseDocuments())
        .containsExactly(
            Map.entry(
                "data", new ReturnedDocument(TestDocuments.referenceJson(agentDocument), null)));
  }

  /** The document reference in the JSON format it is persisted in. */
  private Map<String, Object> referenceOf(AgentDocumentId id) {
    return objectMapper.convertValue(
        objectMapper.valueToTree(agentDocuments.find(id).orElseThrow()).path("reference"),
        new TypeReference<>() {});
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
            .toolDefinitions(
                List.of(
                    ToolDefinition.builder()
                        .name("getWeather")
                        .inputSchema(Map.of("type", "object"))
                        .build()))
            .features(AgentFeatures.ofNewInstance())
            .documents(agentDocuments)
            .responseDocuments(responseDocuments)
            .build();
    return AgentConversation.rehydrate(
            config, context, TurnReconstructor.reconstruct(List.of()), null, List.of())
        .ingest(assistantMessage, AgentMetrics.empty());
  }
}
