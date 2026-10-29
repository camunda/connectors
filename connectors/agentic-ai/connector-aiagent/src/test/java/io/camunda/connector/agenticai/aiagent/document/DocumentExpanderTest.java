/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentExpanderTest {

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  private Document camundaDocument(String content) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType("application/pdf")
            .fileName("report.pdf")
            .build());
  }

  private static UserMessage userMessage(Content... content) {
    return UserMessage.builder().content(List.of(content)).build();
  }

  private static DocumentReferenceContent reference(
      Document document, DocumentSource source, DocumentDelivery delivery) {
    return DocumentReferences.from(document, source).withDelivery(delivery);
  }

  private static TextContent tagOf(DocumentReferenceContent reference) {
    return textContent(reference.toTag());
  }

  private static Map<?, ?> referenceOf(Document document) {
    return TestObjectMapperSupplier.INSTANCE.convertValue(document, Map.class);
  }

  private static void assertRebuiltDocumentIs(Content content, Document expected) {
    assertThat(content)
        .isInstanceOfSatisfying(
            DocumentContent.class,
            documentContent -> {
              assertThat(DocumentHandle.idFor(documentContent.document()))
                  .isEqualTo(DocumentHandle.idFor(expected));
              assertThat(referenceOf(documentContent.document())).isEqualTo(referenceOf(expected));
            });
  }

  @Test
  void expandingForTheModelAddsTheDocumentOnlyAfterAReferenceWithDeliveryContent() {
    final var documentWithContent = camundaDocument("loaded");
    final var referencedDocument = camundaDocument("referenced");
    final var withContent =
        reference(documentWithContent, DocumentSource.userMessage(), DocumentDelivery.CONTENT);
    final var referenced =
        reference(referencedDocument, DocumentSource.userMessage(), DocumentDelivery.REFERENCE);
    final var message = userMessage(withContent, referenced);

    final var expanded = DocumentExpander.expandMessages(List.of(message));

    final var content = ((UserMessage) expanded.getFirst()).content();
    assertThat(content).hasSize(3);
    assertThat(content.get(0)).isEqualTo(tagOf(withContent));
    assertRebuiltDocumentIs(content.get(1), documentWithContent);
    assertThat(content.get(2)).isEqualTo(tagOf(referenced));
  }

  @Test
  void expandingForTheModelTurnsAReferenceWithDeliveryReferenceIntoItsTagOnly() {
    final var document = camundaDocument("hello");
    final var reference =
        reference(document, DocumentSource.userMessage(), DocumentDelivery.REFERENCE);
    final var message = userMessage(reference);

    final var expanded = DocumentExpander.expandMessages(List.of(message));

    assertThat(((UserMessage) expanded.getFirst()).content()).containsExactly(tagOf(reference));
    assertThat(((UserMessage) expanded.getFirst()).id()).isEqualTo(message.id());
  }

  @Test
  void expandingForTheModelLeavesAMessageWithoutDocumentReferencesAlone() {
    final var messages =
        List.<Message>of(userMessage(textContent("hello")), assistantMessage("hi"));

    assertThat(DocumentExpander.expandMessages(messages)).isEqualTo(messages);
  }

  @Test
  void leavesTagsInPlainTextAlone() {
    final var document = camundaDocument("hello");
    final var literal =
        "<doc id=\"%s\" fileName=\"report.pdf\" />".formatted(DocumentHandle.idFor(document));
    final var messages =
        List.<Message>of(userMessage(textContent("See " + literal), textContent(literal)));

    assertThat(DocumentExpander.expandMessages(messages)).isEqualTo(messages);
  }

  @Test
  void expandingTheResultOfAnExpansionChangesNothing() {
    final var document = camundaDocument("hello");
    final var messages =
        List.<Message>of(
            userMessage(reference(document, DocumentSource.event(), DocumentDelivery.CONTENT)),
            toolResultMessage(DocumentReferences.from(document)));

    final var once = DocumentExpander.expandMessages(messages);

    assertThat(DocumentExpander.expandMessages(once)).isEqualTo(once);
  }

  @Test
  void expandsTheContentOfUserMessagesOnly() {
    final var document = camundaDocument("hello");
    final var assistant = assistantMessage("hi");
    final var plainUser = userMessage(textContent("hello"));
    final var documentUser =
        userMessage(reference(document, DocumentSource.userMessage(), DocumentDelivery.CONTENT));
    final var messages = List.<Message>of(assistant, plainUser, documentUser);

    final var expanded = DocumentExpander.expandMessages(messages);

    assertThat(expanded).hasSize(3);
    assertThat(expanded.get(0)).isEqualTo(assistant);
    assertThat(expanded.get(1)).isEqualTo(plainUser);
    assertThat(expanded.get(2).id()).isEqualTo(documentUser.id());
    assertThat(((UserMessage) expanded.get(2)).content()).hasSize(2);
    assertThat(DocumentExpander.expandMessages(List.of(assistant, plainUser)))
        .containsExactly(assistant, plainUser);
  }

  private static ToolCallResultMessage toolResultMessage(Content... content) {
    return ToolCallResultMessage.builder()
        .results(
            List.of(
                ToolCallResultContent.builder()
                    .id("call-1")
                    .name("fetch")
                    .content(List.of(content))
                    .completedAt(OffsetDateTime.parse("2026-07-02T09:59:55Z"))
                    .build()))
        .build();
  }

  private static List<Content> resultContent(Message message) {
    return ((ToolCallResultMessage) message).results().getFirst().content();
  }

  @Test
  void expandsAReferenceInAToolResultToItsTagText() {
    final var document = camundaDocument("hello");
    final var reference = DocumentReferences.from(document);
    final var message = toolResultMessage(textContent("before"), reference);

    final var expanded = DocumentExpander.expandMessages(List.of(message));

    assertThat(expanded.getFirst().id()).isEqualTo(message.id());
    assertThat(((ToolCallResultMessage) expanded.getFirst()).results().getFirst().id())
        .isEqualTo("call-1");
    final var content = resultContent(expanded.getFirst());
    assertThat(content).hasSize(2);
    assertThat(content.get(0)).isEqualTo(textContent("before"));
    assertThat(content.get(1)).isEqualTo(tagOf(reference));
  }

  @Test
  void aReferenceInAToolResultBecomesTheShortTagOfItsDocument() {
    final var document = camundaDocument("hello");
    final var reference = DocumentReferences.from(document);
    final var withSource =
        reference(document, DocumentSource.toolCall("fetch", "call-1"), DocumentDelivery.REFERENCE);

    final var expanded =
        resultContent(
            DocumentExpander.expandMessages(List.of(toolResultMessage(withSource))).getFirst());

    assertThat(withSource.toTag()).contains("toolName").contains("toolCallId");
    assertThat(expanded).containsExactly(tagOf(reference));
  }

  @Test
  void aReferenceInAToolResultNeverGetsTheDocumentAfterItsTag() {
    final var document = camundaDocument("hello");

    final var expanded =
        DocumentExpander.expandMessages(
            List.of(
                toolResultMessage(
                    DocumentReferences.from(document).withDelivery(DocumentDelivery.CONTENT))));

    assertThat(resultContent(expanded.getFirst()))
        .containsExactly(tagOf(DocumentReferences.from(document)));
  }

  @Test
  void leavesToolResultsWithoutReferencesAlone() {
    final var document = camundaDocument("hello");
    final var messages =
        List.<Message>of(
            toolResultMessage(
                textContent("text"),
                DocumentContent.documentContent(document),
                ObjectContent.objectContent(Map.of("status", 200))));

    assertThat(DocumentExpander.expandMessages(messages)).isEqualTo(messages);
  }

  @Test
  void replacesADocumentNestedInAnObjectOfAToolResultByItsTag() {
    final var document = camundaDocument("hello");
    final var metadata = Map.<String, Object>of("key", "value");
    final var message =
        toolResultMessage(
            new ObjectContent(
                Map.of("status", 200, "files", List.of(Map.of("file", document))), metadata));

    final var expanded = DocumentExpander.expandMessages(List.of(message));

    assertThat(resultContent(expanded.getFirst()))
        .containsExactly(
            new ObjectContent(
                Map.of(
                    "status",
                    200,
                    "files",
                    List.of(Map.of("file", DocumentReferences.from(document).toTag()))),
                metadata));
    assertThat(resultContent(message).getFirst())
        .isInstanceOfSatisfying(
            ObjectContent.class,
            stored ->
                assertThat(((Map<?, ?>) stored.content()).get("files"))
                    .isEqualTo(List.of(Map.of("file", document))));
  }

  @Test
  void replacesADocumentNestedInAnObjectOfAUserMessageByItsTag() {
    final var document = camundaDocument("hello");

    final var expanded =
        DocumentExpander.expandMessages(
            List.of(userMessage(ObjectContent.objectContent(Map.of("file", document)))));

    assertThat(((UserMessage) expanded.getFirst()).content())
        .containsExactly(
            ObjectContent.objectContent(Map.of("file", DocumentReferences.from(document).toTag())));
  }

  @Test
  void rendersADocumentAtAnyDepthAsItsTagAndCollectsTheDocuments() {
    final var first = camundaDocument("one");
    final var second = camundaDocument("two");

    final var tagged =
        DocumentExpander.renderWithTags(
            Map.of("status", 200, "files", List.of(Map.of("file", first), Map.of("file", second))));

    assertThat(tagged.tree())
        .isEqualTo(
            Map.of(
                "status",
                200,
                "files",
                List.of(
                    Map.of("file", DocumentReferences.from(first).toTag()),
                    Map.of("file", DocumentReferences.from(second).toTag()))));
    assertThat(tagged.documents()).containsExactly(first, second);
  }
}
