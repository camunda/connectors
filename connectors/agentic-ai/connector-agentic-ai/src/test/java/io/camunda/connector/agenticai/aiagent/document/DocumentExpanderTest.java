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

import io.camunda.connector.agenticai.aiagent.chatmodel.provider.ModelFacingJson;
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
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.InlineDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentExpanderTest {

  private final DocumentExpander expander = new DocumentExpander(TestObjectMapperSupplier.INSTANCE);
  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  private Document camundaDocument(String content) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType("application/pdf")
            .fileName("report.pdf")
            .build());
  }

  private static DocumentRegistry registryOf(Document document, DocumentSource source) {
    return DocumentRegistry.empty().withDocuments(List.of(new IngestedDocument(document, source)));
  }

  private static UserMessage userMessage(Content... content) {
    return UserMessage.builder().content(List.of(content)).build();
  }

  private static DocumentReferenceContent reference(
      DocumentRegistry registry, int index, DocumentDelivery delivery) {
    return DocumentReferenceContent.from(registry.entries().get(index), delivery);
  }

  private static TextContent tagOf(DocumentReferenceContent reference) {
    return textContent(reference.toTag());
  }

  private List<Content> expand(List<Content> content, DocumentRegistry registry) {
    return expander.expandContent(content, registry);
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
  void expandsAReferenceToItsTagAndTheDocumentFromTheRegistryForTheHistory() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.toolCall("search", "call-1", "m-1"));
    final var reference = reference(registry, 0, DocumentDelivery.REFERENCE);

    final var expanded = expand(List.of(textContent("preamble"), reference), registry);

    assertThat(expanded).hasSize(3);
    assertThat(expanded.subList(0, 2)).containsExactly(textContent("preamble"), tagOf(reference));
    assertRebuiltDocumentIs(expanded.get(2), document);
  }

  @Test
  void expandingForTheModelAddsTheDocumentOnlyAfterAReferenceWithDeliveryContent() {
    final var documentWithContent = camundaDocument("loaded");
    final var referencedDocument = camundaDocument("referenced");
    final var registry =
        DocumentRegistry.empty()
            .withDocuments(
                List.of(
                    new IngestedDocument(documentWithContent, DocumentSource.userMessage("m-1")),
                    new IngestedDocument(referencedDocument, DocumentSource.userMessage("m-1"))));
    final var withContent = reference(registry, 0, DocumentDelivery.CONTENT);
    final var referenced = reference(registry, 1, DocumentDelivery.REFERENCE);
    final var message = userMessage(withContent, referenced);

    final var expanded = expander.expandMessages(List.of(message), registry);

    final var content = ((UserMessage) expanded.getFirst()).content();
    assertThat(content).hasSize(3);
    assertThat(content.get(0)).isEqualTo(tagOf(withContent));
    assertRebuiltDocumentIs(content.get(1), documentWithContent);
    assertThat(content.get(2)).isEqualTo(tagOf(referenced));
  }

  @Test
  void expandingForTheModelTurnsAReferenceWithDeliveryReferenceIntoItsTagOnly() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.userMessage(null));
    final var reference = reference(registry, 0, DocumentDelivery.REFERENCE);
    final var message = userMessage(reference);

    final var expanded = expander.expandMessages(List.of(message), registry);

    assertThat(((UserMessage) expanded.getFirst()).content()).containsExactly(tagOf(reference));
    assertThat(((UserMessage) expanded.getFirst()).id()).isEqualTo(message.id());
  }

  @Test
  void expandingForTheModelLeavesAMessageWithoutDocumentReferencesAlone() {
    final var messages =
        List.<Message>of(userMessage(textContent("hello")), assistantMessage("hi"));

    assertThat(expander.expandMessages(messages, DocumentRegistry.empty())).isSameAs(messages);
  }

  @Test
  void expandingForTheModelKeepsTheTagWithoutContentWhenTheDocumentIsNotInTheRegistry() {
    final var reference =
        new DocumentReferenceContent(
            "unknown", null, null, null, null, null, null, DocumentDelivery.CONTENT);

    final var expanded =
        expander.expandMessages(List.of(userMessage(reference)), DocumentRegistry.empty());

    assertThat(((UserMessage) expanded.getFirst()).content()).containsExactly(tagOf(reference));
  }

  @Test
  void expandingForTheHistoryAddsTheDocumentAfterEveryReference() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.userMessage(null));
    final var reference = reference(registry, 0, DocumentDelivery.REFERENCE);

    final var expanded = expander.expandContent(List.of(reference), registry);

    assertThat(expanded).hasSize(2);
    assertThat(expanded.get(0)).isEqualTo(tagOf(reference));
    assertRebuiltDocumentIs(expanded.get(1), document);
  }

  @Test
  void rebuildsAnExternalAndAnInlineDocumentFromTheirReference() {
    final var external = new ExternalDocument("https://example.com/a.pdf", "a.pdf", u -> null);
    final var inline = new InlineDocument("{\"a\":1}", "data.json", "application/json");
    final var registry =
        DocumentRegistry.empty()
            .withDocuments(
                List.of(
                    new IngestedDocument(external, DocumentSource.userMessage("m-1")),
                    new IngestedDocument(inline, DocumentSource.userMessage("m-1"))));
    final var references =
        registry.entries().stream().<Content>map(DocumentReferenceContent::from).toList();

    final var expanded = expand(references, registry);

    assertThat(expanded).hasSize(4);
    assertRebuiltDocumentIs(expanded.get(1), external);
    assertRebuiltDocumentIs(expanded.get(3), inline);
  }

  @Test
  void doesNotAddTheDocumentAgainWhenItAlreadyFollowsTheReference() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.userMessage(null));
    final var reference = reference(registry, 0, DocumentDelivery.REFERENCE);
    final var content = List.<Content>of(reference, DocumentContent.documentContent(document));

    assertThat(expand(content, registry))
        .hasSize(2)
        .containsExactly(tagOf(reference), DocumentContent.documentContent(document));
  }

  @Test
  void expandsAReferenceWithoutRegistryEntryToItsTagOnly() {
    final var reference =
        new DocumentReferenceContent(
            "unknown", null, null, null, null, null, null, DocumentDelivery.REFERENCE);
    final var content = List.<Content>of(textContent("text"), reference);

    assertThat(expand(content, DocumentRegistry.empty()))
        .containsExactly(textContent("text"), tagOf(reference));
  }

  @Test
  void expandsAReferenceToItsTagOnlyWhenTheRegistryReferenceCannotBeRead() {
    final var registry =
        DocumentRegistry.of(
            List.of(
                new DocumentRegistryEntry(
                    "broken",
                    DocumentSource.userMessage(null),
                    Map.of("camunda.document.type", "unsupported"))));
    final var reference =
        new DocumentReferenceContent(
            "broken", null, null, null, null, null, null, DocumentDelivery.REFERENCE);

    assertThat(expand(List.of(reference), registry)).containsExactly(tagOf(reference));
  }

  @Test
  void leavesTagsInPlainTextAlone() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.userMessage(null));
    final var literal =
        "<doc id=\"%s\" fileName=\"report.pdf\" />".formatted(DocumentHandle.idFor(document));
    final var content = List.<Content>of(textContent("See " + literal), textContent(literal));

    assertThat(expand(content, registry)).isSameAs(content);
  }

  @Test
  void expandingTwiceGivesTheSameContent() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.event("m-1"));
    final var content = List.<Content>of(reference(registry, 0, DocumentDelivery.REFERENCE));

    final var once = expand(content, registry);

    assertThat(expand(content, registry)).hasSameSizeAs(once);
    assertThat(expand(content, registry).getFirst()).isEqualTo(once.getFirst());
    assertThat(referenceOf(((DocumentContent) expand(content, registry).getLast()).document()))
        .isEqualTo(referenceOf(((DocumentContent) once.getLast()).document()));
    assertThat(expand(once, registry)).isSameAs(once);
  }

  @Test
  void expandsTheContentOfUserMessagesOnly() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.userMessage(null));
    final var assistant = assistantMessage("hi");
    final var plainUser = userMessage(textContent("hello"));
    final var documentUser = userMessage(reference(registry, 0, DocumentDelivery.CONTENT));
    final var messages = List.<Message>of(assistant, plainUser, documentUser);

    final var expanded = expander.expandMessages(messages, registry);

    assertThat(expanded).hasSize(3);
    assertThat(expanded.get(0)).isSameAs(assistant);
    assertThat(expanded.get(1)).isSameAs(plainUser);
    assertThat(expanded.get(2).id()).isEqualTo(documentUser.id());
    assertThat(((UserMessage) expanded.get(2)).content()).hasSize(2);
    assertThat(expander.expandMessages(List.of(assistant, plainUser), registry))
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

  private String renderedByAProvider(Content content) throws Exception {
    final var modelFacingJson = new ModelFacingJson(TestObjectMapperSupplier.INSTANCE);
    return switch (content) {
      case DocumentContent documentContent -> modelFacingJson.write(documentContent.document());
      case TextContent textContent -> textContent.text();
      default -> throw new IllegalArgumentException(content.toString());
    };
  }

  @Test
  void expandsAReferenceInAToolResultToTheDocumentOfTheRegistry() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.toolCall("fetch", "call-1", "m-1"));
    final var reference = DocumentReferenceContent.from(document);
    final var message = toolResultMessage(textContent("before"), reference);

    final var expanded = expander.expandMessages(List.of(message), registry);

    assertThat(expanded.getFirst().id()).isEqualTo(message.id());
    assertThat(((ToolCallResultMessage) expanded.getFirst()).results().getFirst().id())
        .isEqualTo("call-1");
    final var content = resultContent(expanded.getFirst());
    assertThat(content).hasSize(2);
    assertThat(content.get(0)).isEqualTo(textContent("before"));
    assertRebuiltDocumentIs(content.get(1), document);
  }

  @Test
  void aReferenceInAToolResultIsRenderedAsTheTagTheModelSawForTheStoredDocument() throws Exception {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.toolCall("fetch", "call-1", "m-1"));
    final var reference = DocumentReferenceContent.from(document);

    final var expanded =
        resultContent(
            expander.expandMessages(List.of(toolResultMessage(reference)), registry).getFirst());
    final var withoutRegistry =
        resultContent(
            expander
                .expandMessages(List.of(toolResultMessage(reference)), DocumentRegistry.empty())
                .getFirst());

    final var tagOfTheStoredDocument =
        renderedByAProvider(DocumentContent.documentContent(document));
    assertThat(tagOfTheStoredDocument).isEqualTo(reference.toTag());
    assertThat(renderedByAProvider(expanded.getFirst())).isEqualTo(tagOfTheStoredDocument);
    assertThat(renderedByAProvider(withoutRegistry.getFirst())).isEqualTo(tagOfTheStoredDocument);
  }

  @Test
  void aReferenceInAToolResultBecomesItsTagTextWhenTheRegistryCannotSupplyTheDocument() {
    final var reference =
        new DocumentReferenceContent(
            "unknown", "a.pdf", null, null, null, null, null, DocumentDelivery.REFERENCE);

    final var expanded =
        expander.expandMessages(List.of(toolResultMessage(reference)), DocumentRegistry.empty());

    assertThat(resultContent(expanded.getFirst())).containsExactly(tagOf(reference));
  }

  @Test
  void aReferenceInAToolResultBecomesItsTagTextWhenTheRegistryReferenceCannotBeRead() {
    final var registry =
        DocumentRegistry.of(
            List.of(
                new DocumentRegistryEntry(
                    "broken",
                    DocumentSource.toolCall("fetch", "call-1", "m-1"),
                    Map.of("camunda.document.type", "unsupported"))));
    final var reference =
        new DocumentReferenceContent(
            "broken", null, null, null, null, null, null, DocumentDelivery.REFERENCE);

    final var expanded = expander.expandMessages(List.of(toolResultMessage(reference)), registry);

    assertThat(resultContent(expanded.getFirst())).containsExactly(tagOf(reference));
  }

  @Test
  void aReferenceInAToolResultNeverGetsTheDocumentAfterItsTag() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.toolCall("fetch", "call-1", "m-1"));

    final var expanded =
        expander.expandMessages(
            List.of(
                toolResultMessage(
                    DocumentReferenceContent.from(document)
                        .withDelivery(DocumentDelivery.CONTENT))),
            registry);

    assertThat(resultContent(expanded.getFirst())).hasSize(1);
    assertRebuiltDocumentIs(resultContent(expanded.getFirst()).getFirst(), document);
  }

  @Test
  void leavesToolResultsWithoutReferencesAlone() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.toolCall("fetch", "call-1", "m-1"));
    final var messages =
        List.<Message>of(
            toolResultMessage(
                textContent("text"),
                DocumentContent.documentContent(document),
                ObjectContent.objectContent(Map.of("file", document))));

    assertThat(expander.expandMessages(messages, registry)).isSameAs(messages);
  }

  @Test
  void expandingAToolResultTwiceGivesTheSameResult() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.toolCall("fetch", "call-1", "m-1"));
    final var messages =
        List.<Message>of(toolResultMessage(DocumentReferenceContent.from(document)));

    final var once = expander.expandMessages(messages, registry);

    assertThat(expander.expandMessages(once, registry)).isSameAs(once);
  }

  @Test
  void rendersADocumentAtAnyDepthAsItsTagAndCollectsTheDocuments() {
    final var first = camundaDocument("one");
    final var second = camundaDocument("two");

    final var tagged =
        expander.renderWithTags(
            Map.of("status", 200, "files", List.of(Map.of("file", first), Map.of("file", second))));

    assertThat(tagged.tree())
        .isEqualTo(
            Map.of(
                "status",
                200,
                "files",
                List.of(
                    Map.of("file", DocumentReferenceContent.from(first).toTag()),
                    Map.of("file", DocumentReferenceContent.from(second).toTag()))));
    assertThat(tagged.documents()).containsExactly(first, second);
  }

  @Test
  void findsTheDocumentOfARegistryEntryOnly() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.toolCall("fetch", "call-1", "m-1"));

    assertRebuiltDocumentIs(
        DocumentContent.documentContent(
            expander.findDocument(registry, DocumentHandle.idFor(document)).orElseThrow()),
        document);
    assertThat(expander.findDocument(registry, "unknown")).isEmpty();
  }
}
