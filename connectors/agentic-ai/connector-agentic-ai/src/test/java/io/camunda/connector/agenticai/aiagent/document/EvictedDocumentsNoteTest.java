/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvictedDocumentsNoteTest {

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  private final Document report = createDocument("report.pdf", "application/pdf");
  private final Document notes = createDocument("notes.txt", "text/plain");

  private final DocumentRegistry registry =
      DocumentRegistry.empty()
          .withDocuments(
              List.of(
                  new IngestedDocument(report, DocumentSource.toolCall("search", "call_1", null)),
                  new IngestedDocument(notes, DocumentSource.userMessage(null))));

  private Document createDocument(String fileName, String contentType) {
    return documentFactory.create(
        DocumentCreationRequest.from(fileName.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(fileName)
            .build());
  }

  private static UserMessage userMessageWith(Content... content) {
    return UserMessage.builder().content(List.of(content)).build();
  }

  /** The ids of the documents the note at the end of the messages lists, in order. */
  private static List<String> listedIds(List<Message> messages) {
    return ((UserMessage) messages.getLast())
        .content().stream()
            .filter(DocumentReferenceContent.class::isInstance)
            .map(content -> ((DocumentReferenceContent) content).id())
            .toList();
  }

  @Test
  void leavesTheMessagesUntouchedWhenTheRegistryIsEmpty() {
    final List<Message> windowed = List.of(userMessage("hi"));

    assertThat(EvictedDocumentsNote.appendTo(windowed, DocumentRegistry.empty()))
        .isSameAs(windowed);
  }

  @Test
  void leavesTheMessagesUntouchedWhenEveryDocumentIsVisible() {
    final List<Message> windowed =
        List.of(
            userMessageWith(
                textContent("here"),
                DocumentContent.documentContent(report),
                DocumentContent.documentContent(notes)));

    assertThat(EvictedDocumentsNote.appendTo(windowed, registry)).isSameAs(windowed);
  }

  @Test
  void aDocumentReferenceCountsAsVisible() {
    final List<Message> windowed =
        List.of(
            userMessageWith(
                textContent("here"),
                DocumentReferenceContent.from(registry.entries().get(0)),
                DocumentReferenceContent.from(registry.entries().get(1))));

    assertThat(EvictedDocumentsNote.appendTo(windowed, registry)).isSameAs(windowed);
  }

  @Test
  void listsOnlyTheDocumentsThatAreNotVisibleAsTrailingUserMessage() {
    final List<Message> windowed =
        List.of(userMessageWith(DocumentContent.documentContent(notes)), assistantMessage("ok"));
    final var withTrailingUser =
        List.<Message>of(windowed.get(0), assistantMessage("ok"), userMessage("next"));

    final var result = EvictedDocumentsNote.appendTo(withTrailingUser, registry);

    assertThat(result).hasSize(4);
    assertThat(result.subList(0, 3)).isEqualTo(withTrailingUser);
    assertThat(result.getLast())
        .isInstanceOfSatisfying(
            UserMessage.class,
            note ->
                assertThat(note.content())
                    .containsExactly(
                        textContent(EvictedDocumentsNote.PREAMBLE),
                        DocumentReferenceContent.from(
                            registry.entries().get(0), DocumentDelivery.REFERENCE)));
  }

  @Test
  void listsEveryDocumentInRegistryOrderWhenNoneIsVisible() {
    final var result = EvictedDocumentsNote.appendTo(List.of(userMessage("next")), registry);

    assertThat(((UserMessage) result.getLast()).content())
        .hasSize(3)
        .satisfies(
            content -> {
              assertThat(content.get(0)).isEqualTo(textContent(EvictedDocumentsNote.PREAMBLE));
              assertThat(content.get(1))
                  .isEqualTo(
                      DocumentReferenceContent.from(
                          registry.entries().get(0), DocumentDelivery.REFERENCE));
              assertThat(content.get(2))
                  .isEqualTo(
                      DocumentReferenceContent.from(
                          registry.entries().get(1), DocumentDelivery.REFERENCE));
            });
  }

  @Test
  void neverRendersReferenceFieldsOfTheRegisteredDocuments() {
    final var result = EvictedDocumentsNote.appendTo(List.of(userMessage("next")), registry);

    assertThat(((UserMessage) result.getLast()).content().toString())
        .doesNotContain("storeId", "contentHash", "documentId", "camunda.document.type");
  }

  @Test
  void aDocumentCountsAsVisibleWhileTheMessageThatIntroducedItIsWindowed() {
    final var toolResultMessage = ToolCallResultMessage.builder().results(List.of()).build();
    final var introduced =
        DocumentRegistry.empty()
            .withDocuments(
                List.of(
                    new IngestedDocument(
                        report,
                        DocumentSource.toolCall(
                            "search", "call_1", toolResultMessage.id().toString()))));

    assertThat(EvictedDocumentsNote.appendTo(List.of(toolResultMessage), introduced))
        .containsExactly(toolResultMessage);
    assertThat(EvictedDocumentsNote.appendTo(List.of(userMessage("next")), introduced)).hasSize(2);
  }

  @Test
  void aDocumentNestedInAToolResultCountsAsVisible() {
    final List<Message> windowed =
        List.of(
            ToolCallResultMessage.builder()
                .results(
                    List.of(
                        ToolCallResultContent.builder()
                            .id("call_1")
                            .name("search")
                            .content(
                                List.of(
                                    ObjectContent.objectContent(
                                        Map.of("attachments", List.of(report)))))
                            .build()))
                .build());

    final var result = EvictedDocumentsNote.appendTo(windowed, registry);

    assertThat(listedIds(result)).containsExactly(DocumentHandle.idFor(notes));
  }

  private ToolCallResultMessage toolResultWith(Content... content) {
    return ToolCallResultMessage.builder()
        .results(
            List.of(
                ToolCallResultContent.builder()
                    .id("call_2")
                    .name("fetch")
                    .content(List.of(content))
                    .build()))
        .build();
  }

  private DocumentRegistry registryOfEvictedMessage() {
    // both documents were introduced by a message outside the window
    return DocumentRegistry.empty()
        .withDocuments(
            List.of(
                new IngestedDocument(
                    report, DocumentSource.toolCall("search", "call_1", "evicted-message")),
                new IngestedDocument(notes, DocumentSource.userMessage("evicted-message"))));
  }

  @Test
  void aDocumentReferenceInAToolResultCountsAsVisible() {
    final List<Message> windowed = List.of(toolResultWith(DocumentReferenceContent.from(report)));

    final var result = EvictedDocumentsNote.appendTo(windowed, registryOfEvictedMessage());

    assertThat(listedIds(result)).containsExactly(DocumentHandle.idFor(notes));
  }

  @Test
  void aDocumentContentInAToolResultCountsAsVisible() {
    final List<Message> windowed = List.of(toolResultWith(DocumentContent.documentContent(report)));

    final var result = EvictedDocumentsNote.appendTo(windowed, registryOfEvictedMessage());

    assertThat(listedIds(result)).containsExactly(DocumentHandle.idFor(notes));
  }

  @Test
  void aTagTypedAsTextDoesNotCountAsVisible() {
    final List<Message> windowed =
        List.of(
            userMessageWith(
                textContent(
                    "<doc id=\"%s\" fileName=\"report.pdf\" />"
                        .formatted(DocumentHandle.idFor(report))),
                textContent(
                    "<doc id=\"%s\" fileName=\"notes.txt\" />"
                        .formatted(DocumentHandle.idFor(notes)))),
            toolResultWith(
                textContent("<doc id=\"%s\" />".formatted(DocumentHandle.idFor(report)))));

    final var result = EvictedDocumentsNote.appendTo(windowed, registryOfEvictedMessage());

    assertThat(listedIds(result))
        .containsExactly(DocumentHandle.idFor(report), DocumentHandle.idFor(notes));
  }

  @Test
  void aTagTypedAsTextDoesNotHideADocumentButItsWindowedSourceMessageDoes() {
    final var prompt =
        userMessageWith(textContent("see <doc id=\"" + DocumentHandle.idFor(notes) + "\" />"));
    final var introducedByPrompt =
        DocumentRegistry.empty()
            .withDocuments(
                List.of(
                    new IngestedDocument(notes, DocumentSource.userMessage(prompt.id().toString())),
                    new IngestedDocument(
                        report, DocumentSource.toolCall("search", "call_1", "evicted-message"))));
    final List<Message> windowed = List.of(prompt);

    final var result = EvictedDocumentsNote.appendTo(windowed, introducedByPrompt);

    // the notes document is visible because its source message is windowed, not because of the tag
    // typed into it; the report was introduced by a message that left the window
    assertThat(listedIds(result)).containsExactly(DocumentHandle.idFor(report));
    final List<Message> withoutPrompt = List.of(userMessage("next"));
    assertThat(listedIds(EvictedDocumentsNote.appendTo(withoutPrompt, introducedByPrompt)))
        .containsExactly(DocumentHandle.idFor(notes), DocumentHandle.idFor(report));
  }

  @Test
  void doesNotAddANoteAfterAnAssistantMessage() {
    // a continuation round resumes on the assistant message that ends the conversation
    final List<Message> windowed = List.of(userMessage("hi"), assistantMessage("paused"));

    assertThat(EvictedDocumentsNote.appendTo(windowed, registry)).isSameAs(windowed);
  }
}
