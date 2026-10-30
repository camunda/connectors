/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.connector.agenticai.aiagent.api.tool.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.api.document.Document;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

class ConversationDocumentsTest {

  private static final AgentContext WITH_DOCUMENTS =
      AgentContext.empty().withFeatures(List.of(AgentFeatures.DOCUMENTS));

  private final Document prompt = createDocument("prompt", "prompt.txt");
  private final Document event = createDocument("event", "event.txt");
  private final Document whole = createDocument("whole", "whole.txt");
  private final Document nested = createDocument("nested", "nested.txt");

  private static UserMessage userMessageWith(Content... content) {
    return UserMessage.builder().content(List.of(content)).build();
  }

  private static ToolCallResultMessage toolResult(String id, String name, Content... content) {
    return ToolCallResultMessage.builder()
        .results(
            List.of(
                ToolCallResultContent.builder()
                    .id(id)
                    .name(name)
                    .content(List.of(content))
                    .completedAt(OffsetDateTime.parse("2026-10-29T10:00:00Z"))
                    .build()))
        .build();
  }

  private static List<Tuple> idsAndSources(ConversationDocuments documents) {
    return documents.all().stream()
        .map(document -> tuple(document.id(), document.source()))
        .toList();
  }

  @Test
  void knowsTheDocumentsOfUserMessagesEventsAndToolResultsInTheOrderOfTheMessages() {
    record Attachment(String name, Document file) {}

    final var documents =
        ConversationDocuments.of(
            List.of(
                userMessageWith(
                    textContent("see"),
                    DocumentHandle.referenceFor(prompt, DocumentSource.userMessage())),
                assistantMessage("calling"),
                toolResult(
                    "call-1",
                    "fetch",
                    DocumentHandle.referenceFor(whole),
                    ObjectContent.objectContent(
                        Map.of("attachments", List.of(new Attachment("n", nested))))),
                userMessageWith(DocumentHandle.referenceFor(event, DocumentSource.event()))));

    assertThat(idsAndSources(documents))
        .containsExactly(
            tuple(DocumentHandle.idFor(prompt), DocumentSource.userMessage()),
            tuple(DocumentHandle.idFor(whole), DocumentSource.toolCall("fetch", "call-1")),
            tuple(DocumentHandle.idFor(nested), DocumentSource.toolCall("fetch", "call-1")),
            tuple(DocumentHandle.idFor(event), DocumentSource.event()));
    assertThat(documents.find(DocumentHandle.idFor(nested)))
        .map(AgentDocument::document)
        .containsSame(nested);
  }

  @Test
  void ignoresDocumentsOutsideOfReferencesAndToolResults() {
    final var documents =
        ConversationDocuments.of(
            List.of(
                // a document that is content of a user message, as without the documents feature
                userMessageWith(DocumentContent.documentContent(prompt)),
                // an object of a user message, the events carry their documents as references
                userMessageWith(ObjectContent.objectContent(Map.of("file", event))),
                assistantMessage("<doc id=\"%s\" />".formatted(DocumentHandle.idFor(whole)))));

    assertThat(documents.all()).isEmpty();
  }

  @Test
  void theFirstOccurrenceOfADocumentWins() {
    final var documents =
        ConversationDocuments.of(
            List.of(
                userMessageWith(DocumentHandle.referenceFor(prompt, DocumentSource.userMessage())),
                toolResult("call-1", "fetch", DocumentHandle.referenceFor(prompt))));

    assertThat(idsAndSources(documents))
        .containsExactly(tuple(DocumentHandle.idFor(prompt), DocumentSource.userMessage()));
  }

  @Test
  void seenByModelReadsTheWindowOfTheMessagesBeforeTheLastAnswer() {
    final var early = createDocument("early", "early.txt");
    final var late = createDocument("late", "late.txt");
    final List<Message> messages =
        List.of(
            systemMessage("system"),
            userMessageWith(DocumentHandle.referenceFor(early, DocumentSource.userMessage())),
            assistantMessage("one"),
            userMessageWith(DocumentHandle.referenceFor(late, DocumentSource.userMessage())),
            assistantMessage("two"));

    // without the answer the model was called with system, early, one, late
    assertThat(ConversationDocuments.seenByModel(WITH_DOCUMENTS, messages, 3).all())
        .extracting(AgentDocument::id)
        .containsExactly(DocumentHandle.idFor(late));
    assertThat(ConversationDocuments.seenByModel(WITH_DOCUMENTS, messages, 4).all())
        .extracting(AgentDocument::id)
        .containsExactly(DocumentHandle.idFor(early), DocumentHandle.idFor(late));
  }

  @Test
  void seenByModelIsEmptyWithoutTheDocumentsFeature() {
    final var messages =
        List.<Message>of(
            userMessageWith(DocumentHandle.referenceFor(prompt, DocumentSource.userMessage())),
            userMessage("next"));

    assertThat(ConversationDocuments.seenByModel(AgentContext.empty(), messages, 20).all())
        .isEmpty();
  }

  @Test
  void theReferenceOfAKnownDocumentCarriesItsSource() {
    final var documents =
        ConversationDocuments.of(
            List.of(toolResult("call-1", "fetch", DocumentHandle.referenceFor(whole))));

    assertThat(documents.reference(DocumentHandle.idFor(whole)))
        .isEqualTo(DocumentHandle.referenceFor(whole, DocumentSource.toolCall("fetch", "call-1")));
    assertThatThrownBy(() -> documents.reference(DocumentId.of("unknown")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
