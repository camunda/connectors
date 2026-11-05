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

import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments.NewDocument;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
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
  private final DocumentExpander expander =
      new DocumentExpander(
          new DocumentRestorer(documentFactory),
          new ObjectTagRenderer(TestObjectMapperSupplier.INSTANCE));

  private Document camundaDocument(String content) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType("application/pdf")
            .fileName("report.pdf")
            .build());
  }

  private static AgentDocuments workspaceOf(Document document, DocumentOrigin origin) {
    return AgentDocuments.empty().register(document, origin);
  }

  private static UserMessage userMessage(Content... content) {
    return UserMessage.builder().content(List.of(content)).build();
  }

  private static AgentDocumentContent reference(
      AgentDocuments workspace, int index, DocumentDelivery delivery) {
    return new AgentDocumentContent(workspace.all().get(index).id(), delivery, null);
  }

  private static TextContent tagOf(AgentDocuments workspace, AgentDocumentContent reference) {
    return textContent(DocumentTags.longTag(workspace, reference.id()));
  }

  private static TextContent shortTagOf(AgentDocuments workspace, AgentDocumentContent reference) {
    return textContent(DocumentTags.shortTag(workspace, reference.id()));
  }

  private static Map<?, ?> referenceOf(Document document) {
    return TestObjectMapperSupplier.INSTANCE.convertValue(document, Map.class);
  }

  private static void assertRebuiltDocumentIs(Content content, Document expected) {
    assertThat(content)
        .isInstanceOfSatisfying(
            DocumentContent.class,
            documentContent -> {
              assertThat(AgentDocumentId.of(documentContent.document()))
                  .isEqualTo(AgentDocumentId.of(expected));
              assertThat(referenceOf(documentContent.document())).isEqualTo(referenceOf(expected));
            });
  }

  @Test
  void expandingForTheModelAddsTheDocumentOnlyAfterAReferenceWithDeliveryContent() {
    final var documentWithContent = camundaDocument("loaded");
    final var referencedDocument = camundaDocument("referenced");
    final var workspace =
        AgentDocuments.empty()
            .register(
                List.of(
                    new NewDocument(documentWithContent, DocumentOrigin.userMessage()),
                    new NewDocument(referencedDocument, DocumentOrigin.userMessage())));
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
  void leavesAMessageWithoutDocumentBlocksAlone() {
    final var messages =
        List.<Message>of(userMessage(textContent("hello")), assistantMessage("hi"));

    assertThat(expander.expandMessages(messages, AgentDocuments.empty())).isEqualTo(messages);
  }

  @Test
  void expandingForTheModelKeepsTheTagWithoutContentWhenTheDocumentIsNotInTheWorkspace() {
    final var reference =
        new AgentDocumentContent(AgentDocumentId.of("unknown"), DocumentDelivery.CONTENT, null);

    final var expanded =
        expander.expandMessages(List.of(userMessage(reference)), AgentDocuments.empty());

    assertThat(((UserMessage) expanded.getFirst()).content())
        .containsExactly(tagOf(AgentDocuments.empty(), reference));
  }

  @Test
  void doesNotAddTheDocumentAgainWhenItAlreadyFollowsTheReference() {
    final var document = camundaDocument("hello");
    final var workspace = workspaceOf(document, DocumentOrigin.userMessage());
    final var reference = reference(workspace, 0, DocumentDelivery.CONTENT);
    final var message = userMessage(reference, DocumentContent.documentContent(document));

    assertThat(
            ((UserMessage) expander.expandMessages(List.of(message), workspace).getFirst())
                .content())
        .hasSize(2)
        .containsExactly(tagOf(workspace, reference), DocumentContent.documentContent(document));
  }

  @Test
  void expandsABlockToItsTagOnlyWhenTheAgentCannotSupplyTheDocument() {
    final var unknown =
        new AgentDocumentContent(AgentDocumentId.of("unknown"), DocumentDelivery.CONTENT, null);
    final var document = camundaDocument("hello");
    final var documents = workspaceOf(document, DocumentOrigin.toolCall("fetch", "call-1"));
    final var known = reference(documents, 0, DocumentDelivery.CONTENT);
    final var failingExpander =
        new DocumentExpander(
            new DocumentRestorer(
                new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE) {
                  @Override
                  public Document resolve(
                      io.camunda.connector.api.document.DocumentReference reference) {
                    throw new IllegalStateException("store unavailable");
                  }
                }),
            new ObjectTagRenderer(TestObjectMapperSupplier.INSTANCE));

    assertThat(
            ((UserMessage)
                    expander
                        .expandMessages(
                            List.of(userMessage(textContent("text"), unknown)),
                            AgentDocuments.empty())
                        .getFirst())
                .content())
        .containsExactly(textContent("text"), tagOf(AgentDocuments.empty(), unknown));
    assertThat(
            ((UserMessage)
                    failingExpander
                        .expandMessages(List.of(userMessage(known)), documents)
                        .getFirst())
                .content())
        .containsExactly(tagOf(documents, known));
    assertThat(
            resultContent(
                expander
                    .expandMessages(List.of(toolResultMessage(unknown)), AgentDocuments.empty())
                    .getFirst()))
        .containsExactly(shortTagOf(AgentDocuments.empty(), unknown));
  }

  @Test
  void leavesTagsInPlainTextAlone() {
    final var document = camundaDocument("hello");
    final var workspace = workspaceOf(document, DocumentOrigin.userMessage());
    final var literal =
        "<doc id=\"%s\" fileName=\"report.pdf\" />".formatted(AgentDocumentId.of(document));
    final var content = List.<Content>of(textContent("See " + literal), textContent(literal));

    assertThat(
            ((UserMessage)
                    expander
                        .expandMessages(
                            List.of(userMessage(content.toArray(Content[]::new))), workspace)
                        .getFirst())
                .content())
        .isEqualTo(content);
  }

  @Test
  void expandingTheResultOfAnExpansionChangesNothing() {
    final var document = camundaDocument("hello");
    final var workspace = workspaceOf(document, DocumentOrigin.event());
    final var messages =
        List.<Message>of(
            userMessage(reference(workspace, 0, DocumentDelivery.CONTENT)),
            toolResultMessage(AgentDocumentContent.of(AgentDocumentId.of(document))));

    final var once = expander.expandMessages(messages, workspace);

    assertThat(expander.expandMessages(once, workspace)).isEqualTo(once);
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
    assertThat(expanded.get(1)).isEqualTo(plainUser);
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
    final var reference = AgentDocumentContent.of(AgentDocumentId.of(document));
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
    final var reference = AgentDocumentContent.of(AgentDocumentId.of(document));

    final var expanded =
        resultContent(
            expander.expandMessages(List.of(toolResultMessage(reference)), workspace).getFirst());
    final var withoutWorkspace =
        resultContent(
            expander
                .expandMessages(List.of(toolResultMessage(reference)), AgentDocuments.empty())
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
                    AgentDocumentContent.of(AgentDocumentId.of(document))
                        .withDelivery(DocumentDelivery.CONTENT))),
            workspace);

    assertThat(resultContent(expanded.getFirst()))
        .containsExactly(
            shortTagOf(workspace, AgentDocumentContent.of(AgentDocumentId.of(document))));
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

    assertThat(expander.expandMessages(messages, workspace)).isEqualTo(messages);
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
            AgentDocuments.empty());

    assertThat(((UserMessage) expanded.getFirst()).content())
        .containsExactly(
            ObjectContent.objectContent(Map.of("file", DocumentTags.shortTag(document))));
  }

  @Test
  void expandsTheSnapshotOfAnAgentThatTracksItsDocumentsAndKeepsItsToolDefinitions() {
    final var document = camundaDocument("hello");
    final var workspace = workspaceOf(document, DocumentOrigin.userMessage());
    final var reference = reference(workspace, 0, DocumentDelivery.CONTENT);
    final var snapshot =
        new ConversationSnapshot(
            List.of(userMessage(reference)),
            List.of(ToolDefinition.builder().name("tool").description("d").build()));

    final var expanded = expander.expand(snapshot, new DocumentHandling.Tracked(workspace));

    assertThat(expanded.toolDefinitions()).isEqualTo(snapshot.toolDefinitions());
    assertThat(((UserMessage) expanded.messages().getFirst()).content())
        .hasSize(2)
        .startsWith(tagOf(workspace, reference));
  }

  @Test
  void leavesTheSnapshotOfAnAgentThatDoesNotTrackItsDocumentsAlone() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(userMessage(DocumentContent.documentContent(camundaDocument("hello")))),
            List.of());

    assertThat(expander.expand(snapshot, new DocumentHandling.Untracked())).isSameAs(snapshot);
  }
}
