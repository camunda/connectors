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

import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistryEntry;
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
    return DocumentRegistries.withDocuments(
        DocumentRegistry.empty(), List.of(new IngestedDocument(document, source)));
  }

  private static UserMessage userMessage(Content... content) {
    return UserMessage.builder().content(List.of(content)).build();
  }

  private static DocumentReferenceContent reference(
      DocumentRegistry registry, int index, DocumentDelivery delivery) {
    return DocumentReferences.from(registry.entries().get(index)).withDelivery(delivery);
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
    final var registry = registryOf(document, DocumentSource.toolCall("search", "call-1"));
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
        DocumentRegistries.withDocuments(
            DocumentRegistry.empty(),
            List.of(
                new IngestedDocument(documentWithContent, DocumentSource.userMessage()),
                new IngestedDocument(referencedDocument, DocumentSource.userMessage())));
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
    final var registry = registryOf(document, DocumentSource.userMessage());
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
            DocumentId.of("unknown"), null, null, null, null, DocumentDelivery.CONTENT, null);

    final var expanded =
        expander.expandMessages(List.of(userMessage(reference)), DocumentRegistry.empty());

    assertThat(((UserMessage) expanded.getFirst()).content()).containsExactly(tagOf(reference));
  }

  @Test
  void expandingForTheHistoryAddsTheDocumentAfterEveryReference() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.userMessage());
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
        DocumentRegistries.withDocuments(
            DocumentRegistry.empty(),
            List.of(
                new IngestedDocument(external, DocumentSource.userMessage()),
                new IngestedDocument(inline, DocumentSource.userMessage())));
    final var references =
        registry.entries().stream().<Content>map(DocumentReferences::from).toList();

    final var expanded = expand(references, registry);

    assertThat(expanded).hasSize(4);
    assertRebuiltDocumentIs(expanded.get(1), external);
    assertRebuiltDocumentIs(expanded.get(3), inline);
  }

  @Test
  void doesNotAddTheDocumentAgainWhenItAlreadyFollowsTheReference() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.userMessage());
    final var reference = reference(registry, 0, DocumentDelivery.REFERENCE);
    final var content = List.<Content>of(reference, DocumentContent.documentContent(document));

    assertThat(expand(content, registry))
        .hasSize(2)
        .containsExactly(tagOf(reference), DocumentContent.documentContent(document));
  }

  @Test
  void expandsAReferenceToItsTagOnlyWhenTheRegistryCannotSupplyTheDocument() {
    final var unknown =
        new DocumentReferenceContent(
            DocumentId.of("unknown"), null, null, null, null, DocumentDelivery.REFERENCE, null);
    final var broken =
        new DocumentReferenceContent(
            DocumentId.of("broken"), null, null, null, null, DocumentDelivery.REFERENCE, null);
    final var brokenRegistry =
        DocumentRegistry.of(
            List.of(
                new DocumentRegistryEntry(
                    DocumentId.of("broken"),
                    DocumentSource.toolCall("fetch", "call-1"),
                    Map.of("camunda.document.type", "unsupported"))));

    // for the history: no entry, and an entry whose reference cannot be read
    assertThat(expand(List.of(textContent("text"), unknown), DocumentRegistry.empty()))
        .containsExactly(textContent("text"), tagOf(unknown));
    assertThat(expand(List.of(broken), brokenRegistry)).containsExactly(tagOf(broken));
    // for the model: the same in a tool result
    assertThat(
            resultContent(
                expander
                    .expandMessages(List.of(toolResultMessage(unknown)), DocumentRegistry.empty())
                    .getFirst()))
        .containsExactly(tagOf(unknown));
    assertThat(
            resultContent(
                expander
                    .expandMessages(List.of(toolResultMessage(broken)), brokenRegistry)
                    .getFirst()))
        .containsExactly(tagOf(broken));
  }

  @Test
  void leavesTagsInPlainTextAlone() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.userMessage());
    final var literal =
        "<doc id=\"%s\" fileName=\"report.pdf\" />".formatted(DocumentHandle.idFor(document));
    final var content = List.<Content>of(textContent("See " + literal), textContent(literal));

    assertThat(expand(content, registry)).isSameAs(content);
  }

  @Test
  void expandingTheResultOfAnExpansionChangesNothing() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.event());
    final var content = List.<Content>of(reference(registry, 0, DocumentDelivery.REFERENCE));
    final var messages = List.<Message>of(toolResultMessage(DocumentReferences.from(document)));

    final var once = expand(content, registry);
    final var onceForTheModel = expander.expandMessages(messages, registry);

    assertThat(expand(once, registry)).isSameAs(once);
    assertThat(expander.expandMessages(onceForTheModel, registry)).isSameAs(onceForTheModel);
  }

  @Test
  void expandsTheContentOfUserMessagesOnly() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.userMessage());
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

  @Test
  void expandsAReferenceInAToolResultToItsTagText() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.toolCall("fetch", "call-1"));
    final var reference = DocumentReferences.from(document);
    final var message = toolResultMessage(textContent("before"), reference);

    final var expanded = expander.expandMessages(List.of(message), registry);

    assertThat(expanded.getFirst().id()).isEqualTo(message.id());
    assertThat(((ToolCallResultMessage) expanded.getFirst()).results().getFirst().id())
        .isEqualTo("call-1");
    final var content = resultContent(expanded.getFirst());
    assertThat(content).hasSize(2);
    assertThat(content.get(0)).isEqualTo(textContent("before"));
    assertThat(content.get(1)).isEqualTo(tagOf(reference));
  }

  @Test
  void aReferenceInAToolResultBecomesTheShortTagOfTheDocumentOfTheRegistry() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.toolCall("fetch", "call-1"));
    final var reference = DocumentReferences.from(document);
    final var entryReference = reference(registry, 0, DocumentDelivery.REFERENCE);

    final var expanded =
        resultContent(
            expander
                .expandMessages(List.of(toolResultMessage(entryReference)), registry)
                .getFirst());
    final var withoutRegistry =
        resultContent(
            expander
                .expandMessages(List.of(toolResultMessage(reference)), DocumentRegistry.empty())
                .getFirst());

    assertThat(entryReference.toTag()).contains("toolName").contains("toolCallId");
    assertThat(expanded).containsExactly(tagOf(reference));
    assertThat(withoutRegistry).containsExactly(tagOf(reference));
  }

  @Test
  void aReferenceInAToolResultNeverGetsTheDocumentAfterItsTag() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.toolCall("fetch", "call-1"));

    final var expanded =
        expander.expandMessages(
            List.of(
                toolResultMessage(
                    DocumentReferences.from(document).withDelivery(DocumentDelivery.CONTENT))),
            registry);

    assertThat(resultContent(expanded.getFirst()))
        .containsExactly(tagOf(DocumentReferences.from(document)));
  }

  @Test
  void leavesToolResultsWithoutReferencesAlone() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.toolCall("fetch", "call-1"));
    final var messages =
        List.<Message>of(
            toolResultMessage(
                textContent("text"),
                DocumentContent.documentContent(document),
                ObjectContent.objectContent(Map.of("status", 200))));

    assertThat(expander.expandMessages(messages, registry)).isSameAs(messages);
  }

  @Test
  void replacesADocumentNestedInAnObjectOfAToolResultByItsTag() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.toolCall("fetch", "call-1"));
    final var metadata = Map.<String, Object>of("key", "value");
    final var message =
        toolResultMessage(
            new ObjectContent(
                Map.of("status", 200, "files", List.of(Map.of("file", document))), metadata));

    final var expanded = expander.expandMessages(List.of(message), registry);

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
        expander.expandMessages(
            List.of(userMessage(ObjectContent.objectContent(Map.of("file", document)))),
            DocumentRegistry.empty());

    assertThat(((UserMessage) expanded.getFirst()).content())
        .containsExactly(
            ObjectContent.objectContent(Map.of("file", DocumentReferences.from(document).toTag())));
  }

  @Test
  void leavesTheObjectsOfTheHistoryContentAlone() {
    final var content =
        List.<Content>of(ObjectContent.objectContent(Map.of("file", camundaDocument("hello"))));

    assertThat(expand(content, DocumentRegistry.empty())).isSameAs(content);
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
                    Map.of("file", DocumentReferences.from(first).toTag()),
                    Map.of("file", DocumentReferences.from(second).toTag()))));
    assertThat(tagged.documents()).containsExactly(first, second);
  }

  @Test
  void findsTheDocumentOfARegistryEntryOnly() {
    final var document = camundaDocument("hello");
    final var registry = registryOf(document, DocumentSource.toolCall("fetch", "call-1"));

    assertRebuiltDocumentIs(
        DocumentContent.documentContent(
            expander.findDocument(registry, DocumentHandle.idFor(document)).orElseThrow()),
        document);
    assertThat(expander.findDocument(registry, DocumentId.of("unknown"))).isEmpty();
  }
}
