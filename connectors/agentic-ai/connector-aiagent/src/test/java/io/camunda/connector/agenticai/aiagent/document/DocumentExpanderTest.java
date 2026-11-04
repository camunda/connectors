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
import io.camunda.connector.agenticai.aiagent.model.document.DocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentWorkspace;
import io.camunda.connector.agenticai.aiagent.model.document.WorkspaceDocument;
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

  private static DocumentWorkspace workspaceOf(Document document, DocumentOrigin origin) {
    return DocumentIngestion.withDocuments(
        DocumentWorkspace.empty(), List.of(new IngestedDocument(document, origin)));
  }

  private static UserMessage userMessage(Content... content) {
    return UserMessage.builder().content(List.of(content)).build();
  }

  private static DocumentReferenceContent reference(
      DocumentWorkspace workspace, int index, DocumentDelivery delivery) {
    return new DocumentReferenceContent(workspace.entries().get(index).id(), delivery, null);
  }

  private static TextContent tagOf(
      DocumentWorkspace workspace, DocumentReferenceContent reference) {
    return textContent(DocumentTags.longTag(workspace, reference.id()));
  }

  private static TextContent shortTagOf(
      DocumentWorkspace workspace, DocumentReferenceContent reference) {
    return textContent(DocumentTags.shortTag(workspace, reference.id()));
  }

  private List<Content> expand(List<Content> content, DocumentWorkspace workspace) {
    return expander.expandContent(content, workspace);
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
  void expandsAReferenceToItsTagAndTheDocumentFromTheWorkspaceForTheHistory() {
    final var document = camundaDocument("hello");
    final var workspace = workspaceOf(document, DocumentOrigin.toolCall("search", "call-1"));
    final var reference = reference(workspace, 0, DocumentDelivery.REFERENCE);

    final var expanded = expand(List.of(textContent("preamble"), reference), workspace);

    assertThat(expanded).hasSize(3);
    assertThat(expanded.subList(0, 2))
        .containsExactly(textContent("preamble"), tagOf(workspace, reference));
    assertRebuiltDocumentIs(expanded.get(2), document);
  }

  @Test
  void expandingForTheModelAddsTheDocumentOnlyAfterAReferenceWithDeliveryContent() {
    final var documentWithContent = camundaDocument("loaded");
    final var referencedDocument = camundaDocument("referenced");
    final var workspace =
        DocumentIngestion.withDocuments(
            DocumentWorkspace.empty(),
            List.of(
                new IngestedDocument(documentWithContent, DocumentOrigin.userMessage()),
                new IngestedDocument(referencedDocument, DocumentOrigin.userMessage())));
    final var withContent = reference(workspace, 0, DocumentDelivery.CONTENT);
    final var referenced = reference(workspace, 1, DocumentDelivery.REFERENCE);
    final var message = userMessage(withContent, referenced);

    final var expanded = expander.expandMessages(List.of(message), workspace);

    final var content = ((UserMessage) expanded.getFirst()).content();
    assertThat(content).hasSize(3);
    assertThat(content.get(0)).isEqualTo(tagOf(workspace, withContent));
    assertRebuiltDocumentIs(content.get(1), documentWithContent);
    assertThat(content.get(2)).isEqualTo(tagOf(workspace, referenced));
  }

  @Test
  void expandingForTheModelTurnsAReferenceWithDeliveryReferenceIntoItsTagOnly() {
    final var document = camundaDocument("hello");
    final var workspace = workspaceOf(document, DocumentOrigin.userMessage());
    final var reference = reference(workspace, 0, DocumentDelivery.REFERENCE);
    final var message = userMessage(reference);

    final var expanded = expander.expandMessages(List.of(message), workspace);

    assertThat(((UserMessage) expanded.getFirst()).content())
        .containsExactly(tagOf(workspace, reference));
    assertThat(((UserMessage) expanded.getFirst()).id()).isEqualTo(message.id());
  }

  @Test
  void expandingForTheModelLeavesAMessageWithoutDocumentReferencesAlone() {
    final var messages =
        List.<Message>of(userMessage(textContent("hello")), assistantMessage("hi"));

    assertThat(expander.expandMessages(messages, DocumentWorkspace.empty())).isSameAs(messages);
  }

  @Test
  void expandingForTheModelKeepsTheTagWithoutContentWhenTheDocumentIsNotInTheWorkspace() {
    final var reference =
        new DocumentReferenceContent(DocumentId.of("unknown"), DocumentDelivery.CONTENT, null);

    final var expanded =
        expander.expandMessages(List.of(userMessage(reference)), DocumentWorkspace.empty());

    assertThat(((UserMessage) expanded.getFirst()).content())
        .containsExactly(tagOf(DocumentWorkspace.empty(), reference));
  }

  @Test
  void expandingForTheHistoryAddsTheDocumentAfterEveryReference() {
    final var document = camundaDocument("hello");
    final var workspace = workspaceOf(document, DocumentOrigin.userMessage());
    final var reference = reference(workspace, 0, DocumentDelivery.REFERENCE);

    final var expanded = expander.expandContent(List.of(reference), workspace);

    assertThat(expanded).hasSize(2);
    assertThat(expanded.get(0)).isEqualTo(tagOf(workspace, reference));
    assertRebuiltDocumentIs(expanded.get(1), document);
  }

  @Test
  void rebuildsAnExternalAndAnInlineDocumentFromTheirReference() {
    final var external = new ExternalDocument("https://example.com/a.pdf", "a.pdf", u -> null);
    final var inline = new InlineDocument("{\"a\":1}", "data.json", "application/json");
    final var workspace =
        DocumentIngestion.withDocuments(
            DocumentWorkspace.empty(),
            List.of(
                new IngestedDocument(external, DocumentOrigin.userMessage()),
                new IngestedDocument(inline, DocumentOrigin.userMessage())));
    final var references =
        workspace.entries().stream()
            .<Content>map(
                entry -> new DocumentReferenceContent(entry.id(), DocumentDelivery.REFERENCE, null))
            .toList();

    final var expanded = expand(references, workspace);

    assertThat(expanded).hasSize(4);
    assertRebuiltDocumentIs(expanded.get(1), external);
    assertRebuiltDocumentIs(expanded.get(3), inline);
  }

  @Test
  void doesNotAddTheDocumentAgainWhenItAlreadyFollowsTheReference() {
    final var document = camundaDocument("hello");
    final var workspace = workspaceOf(document, DocumentOrigin.userMessage());
    final var reference = reference(workspace, 0, DocumentDelivery.REFERENCE);
    final var content = List.<Content>of(reference, DocumentContent.documentContent(document));

    assertThat(expand(content, workspace))
        .hasSize(2)
        .containsExactly(tagOf(workspace, reference), DocumentContent.documentContent(document));
  }

  @Test
  void expandsAReferenceToItsTagOnlyWhenTheWorkspaceCannotSupplyTheDocument() {
    final var unknown =
        new DocumentReferenceContent(DocumentId.of("unknown"), DocumentDelivery.REFERENCE, null);
    final var broken =
        new DocumentReferenceContent(DocumentId.of("broken"), DocumentDelivery.REFERENCE, null);
    final var brokenWorkspace =
        DocumentWorkspace.of(
            List.of(
                new WorkspaceDocument(
                    DocumentId.of("broken"),
                    DocumentOrigin.toolCall("fetch", "call-1"),
                    Map.of("camunda.document.type", "unsupported"))));

    // for the history: no entry, and an entry whose reference cannot be read
    assertThat(expand(List.of(textContent("text"), unknown), DocumentWorkspace.empty()))
        .containsExactly(textContent("text"), tagOf(DocumentWorkspace.empty(), unknown));
    assertThat(expand(List.of(broken), brokenWorkspace))
        .containsExactly(tagOf(brokenWorkspace, broken));
    // for the model: the same in a tool result
    assertThat(
            resultContent(
                expander
                    .expandMessages(List.of(toolResultMessage(unknown)), DocumentWorkspace.empty())
                    .getFirst()))
        .containsExactly(shortTagOf(DocumentWorkspace.empty(), unknown));
    assertThat(
            resultContent(
                expander
                    .expandMessages(List.of(toolResultMessage(broken)), brokenWorkspace)
                    .getFirst()))
        .containsExactly(shortTagOf(brokenWorkspace, broken));
  }

  @Test
  void leavesTagsInPlainTextAlone() {
    final var document = camundaDocument("hello");
    final var workspace = workspaceOf(document, DocumentOrigin.userMessage());
    final var literal =
        "<doc id=\"%s\" fileName=\"report.pdf\" />".formatted(DocumentHandle.idFor(document));
    final var content = List.<Content>of(textContent("See " + literal), textContent(literal));

    assertThat(expand(content, workspace)).isSameAs(content);
  }

  @Test
  void expandingTheResultOfAnExpansionChangesNothing() {
    final var document = camundaDocument("hello");
    final var workspace = workspaceOf(document, DocumentOrigin.event());
    final var content = List.<Content>of(reference(workspace, 0, DocumentDelivery.REFERENCE));
    final var messages = List.<Message>of(toolResultMessage(DocumentReferences.from(document)));

    final var once = expand(content, workspace);
    final var onceForTheModel = expander.expandMessages(messages, workspace);

    assertThat(expand(once, workspace)).isSameAs(once);
    assertThat(expander.expandMessages(onceForTheModel, workspace)).isSameAs(onceForTheModel);
  }

  @Test
  void expandsTheContentOfUserMessagesOnly() {
    final var document = camundaDocument("hello");
    final var workspace = workspaceOf(document, DocumentOrigin.userMessage());
    final var assistant = assistantMessage("hi");
    final var plainUser = userMessage(textContent("hello"));
    final var documentUser = userMessage(reference(workspace, 0, DocumentDelivery.CONTENT));
    final var messages = List.<Message>of(assistant, plainUser, documentUser);

    final var expanded = expander.expandMessages(messages, workspace);

    assertThat(expanded).hasSize(3);
    assertThat(expanded.get(0)).isSameAs(assistant);
    assertThat(expanded.get(1)).isSameAs(plainUser);
    assertThat(expanded.get(2).id()).isEqualTo(documentUser.id());
    assertThat(((UserMessage) expanded.get(2)).content()).hasSize(2);
    assertThat(expander.expandMessages(List.of(assistant, plainUser), workspace))
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
    final var workspace = workspaceOf(document, DocumentOrigin.toolCall("fetch", "call-1"));
    final var reference = DocumentReferences.from(document);
    final var message = toolResultMessage(textContent("before"), reference);

    final var expanded = expander.expandMessages(List.of(message), workspace);

    assertThat(expanded.getFirst().id()).isEqualTo(message.id());
    assertThat(((ToolCallResultMessage) expanded.getFirst()).results().getFirst().id())
        .isEqualTo("call-1");
    final var content = resultContent(expanded.getFirst());
    assertThat(content).hasSize(2);
    assertThat(content.get(0)).isEqualTo(textContent("before"));
    assertThat(content.get(1)).isEqualTo(shortTagOf(workspace, reference));
  }

  @Test
  void aReferenceInAToolResultBecomesTheShortTagOfTheDocumentOfTheWorkspace() {
    final var document = camundaDocument("hello");
    final var workspace = workspaceOf(document, DocumentOrigin.toolCall("fetch", "call-1"));
    final var reference = DocumentReferences.from(document);

    final var expanded =
        resultContent(
            expander.expandMessages(List.of(toolResultMessage(reference)), workspace).getFirst());
    final var withoutWorkspace =
        resultContent(
            expander
                .expandMessages(List.of(toolResultMessage(reference)), DocumentWorkspace.empty())
                .getFirst());

    assertThat(DocumentTags.longTag(workspace, reference.id()))
        .contains("toolName")
        .contains("toolCallId");
    assertThat(expanded).containsExactly(textContent(DocumentTags.shortTag(document)));
    assertThat(withoutWorkspace)
        .containsExactly(textContent(DocumentTags.idOnlyTag(reference.id())));
  }

  @Test
  void aReferenceInAToolResultNeverGetsTheDocumentAfterItsTag() {
    final var document = camundaDocument("hello");
    final var workspace = workspaceOf(document, DocumentOrigin.toolCall("fetch", "call-1"));

    final var expanded =
        expander.expandMessages(
            List.of(
                toolResultMessage(
                    DocumentReferences.from(document).withDelivery(DocumentDelivery.CONTENT))),
            workspace);

    assertThat(resultContent(expanded.getFirst()))
        .containsExactly(shortTagOf(workspace, DocumentReferences.from(document)));
  }

  @Test
  void leavesToolResultsWithoutReferencesAlone() {
    final var document = camundaDocument("hello");
    final var workspace = workspaceOf(document, DocumentOrigin.toolCall("fetch", "call-1"));
    final var messages =
        List.<Message>of(
            toolResultMessage(
                textContent("text"),
                DocumentContent.documentContent(document),
                ObjectContent.objectContent(Map.of("status", 200))));

    assertThat(expander.expandMessages(messages, workspace)).isSameAs(messages);
  }

  @Test
  void replacesADocumentNestedInAnObjectOfAToolResultByItsTag() {
    final var document = camundaDocument("hello");
    final var workspace = workspaceOf(document, DocumentOrigin.toolCall("fetch", "call-1"));
    final var metadata = Map.<String, Object>of("key", "value");
    final var message =
        toolResultMessage(
            new ObjectContent(
                Map.of("status", 200, "files", List.of(Map.of("file", document))), metadata));

    final var expanded = expander.expandMessages(List.of(message), workspace);

    assertThat(resultContent(expanded.getFirst()))
        .containsExactly(
            new ObjectContent(
                Map.of(
                    "status",
                    200,
                    "files",
                    List.of(Map.of("file", DocumentTags.shortTag(document)))),
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
            DocumentWorkspace.empty());

    assertThat(((UserMessage) expanded.getFirst()).content())
        .containsExactly(
            ObjectContent.objectContent(Map.of("file", DocumentTags.shortTag(document))));
  }

  @Test
  void leavesTheObjectsOfTheHistoryContentAlone() {
    final var content =
        List.<Content>of(ObjectContent.objectContent(Map.of("file", camundaDocument("hello"))));

    assertThat(expand(content, DocumentWorkspace.empty())).isSameAs(content);
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
                    Map.of("file", DocumentTags.shortTag(first)),
                    Map.of("file", DocumentTags.shortTag(second)))));
    assertThat(tagged.documents()).containsExactly(first, second);
  }

  @Test
  void findsTheDocumentOfAWorkspaceDocumentOnly() {
    final var document = camundaDocument("hello");
    final var workspace = workspaceOf(document, DocumentOrigin.toolCall("fetch", "call-1"));

    assertRebuiltDocumentIs(
        DocumentContent.documentContent(
            expander.findDocument(workspace, DocumentHandle.idFor(document)).orElseThrow()),
        document);
    assertThat(expander.findDocument(workspace, DocumentId.of("unknown"))).isEmpty();
  }
}
