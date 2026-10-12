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
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
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

  private static UserMessage userMessageWith(
      io.camunda.connector.agenticai.aiagent.model.message.content.Content... content) {
    return UserMessage.builder().content(List.of(content)).build();
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
                        textContent(
                            "<doc id=\"%s\" fileName=\"report.pdf\" contentType=\"application/pdf\" toolName=\"search\" toolCallId=\"call_1\" />"
                                .formatted(DocumentHandle.idFor(report)))));
  }

  @Test
  void listsEveryDocumentInRegistryOrderWhenNoneIsVisible() {
    final var result = EvictedDocumentsNote.appendTo(List.of(userMessage("next")), registry);

    assertThat(((UserMessage) result.getLast()).content())
        .extracting(content -> ((TextContent) content).text())
        .hasSize(3)
        .satisfies(
            texts -> {
              assertThat(texts.get(1)).contains(DocumentHandle.idFor(report));
              assertThat(texts.get(2)).contains(DocumentHandle.idFor(notes));
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

    assertThat(((UserMessage) result.getLast()).content())
        .extracting(content -> ((TextContent) content).text())
        .hasSize(2)
        .last()
        .asString()
        .contains(DocumentHandle.idFor(notes))
        .doesNotContain(DocumentHandle.idFor(report));
  }

  @Test
  void aTagInTextCountsAsVisible() {
    final List<Message> windowed =
        List.of(
            userMessageWith(
                textContent(
                    "<doc id=\"%s\" fileName=\"report.pdf\" />"
                        .formatted(DocumentHandle.idFor(report))),
                textContent(
                    "<doc id=\"%s\" fileName=\"notes.txt\" />"
                        .formatted(DocumentHandle.idFor(notes)))));

    assertThat(EvictedDocumentsNote.appendTo(windowed, registry)).isSameAs(windowed);
  }

  @Test
  void doesNotAddANoteAfterAnAssistantMessage() {
    // a continuation round resumes on the assistant message that ends the conversation
    final List<Message> windowed = List.of(userMessage("hi"), assistantMessage("paused"));

    assertThat(EvictedDocumentsNote.appendTo(windowed, registry)).isSameAs(windowed);
  }
}
