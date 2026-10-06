/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.InlineDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
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
  void addsTheDocumentFromTheRegistryAfterAFlaggedTag() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.toolCall("search", "call-1", "m-1"));
    final var tag = DocumentReferenceXmlTag.from(registry.entries().getFirst()).toContent();

    final var expanded = expand(List.of(textContent("preamble"), tag), registry);

    assertThat(expanded).hasSize(3);
    assertThat(expanded.subList(0, 2)).containsExactly(textContent("preamble"), tag);
    assertRebuiltDocumentIs(expanded.get(2), document);
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
    final var tags =
        registry.entries().stream()
            .<Content>map(entry -> DocumentReferenceXmlTag.from(entry).toContent())
            .toList();

    final var expanded = expand(tags, registry);

    assertThat(expanded).hasSize(4);
    assertRebuiltDocumentIs(expanded.get(1), external);
    assertRebuiltDocumentIs(expanded.get(3), inline);
  }

  @Test
  void leavesAFlaggedTagAloneWhenItsDocumentAlreadyFollows() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.userMessage(null));
    final var content =
        List.<Content>of(
            DocumentReferenceXmlTag.from(registry.entries().getFirst()).toContent(),
            DocumentContent.documentContent(document));

    assertThat(expand(content, registry)).isSameAs(content);
  }

  @Test
  void leavesAFlaggedTagWithoutRegistryEntryAlone() {
    final var tag =
        new DocumentReferenceXmlTag("unknown", null, null, null, null, null).toContent();
    final var content = List.<Content>of(textContent("text"), tag);

    assertThat(expand(content, DocumentRegistry.empty())).isSameAs(content);
  }

  @Test
  void leavesAFlaggedTagAloneWhenTheReferenceCannotBeRead() {
    final var registry =
        DocumentRegistry.of(
            List.of(
                new DocumentRegistryEntry(
                    "broken",
                    DocumentSource.userMessage(null),
                    Map.of("camunda.document.type", "unsupported"))));
    final var content =
        List.<Content>of(
            new DocumentReferenceXmlTag("broken", null, null, null, null, null).toContent());

    assertThat(expand(content, registry)).isSameAs(content);
  }

  @Test
  void onlyExpandsFlaggedTags() {
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
    final var content =
        List.<Content>of(DocumentReferenceXmlTag.from(registry.entries().getFirst()).toContent());

    final var once = expand(content, registry);

    assertThat(expand(content, registry)).hasSameSizeAs(once);
    assertThat(expand(content, registry).getFirst()).isEqualTo(once.getFirst());
    assertThat(referenceOf(((DocumentContent) expand(content, registry).getLast()).document()))
        .isEqualTo(referenceOf(((DocumentContent) once.getLast()).document()));
    assertThat(expand(once, registry)).isSameAs(once);
  }

  @Test
  void replacesAnOlderCamundaTagByTheCanonicalTagOfTheRegistryEntry() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.toolCall("search", "call-1", "m-1"));
    final var id = DocumentHandle.idFor(document);
    final var olderTag =
        "<doc toolName=\"search\" toolCallId=\"call-1\" documentId=\"%s\" storeId=\"in-memory\" contentType=\"application/pdf\" fileName=\"report.pdf\" />"
            .formatted(id);

    final var expanded =
        expand(List.of(textContent(olderTag), DocumentContent.documentContent(document)), registry);

    assertThat(expanded)
        .containsExactly(
            textContent(
                "<doc id=\"%s\" fileName=\"report.pdf\" contentType=\"application/pdf\" toolName=\"search\" toolCallId=\"call-1\" />"
                    .formatted(id)),
            DocumentContent.documentContent(document));
  }

  @Test
  void replacesAnOlderExternalTagByTheCanonicalTagOfTheDocument() {
    final var external = new ExternalDocument("https://example.com/a.pdf", "a.pdf", u -> null);
    final var olderTag =
        "<doc toolName=\"download\" toolCallId=\"call-2\" url=\"https://example.com/a.pdf\" name=\"a.pdf\" />";

    final var expanded =
        expand(
            List.of(textContent(olderTag), DocumentContent.documentContent(external)),
            DocumentRegistry.empty());

    assertThat(expanded)
        .containsExactly(
            textContent(
                "<doc id=\"%s\" fileName=\"a.pdf\" />".formatted(DocumentHandle.idFor(external))),
            DocumentContent.documentContent(external));
  }

  @Test
  void insertsTheCanonicalTagBeforeADocumentWithoutTag() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.userMessage("m-1"));

    final var expanded =
        expand(
            List.of(textContent("Look at this"), DocumentContent.documentContent(document)),
            registry);

    assertThat(expanded)
        .containsExactly(
            textContent("Look at this"),
            textContent(
                "<doc id=\"%s\" fileName=\"report.pdf\" contentType=\"application/pdf\" source=\"user_message\" />"
                    .formatted(DocumentHandle.idFor(document))),
            DocumentContent.documentContent(document));
  }

  @Test
  void leavesInlineDocumentsAsTheyAre() {
    final var inline = new InlineDocument("{\"a\":1}", "data.json", "application/json");
    final var content =
        List.<Content>of(
            textContent("<doc toolName=\"gen\" toolCallId=\"call-1\" />"),
            DocumentContent.documentContent(inline),
            DocumentContent.documentContent(inline));

    assertThat(expand(content, DocumentRegistry.empty())).isSameAs(content);
  }

  @Test
  void leavesATagThatDoesNotNameTheFollowingDocumentAlone() {
    final var document = camundaDocument("hello");
    final var content =
        List.<Content>of(
            textContent("<doc documentId=\"another\" storeId=\"in-memory\" />"),
            DocumentContent.documentContent(document));

    assertThat(expand(content, DocumentRegistry.empty())).isSameAs(content);
  }

  @Test
  void onlyChangesUserMessages() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.userMessage(null));
    final var assistant = assistantMessage("hi");
    final var plainUser = userMessage(textContent("hello"));
    final var documentUser =
        userMessage(DocumentReferenceXmlTag.from(registry.entries().getFirst()).toContent());
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
}
