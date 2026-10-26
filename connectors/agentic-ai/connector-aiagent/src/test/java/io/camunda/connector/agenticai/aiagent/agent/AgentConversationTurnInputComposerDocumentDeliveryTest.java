/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.conversation.AgentInput;
import io.camunda.connector.agenticai.aiagent.conversation.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentGuard;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentLimits;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentReferences;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.InlineDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The document delivery of the user prompt. */
class AgentConversationTurnInputComposerDocumentDeliveryTest {

  private static final AgentConfiguration CONFIG =
      new AgentConfiguration(null, null, null, null, null, null, null);
  private static final AgentContext CTX =
      AgentContext.builder()
          .state(AgentState.READY)
          .features(List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS))
          .build();
  private static final AgentContext CTX_WITH_CONVERSATION =
      CTX.withConversation(InProcessConversationContext.builder("conv").build());

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
  private AgentConversationTurnInputComposerImpl composer;

  @BeforeEach
  void setUp() {
    final var gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
    when(gatewayToolHandlers.transformToolCallResults(any(), any()))
        .thenAnswer(inv -> inv.getArgument(1));
    when(gatewayToolHandlers.handlerForToolDefinition(any()))
        .thenReturn(java.util.Optional.empty());
    composer =
        new AgentConversationTurnInputComposerImpl(
            gatewayToolHandlers,
            new ToolCallResultsInputBuilderImpl(
                new DocumentContentGuard(DocumentContentLimits.defaults()),
                TestObjectMapperSupplier.INSTANCE),
            new DocumentContentGuard(DocumentContentLimits.defaults()));
  }

  private Document document(String content, String contentType, String fileName) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(fileName)
            .build());
  }

  private static CompositionResult.NextTurn nextTurn(CompositionResult result) {
    assertThat(result).isInstanceOf(CompositionResult.NextTurn.class);
    return (CompositionResult.NextTurn) result;
  }

  private static boolean isTagWithContent(Content content) {
    return content instanceof DocumentReferenceContent reference
        && reference.delivery() == DocumentDelivery.CONTENT;
  }

  private static DocumentReferenceContent tag(
      Document document, DocumentSource source, boolean loaded) {
    return DocumentReferences.from(document, source)
        .withDelivery(loaded ? DocumentDelivery.CONTENT : DocumentDelivery.REFERENCE);
  }

  // --- user prompt

  private CompositionResult composePrompt(
      AgentContext context,
      @Nullable DocumentDelivery delivery,
      List<Message> previousMessages,
      Document... docs) {
    final var input =
        AgentInput.from(new UserPromptConfiguration("Hello?", List.of(docs), delivery), List.of());
    return composer.compose(
        CONFIG, context, TurnReconstructor.reconstruct(previousMessages), input);
  }

  @Test
  void userPrompt_withTheDeliveryContent_addsTheContentOfADocumentThatPassesTheGuard() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), doc));

    final var message = turn.messages().getFirst();
    final var source = DocumentSource.userMessage();
    assertThat(((UserMessage) message).content())
        .containsExactly(textContent("Hello?"), tag(doc, source, true));
    assertThat(turn.documents()).containsExactly(new IngestedDocument(doc, source));
  }

  @Test
  void userPrompt_withoutADelivery_addsTheContentOfAPdfOverTheLimitWithoutAGuard() {
    final var pdf = document("p".repeat(3_700_000), "application/pdf", "large.pdf");

    final var turn = nextTurn(composePrompt(CTX, null, List.of(), pdf));

    final var message = turn.messages().getFirst();
    assertThat(((UserMessage) message).content())
        .containsExactly(textContent("Hello?"), tag(pdf, DocumentSource.userMessage(), true));
  }

  @Test
  void userPrompt_withoutADelivery_addsTheContentOfADocumentOfAnUnsupportedType() {
    final var archive = document("zip", "application/zip", "archive.zip");

    final var turn = nextTurn(composePrompt(CTX, null, List.of(), archive));

    final var message = turn.messages().getFirst();
    assertThat(((UserMessage) message).content())
        .containsExactly(textContent("Hello?"), tag(archive, DocumentSource.userMessage(), true));
  }

  @Test
  void userPrompt_withoutADelivery_addsTheContentOfAnExternalDocument() {
    final var external =
        new ExternalDocument("https://files.example.com/report.pdf", "report.pdf", url -> null);

    final var turn = nextTurn(composePrompt(CTX, null, List.of(), external));

    final var message = turn.messages().getFirst();
    assertThat(((UserMessage) message).content())
        .containsExactly(textContent("Hello?"), tag(external, DocumentSource.userMessage(), true));
  }

  @Test
  void userPrompt_withReferenceDelivery_addsTheContentOfAnExternalDocument() {
    final var external =
        new ExternalDocument("https://files.example.com/report.pdf", "report.pdf", url -> null);

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.REFERENCE, List.of(), external));

    final var message = turn.messages().getFirst();
    assertThat(((UserMessage) message).content())
        .containsExactly(textContent("Hello?"), tag(external, DocumentSource.userMessage(), true));
  }

  @Test
  void userPrompt_withTheDeliveryContent_keepsTheGuardForAPdfOverTheLimit() {
    final var pdf = document("p".repeat(3_700_000), "application/pdf", "large.pdf");

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), pdf));

    final var message = turn.messages().getFirst();
    assertThat(((UserMessage) message).content())
        .hasSize(3)
        .contains(tag(pdf, DocumentSource.userMessage(), false));
  }

  @Test
  void userPrompt_withReferenceDelivery_keepsTheDocumentAsAReference() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.REFERENCE, List.of(), doc));

    final var message = turn.messages().getFirst();
    assertThat(((UserMessage) message).content())
        .containsExactly(textContent("Hello?"), tag(doc, DocumentSource.userMessage(), false));
  }

  @Test
  void
      userPrompt_withTheDeliveryContent_fallsBackToTheReferenceAndANoteForADocumentThatFailsTheGuard() {
    final var loadable = document("prompt data", "text/plain", "prompt.txt");
    final var archive = document("zip", "application/zip", "archive.zip");

    final var turn =
        nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), loadable, archive));

    final var message = turn.messages().getFirst();
    final var source = DocumentSource.userMessage();
    assertThat(((UserMessage) message).content())
        .containsExactly(
            textContent("Hello?"),
            tag(loadable, source, true),
            tag(archive, source, false),
            textContent(
                "Document %s was not loaded automatically: it is of type application/zip which cannot be loaded into the conversation. Pass its tag to a tool that processes it."
                    .formatted(DocumentHandle.idFor(archive))));
    // both documents are registered, loaded or not
    assertThat(turn.documents()).extracting(IngestedDocument::document).contains(loadable, archive);
  }

  @Test
  void userPrompt_withTheDeliveryContent_theNoteForAnUnreadableDocumentHoldsNoDetailOfTheFailure() {
    final var unreadable =
        new InlineDocument("data", "report.txt", "text/plain") {
          @Override
          public InputStream asInputStream() {
            throw new IllegalStateException(
                "GET https://bucket.example/report?token=secret failed");
          }
        };

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), unreadable));

    final var message = turn.messages().getFirst();
    final var source = DocumentSource.userMessage();
    assertThat(((UserMessage) message).content())
        .containsExactly(
            textContent("Hello?"),
            tag(unreadable, source, false),
            textContent(
                "Document %s was not loaded automatically: it could not be read to check its size. A tool for loading documents can try to load it again."
                    .formatted(DocumentHandle.idFor(unreadable))));
  }

  @Test
  void userPrompt_withTheDeliveryContent_addsTheContentOfAnExternalDocumentWithoutGuardingIt() {
    final var downloads = new AtomicInteger();
    final var external =
        new ExternalDocument(
            "https://files.example.com/report.pdf",
            "report.pdf",
            url -> {
              downloads.incrementAndGet();
              return null;
            });

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), external));

    final var message = turn.messages().getFirst();
    final var source = DocumentSource.userMessage();
    assertThat(((UserMessage) message).content())
        .containsExactly(textContent("Hello?"), tag(external, source, true));
    assertThat(downloads).hasValue(0);
  }

  @Test
  void userPrompt_withTheDeliveryContent_addsTheContentOfADocumentOnlyOnce() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");

    final var turn = nextTurn(composePrompt(CTX, DocumentDelivery.CONTENT, List.of(), doc, doc));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .filteredOn(AgentConversationTurnInputComposerDocumentDeliveryTest::isTagWithContent)
        .hasSize(1);
  }

  @Test
  void
      userPrompt_withTheDeliveryContent_doesNotAddTheContentOfADocumentThatAlreadyHasContentInTheConversation() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");
    final var earlier =
        UserMessage.builder()
            .content(List.of(tag(doc, DocumentSource.userMessage(), true)))
            .build();

    final var turn =
        nextTurn(
            composePrompt(
                CTX_WITH_CONVERSATION,
                DocumentDelivery.CONTENT,
                List.of(earlier, assistantMessage("done")),
                doc));

    final var message = turn.messages().getFirst();
    assertThat(((UserMessage) message).content())
        .containsExactly(textContent("Hello?"), tag(doc, DocumentSource.userMessage(), false));
  }

  @Test
  void
      userPrompt_withoutADelivery_addsTheContentOfADocumentThatAlreadyHasContentInTheConversation() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");
    final var earlier =
        UserMessage.builder()
            .content(List.of(tag(doc, DocumentSource.userMessage(), true)))
            .build();

    final var turn =
        nextTurn(
            composePrompt(
                CTX_WITH_CONVERSATION, null, List.of(earlier, assistantMessage("done")), doc, doc));

    final var message = turn.messages().getFirst();
    final var source = DocumentSource.userMessage();
    assertThat(((UserMessage) message).content())
        .containsExactly(textContent("Hello?"), tag(doc, source, true), tag(doc, source, true));
  }

  @Test
  void userPrompt_ofAnAgentInstanceWithoutTheDocumentsFeature_ignoresTheDelivery() {
    final var doc = document("prompt data", "text/plain", "prompt.txt");
    final var legacy = AgentContext.builder().state(AgentState.READY).build();

    final var turn = nextTurn(composePrompt(legacy, DocumentDelivery.CONTENT, List.of(), doc));

    assertThat(((UserMessage) turn.messages().getFirst()).content())
        .containsExactly(textContent("Hello?"), DocumentContent.documentContent(doc));
    assertThat(turn.documents()).isEmpty();
  }
}
