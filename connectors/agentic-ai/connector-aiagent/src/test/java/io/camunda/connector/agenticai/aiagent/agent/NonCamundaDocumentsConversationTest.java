/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TOOL_CALLS;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent.objectContent;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.api.tool.ToolCallEffect;
import io.camunda.connector.agenticai.aiagent.conversation.AgentInput;
import io.camunda.connector.agenticai.aiagent.conversation.PreviousConversation;
import io.camunda.connector.agenticai.aiagent.conversation.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.document.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.UnsupportedDocumentReferenceException;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.InlineDocument;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * An agent instance with the documents feature accepts Camunda documents only, wherever a document
 * enters the conversation; an agent instance without it keeps every document.
 */
class NonCamundaDocumentsConversationTest {

  private static final Document EXTERNAL =
      new ExternalDocument("https://example.com/report.pdf?token=secret", "report.pdf", u -> null);
  private static final Document INLINE =
      new InlineDocument("secret content", "report.txt", "text/plain");

  private static final String PREFIX = "Only Camunda documents can be passed to the AI Agent, but ";

  private static final AgentContext TRACKED =
      AgentContext.builder()
          .state(AgentState.READY)
          .features(List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS))
          .conversation(InProcessConversationContext.builder("conv").build())
          .build();
  private static final AgentContext UNTRACKED =
      AgentContext.builder()
          .state(AgentState.READY)
          .conversation(InProcessConversationContext.builder("conv").build())
          .build();
  private static final AgentConfiguration CONFIG =
      new AgentConfiguration(null, null, null, null, null, null, null);

  private AgentConversationTurnInputComposerImpl composer;

  @BeforeEach
  void setUp() {
    final var gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
    when(gatewayToolHandlers.transformToolCallResults(any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(gatewayToolHandlers.handlerForToolDefinition(any())).thenReturn(Optional.empty());
    composer =
        new AgentConversationTurnInputComposerImpl(
            gatewayToolHandlers,
            new ToolCallResultsInputBuilder(TestDocuments.documentContentDelivery()),
            TestDocuments.objectHandleRenderer());
  }

  private static PreviousConversation firstTurn() {
    return TurnReconstructor.reconstruct(List.of());
  }

  private static PreviousConversation afterToolCalls() {
    return TurnReconstructor.reconstruct(
        List.of(userMessage("hi"), assistantMessage("thinking", TOOL_CALLS)));
  }

  private static AgentInput prompt(String text, Document... documents) {
    return AgentInput.from(new UserPromptConfiguration(text, List.of(documents)), List.of());
  }

  private static AgentInput results(ToolCallResult... results) {
    return AgentInput.from(new UserPromptConfiguration("user input", List.of()), List.of(results));
  }

  private static ToolCallResult weather(Object content) {
    return ToolCallResult.builder().id("abcdef").name("getWeather").content(content).build();
  }

  private static ToolCallResult dateTime() {
    return ToolCallResult.builder().id("fedcba").name("getDateTime").content("15:00").build();
  }

  private TurnInput assemble(CompositionResult result) {
    assertThat(result).isInstanceOf(CompositionResult.Ready.class);
    return composer.assemble((CompositionResult.Ready) result);
  }

  private void assertFailsWith(Runnable composition, String message) {
    assertThatThrownBy(composition::run)
        .isExactlyInstanceOf(UnsupportedDocumentReferenceException.class)
        .hasMessage(message);
  }

  // ── the user prompt ───────────────────────────────────────────────────────

  @Test
  void anExternalDocumentOfTheUserPromptFailsTheJob() {
    assertFailsWith(
        () -> composer.resolve(CONFIG, TRACKED, firstTurn(), prompt("Read it", EXTERNAL)),
        PREFIX + "the user prompt contains an external document.");
  }

  @Test
  void anInlineDocumentOfTheUserPromptFailsTheJob() {
    assertFailsWith(
        () -> composer.resolve(CONFIG, TRACKED, firstTurn(), prompt("Read it", INLINE)),
        PREFIX + "the user prompt contains an inline document.");
  }

  @Test
  void aCamundaDocumentBeforeAnExternalDocumentOfTheUserPromptDoesNotPreventTheFailure() {
    final var camunda = TestDocuments.createDocument("data", "data.txt");

    assertFailsWith(
        () -> composer.resolve(CONFIG, TRACKED, firstTurn(), prompt("Read it", camunda, EXTERNAL)),
        PREFIX + "the user prompt contains an external document.");
  }

  @Test
  void withoutTheDocumentsFeatureTheDocumentsOfTheUserPromptStayDocuments() {
    final var result =
        composer.resolve(CONFIG, UNTRACKED, firstTurn(), prompt("Read them", EXTERNAL, INLINE));

    final var input = assemble(result);
    assertThat(input.documents()).isEmpty();
    assertThat(input.messages())
        .singleElement()
        .isInstanceOfSatisfying(
            UserMessage.class,
            message ->
                assertThat(message.content())
                    .containsExactly(
                        textContent("Read them"),
                        DocumentContent.documentContent(EXTERNAL),
                        DocumentContent.documentContent(INLINE)));
  }

  // ── the tool call results ─────────────────────────────────────────────────

  @Test
  void anExternalDocumentThatIsAWholeToolCallResultFailsTheJob() {
    assertFailsWith(
        () ->
            composer.resolve(
                CONFIG, TRACKED, afterToolCalls(), results(weather(EXTERNAL), dateTime())),
        PREFIX + "the result of the tool 'getWeather' contains an external document.");
  }

  @Test
  void anInlineDocumentNestedInAnObjectOfAToolCallResultFailsTheJob() {
    final var nested = Map.of("report", Map.of("files", List.of(Map.of("file", INLINE))));

    assertFailsWith(
        () ->
            composer.resolve(
                CONFIG, TRACKED, afterToolCalls(), results(weather(nested), dateTime())),
        PREFIX + "the result of the tool 'getWeather' contains an inline document.");
  }

  @Test
  void aDocumentThatIsNotACamundaDocumentFailsTheJobAlsoWhileResultsAreMissing() {
    assertFailsWith(
        () -> composer.resolve(CONFIG, TRACKED, afterToolCalls(), results(weather(EXTERNAL))),
        PREFIX + "the result of the tool 'getWeather' contains an external document.");
  }

  @Test
  void aToolCallResultWithoutANameFailsTheJobWithoutAToolName() {
    final var unnamed = ToolCallResultContent.builder().id("c1").content(List.of()).build();
    final var entry =
        new ToolCallBatch.Entry(
            unnamed.withContent(List.of(DocumentContent.documentContent(EXTERNAL))),
            List.of(EXTERNAL),
            List.of());

    assertFailsWith(
        entry::requireCamundaDocuments,
        PREFIX + "the result of a tool contains an external document.");
  }

  @Test
  void aRuntimeToolResultWithADocumentThatIsNotACamundaDocumentFailsTheJob() {
    final var runtimeResult =
        ToolCallResultContent.builder()
            .id("abcdef")
            .name("acme_create_report")
            .content(List.of(objectContent(Map.of("file", INLINE))))
            .build();
    final var batch =
        ToolCallBatch.of(TOOL_CALLS)
            .withEntries(
                List.of(
                    ToolCallBatch.Entry.from(runtimeResult),
                    ToolCallBatch.Entry.from(ToolCallResultContent.from(dateTime()))));

    assertFailsWith(
        () ->
            new ToolCallResultsInputBuilder(TestDocuments.documentContentDelivery())
                .build(TRACKED, batch, Set.of()),
        PREFIX + "the result of the tool 'acme_create_report' contains an inline document.");
  }

  @Test
  void aRuntimeToolResultThatAsksForTheContentOfADocumentThatIsNotACamundaDocumentFailsTheJob() {
    final var runtimeResult =
        ToolCallResultContent.builder()
            .id("abcdef")
            .name("acme_create_report")
            .content(List.of(objectContent(Map.of("file", EXTERNAL))))
            .build();
    final var batch =
        ToolCallBatch.of(TOOL_CALLS)
            .withEntries(
                List.of(
                    new ToolCallBatch.Entry(
                        runtimeResult,
                        ToolCallResultDocumentExtractor.documentsOf(runtimeResult.content()),
                        List.of(
                            new ToolCallEffect.AddDocumentContent(AgentDocumentId.of("doc_1")))),
                    ToolCallBatch.Entry.from(ToolCallResultContent.from(dateTime()))));

    assertFailsWith(
        () ->
            new ToolCallResultsInputBuilder(TestDocuments.documentContentDelivery())
                .build(TRACKED, batch, Set.of()),
        PREFIX + "the result of the tool 'acme_create_report' contains an external document.");
  }

  @Test
  void withoutTheDocumentsFeatureTheDocumentsOfAToolCallResultFollowTheResults() {
    final var result =
        composer.resolve(
            CONFIG,
            UNTRACKED,
            afterToolCalls(),
            results(weather(Map.of("file", EXTERNAL)), dateTime()));

    final var input = assemble(result);
    assertThat(input.documents()).isEmpty();
    assertThat(input.messages()).hasSize(2);
    assertThat(((ToolCallResultMessage) input.messages().getFirst()).results())
        .containsExactly(
            ToolCallResultContent.from(weather(Map.of("file", EXTERNAL))),
            ToolCallResultContent.from(dateTime()));
    assertThat(((UserMessage) input.messages().getLast()).content())
        .containsExactly(
            textContent(ToolCallResultsInputBuilder.TOOL_CALL_DOCUMENTS_PREAMBLE),
            textContent(DocumentReferenceXmlTag.from(EXTERNAL, "abcdef", "getWeather").toXml()),
            DocumentContent.documentContent(EXTERNAL));
  }

  // ── the events ────────────────────────────────────────────────────────────

  @Test
  void anExternalDocumentOfAnEventFailsTheJob() {
    final var event = ToolCallResult.builder().content(Map.of("text", "event", "file", EXTERNAL));

    assertFailsWith(
        () ->
            composer.resolve(
                CONFIG,
                TRACKED,
                firstTurn(),
                AgentInput.from(
                    new UserPromptConfiguration("", List.of()), List.of(event.build()))),
        PREFIX + "an event contains an external document.");
  }

  @Test
  void anInlineDocumentThatIsTheWholeContentOfAnEventFailsTheJob() {
    final var event = ToolCallResult.builder().content(INLINE).build();

    assertFailsWith(
        () ->
            composer.resolve(
                CONFIG,
                TRACKED,
                firstTurn(),
                AgentInput.from(new UserPromptConfiguration("", List.of()), List.of(event))),
        PREFIX + "an event contains an inline document.");
  }

  @Test
  void withoutTheDocumentsFeatureTheDocumentsOfAnEventStayDocuments() {
    final var content = Map.of("file", EXTERNAL);
    final var event = ToolCallResult.builder().content(content).build();

    final var result =
        composer.resolve(
            CONFIG,
            UNTRACKED,
            firstTurn(),
            AgentInput.from(new UserPromptConfiguration("", List.of()), List.of(event)));

    final var input = assemble(result);
    assertThat(input.documents()).isEmpty();
    assertThat(input.messages())
        .singleElement()
        .isInstanceOfSatisfying(
            UserMessage.class,
            message ->
                assertThat(message.content())
                    .containsExactly(
                        objectContent(content),
                        textContent(
                            AgentConversationTurnInputComposerImpl
                                .UNTRACKED_EVENT_DOCUMENTS_PREAMBLE),
                        textContent(DocumentReferenceXmlTag.from(EXTERNAL, null, null).toXml()),
                        DocumentContent.documentContent(EXTERNAL)));
  }
}
