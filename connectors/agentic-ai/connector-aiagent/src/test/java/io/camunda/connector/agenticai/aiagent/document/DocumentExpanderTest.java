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
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.testutil.TestDocuments;
import io.camunda.connector.agenticai.aiagent.model.testutil.TestObjectMapperSupplier;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.testutil.TestDocumentHandles;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentExpanderTest {

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
  private final DocumentExpander expander =
      new DocumentExpander(new ObjectHandleRenderer(TestObjectMapperSupplier.INSTANCE));

  private Document camundaDocument(String content) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType("application/pdf")
            .fileName("report.pdf")
            .build());
  }

  private static AgentDocuments agentDocumentsOf(Document document, AgentDocumentOrigin origin) {
    return AgentDocuments.empty().register(document, origin);
  }

  private static UserMessage userMessage(Content... content) {
    return UserMessage.builder().content(List.of(content)).build();
  }

  private static AgentDocumentContent reference(
      AgentDocuments agentDocuments, int index, DocumentDelivery delivery) {
    return new AgentDocumentContent(agentDocuments.all().get(index).id(), delivery, null);
  }

  private static TextContent handleOf(
      AgentDocuments agentDocuments, AgentDocumentContent reference) {
    return textContent(TestDocumentHandles.handleText(agentDocuments, reference.id()));
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
  void rendersTheExactHandleTextInAUserMessageAndAToolResult() {
    final var document = TestDocuments.createDocument("data", "r \"1\".pdf");
    final var agentDocuments =
        agentDocumentsOf(document, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID));
    final var block =
        new AgentDocumentContent(AgentDocumentId.of(document), DocumentDelivery.REFERENCE, null);
    final var expectedText =
        "{\"type\":\"camunda.agenticai.document\",\"id\":\"%s\",\"fileName\":\"r \\\"1\\\".pdf\",\"contentType\":\"text/plain\"}"
            .formatted(AgentDocumentId.of(document));

    final var expanded =
        expander.expandMessages(
            List.of(userMessage(block), toolResultMessage(block)), agentDocuments, documentFactory);

    assertThat(((UserMessage) expanded.get(0)).content())
        .containsExactly(textContent(expectedText));
    assertThat(resultContent(expanded.get(1))).containsExactly(textContent(expectedText));
  }

  @Test
  void expandingForTheModelAddsTheDocumentOnlyAfterAReferenceWithDeliveryContent() {
    final var documentWithContent = camundaDocument("loaded");
    final var referencedDocument = camundaDocument("referenced");
    final var agentDocuments =
        AgentDocuments.empty()
            .register(
                List.of(
                    AgentDocument.of(
                        documentWithContent,
                        AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID)),
                    AgentDocument.of(
                        referencedDocument,
                        AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID))));
    final var withContent = reference(agentDocuments, 0, DocumentDelivery.CONTENT);
    final var referenced = reference(agentDocuments, 1, DocumentDelivery.REFERENCE);
    final var message = userMessage(withContent, referenced);

    final var expanded = expander.expandMessages(List.of(message), agentDocuments, documentFactory);

    final var content = ((UserMessage) expanded.getFirst()).content();
    assertThat(content).hasSize(3);
    assertThat(content.get(0)).isEqualTo(handleOf(agentDocuments, withContent));
    assertRebuiltDocumentIs(content.get(1), documentWithContent);
    assertThat(content.get(2)).isEqualTo(handleOf(agentDocuments, referenced));
  }

  @Test
  void restoresTheDocumentWithTheDocumentFactoryOfTheJob() {
    final var document = camundaDocument("tenant");
    final var agentDocuments =
        agentDocumentsOf(document, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID));
    final var block = reference(agentDocuments, 0, DocumentDelivery.CONTENT);
    final var restored = new ArrayList<DocumentReference>();
    final var jobDocumentFactory =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE) {
          @Override
          public Document resolve(DocumentReference reference) {
            restored.add(reference);
            return super.resolve(reference);
          }
        };

    final var snapshot =
        expander.expand(
            new ConversationSnapshot(List.of(userMessage(block)), List.of()),
            new DocumentHandling.Tracked(agentDocuments),
            jobDocumentFactory);

    assertThat(restored)
        .singleElement()
        .extracting(AgentDocumentId::of)
        .isEqualTo(AgentDocumentId.of(document));
    assertRebuiltDocumentIs(
        ((UserMessage) snapshot.messages().getFirst()).content().get(1), document);
  }

  @Test
  void expandingForTheModelTurnsAReferenceWithDeliveryReferenceIntoItsHandleOnly() {
    final var document = camundaDocument("hello");
    final var agentDocuments =
        agentDocumentsOf(document, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID));
    final var reference = reference(agentDocuments, 0, DocumentDelivery.REFERENCE);
    final var message = userMessage(reference);

    final var expanded = expander.expandMessages(List.of(message), agentDocuments, documentFactory);

    assertThat(((UserMessage) expanded.getFirst()).content())
        .containsExactly(handleOf(agentDocuments, reference));
    assertThat(((UserMessage) expanded.getFirst()).id()).isEqualTo(message.id());
  }

  @Test
  void leavesAMessageWithoutDocumentBlocksAlone() {
    final var messages =
        List.<Message>of(userMessage(textContent("hello")), assistantMessage("hi"));

    assertThat(expander.expandMessages(messages, AgentDocuments.empty(), documentFactory))
        .isEqualTo(messages);
  }

  @Test
  void expandingForTheModelKeepsTheHandleWithoutContentWhenTheDocumentIsNotKnownToTheAgent() {
    final var reference =
        new AgentDocumentContent(AgentDocumentId.of("unknown"), DocumentDelivery.CONTENT, null);

    final var expanded =
        expander.expandMessages(
            List.of(userMessage(reference)), AgentDocuments.empty(), documentFactory);

    assertThat(((UserMessage) expanded.getFirst()).content())
        .containsExactly(handleOf(AgentDocuments.empty(), reference));
  }

  @Test
  void expandsABlockOfADocumentTheAgentDoesNotKnowToItsHandleOnly() {
    final var unknown =
        new AgentDocumentContent(AgentDocumentId.of("unknown"), DocumentDelivery.CONTENT, null);

    assertThat(
            ((UserMessage)
                    expander
                        .expandMessages(
                            List.of(userMessage(textContent("text"), unknown)),
                            AgentDocuments.empty(),
                            documentFactory)
                        .getFirst())
                .content())
        .containsExactly(textContent("text"), handleOf(AgentDocuments.empty(), unknown));
    assertThat(
            resultContent(
                expander
                    .expandMessages(
                        List.of(toolResultMessage(unknown)),
                        AgentDocuments.empty(),
                        documentFactory)
                    .getFirst()))
        .containsExactly(handleOf(AgentDocuments.empty(), unknown));
  }

  @Test
  void leavesAHandleInPlainTextAlone() {
    final var document = camundaDocument("hello");
    final var agentDocuments =
        agentDocumentsOf(document, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID));
    final var literal =
        "{\"type\":\"camunda.agenticai.document\",\"id\":\"%s\"}"
            .formatted(AgentDocumentId.of(document));
    final var content = List.<Content>of(textContent("See " + literal), textContent(literal));

    assertThat(
            ((UserMessage)
                    expander
                        .expandMessages(
                            List.of(userMessage(content.toArray(Content[]::new))),
                            agentDocuments,
                            documentFactory)
                        .getFirst())
                .content())
        .isEqualTo(content);
  }

  @Test
  void expandingTheResultOfAnExpansionChangesNothing() {
    final var document = camundaDocument("hello");
    final var agentDocuments =
        agentDocumentsOf(document, AgentDocumentOrigin.event(TestDocuments.MESSAGE_ID));
    final var messages =
        List.<Message>of(
            userMessage(reference(agentDocuments, 0, DocumentDelivery.CONTENT)),
            toolResultMessage(AgentDocumentContent.of(AgentDocumentId.of(document))));

    final var once = expander.expandMessages(messages, agentDocuments, documentFactory);

    assertThat(expander.expandMessages(once, agentDocuments, documentFactory)).isEqualTo(once);
  }

  @Test
  void expandsTheContentOfUserMessagesOnly() {
    final var document = camundaDocument("hello");
    final var agentDocuments =
        agentDocumentsOf(document, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID));
    final var assistant = assistantMessage("hi");
    final var plainUser = userMessage(textContent("hello"));
    final var documentUser = userMessage(reference(agentDocuments, 0, DocumentDelivery.CONTENT));
    final var messages = List.<Message>of(assistant, plainUser, documentUser);

    final var expanded = expander.expandMessages(messages, agentDocuments, documentFactory);

    assertThat(expanded).hasSize(3);
    assertThat(expanded.get(0)).isSameAs(assistant);
    assertThat(expanded.get(1)).isEqualTo(plainUser);
    assertThat(expanded.get(2).id()).isEqualTo(documentUser.id());
    assertThat(((UserMessage) expanded.get(2)).content()).hasSize(2);
    assertThat(
            expander.expandMessages(List.of(assistant, plainUser), agentDocuments, documentFactory))
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
  void expandsAReferenceInAToolResultToItsHandleText() {
    final var document = camundaDocument("hello");
    final var agentDocuments =
        agentDocumentsOf(document, AgentDocumentOrigin.toolCall("fetch", "call-1"));
    final var reference = AgentDocumentContent.of(AgentDocumentId.of(document));
    final var message = toolResultMessage(textContent("before"), reference);

    final var expanded = expander.expandMessages(List.of(message), agentDocuments, documentFactory);

    assertThat(expanded.getFirst().id()).isEqualTo(message.id());
    assertThat(((ToolCallResultMessage) expanded.getFirst()).results().getFirst().id())
        .isEqualTo("call-1");
    final var content = resultContent(expanded.getFirst());
    assertThat(content).hasSize(2);
    assertThat(content.get(0)).isEqualTo(textContent("before"));
    assertThat(content.get(1)).isEqualTo(handleOf(agentDocuments, reference));
  }

  @Test
  void aBlockInAToolResultBecomesTheHandleOfTheDocumentOfTheAgentDocuments() {
    final var document = camundaDocument("hello");
    final var agentDocuments =
        agentDocumentsOf(document, AgentDocumentOrigin.toolCall("fetch", "call-1"));
    final var reference = AgentDocumentContent.of(AgentDocumentId.of(document));

    final var expanded =
        resultContent(
            expander
                .expandMessages(
                    List.of(toolResultMessage(reference)), agentDocuments, documentFactory)
                .getFirst());
    final var withoutAgentDocuments =
        resultContent(
            expander
                .expandMessages(
                    List.of(toolResultMessage(reference)), AgentDocuments.empty(), documentFactory)
                .getFirst());

    assertThat(expanded).containsExactly(textContent(TestDocumentHandles.handleText(document)));
    assertThat(withoutAgentDocuments)
        .containsExactly(textContent(TestDocumentHandles.unknownHandleText(reference.id())));
  }

  @Test
  void aBlockInAToolResultNeverGetsTheDocumentAfterItsHandle() {
    final var document = camundaDocument("hello");
    final var agentDocuments =
        agentDocumentsOf(document, AgentDocumentOrigin.toolCall("fetch", "call-1"));

    final var expanded =
        expander.expandMessages(
            List.of(
                toolResultMessage(
                    AgentDocumentContent.of(AgentDocumentId.of(document))
                        .withDelivery(DocumentDelivery.CONTENT))),
            agentDocuments,
            documentFactory);

    assertThat(resultContent(expanded.getFirst()))
        .containsExactly(
            handleOf(agentDocuments, AgentDocumentContent.of(AgentDocumentId.of(document))));
  }

  @Test
  void leavesToolResultsWithoutReferencesAlone() {
    final var document = camundaDocument("hello");
    final var agentDocuments =
        agentDocumentsOf(document, AgentDocumentOrigin.toolCall("fetch", "call-1"));
    final var messages =
        List.<Message>of(
            toolResultMessage(
                textContent("text"),
                DocumentContent.documentContent(document),
                ObjectContent.objectContent(Map.of("status", 200))));

    assertThat(expander.expandMessages(messages, agentDocuments, documentFactory))
        .isEqualTo(messages);
  }

  @Test
  void replacesADocumentNestedInAnObjectOfAToolResultByItsHandle() {
    final var document = camundaDocument("hello");
    final var agentDocuments =
        agentDocumentsOf(document, AgentDocumentOrigin.toolCall("fetch", "call-1"));
    final var metadata = Map.<String, Object>of("key", "value");
    final var message =
        toolResultMessage(
            new ObjectContent(
                Map.of("status", 200, "files", List.of(Map.of("file", document))), metadata));

    final var expanded = expander.expandMessages(List.of(message), agentDocuments, documentFactory);

    assertThat(resultContent(expanded.getFirst()))
        .containsExactly(
            new ObjectContent(
                Map.of(
                    "status",
                    200,
                    "files",
                    List.of(Map.of("file", TestDocumentHandles.handleObject(document)))),
                metadata));
    assertThat(resultContent(message).getFirst())
        .isInstanceOfSatisfying(
            ObjectContent.class,
            stored ->
                assertThat(((Map<?, ?>) stored.content()).get("files"))
                    .isEqualTo(List.of(Map.of("file", document))));
  }

  @Test
  void replacesADocumentNestedInAnObjectOfAUserMessageByItsHandle() {
    final var document = camundaDocument("hello");

    final var expanded =
        expander.expandMessages(
            List.of(userMessage(ObjectContent.objectContent(Map.of("file", document)))),
            AgentDocuments.empty(),
            documentFactory);

    assertThat(((UserMessage) expanded.getFirst()).content())
        .containsExactly(
            ObjectContent.objectContent(
                Map.of("file", TestDocumentHandles.handleObject(document))));
  }

  @Test
  void expandsTheSnapshotOfAnAgentThatTracksItsDocumentsAndKeepsItsToolDefinitions() {
    final var document = camundaDocument("hello");
    final var agentDocuments =
        agentDocumentsOf(document, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID));
    final var reference = reference(agentDocuments, 0, DocumentDelivery.CONTENT);
    final var snapshot =
        new ConversationSnapshot(
            List.of(userMessage(reference)),
            List.of(ToolDefinition.builder().name("tool").description("d").build()));

    final var expanded =
        expander.expand(snapshot, new DocumentHandling.Tracked(agentDocuments), documentFactory);

    assertThat(expanded.toolDefinitions()).isEqualTo(snapshot.toolDefinitions());
    assertThat(((UserMessage) expanded.messages().getFirst()).content())
        .hasSize(2)
        .startsWith(handleOf(agentDocuments, reference));
  }

  @Test
  void leavesTheSnapshotOfAnAgentThatDoesNotTrackItsDocumentsAlone() {
    final var snapshot =
        new ConversationSnapshot(
            List.of(userMessage(DocumentContent.documentContent(camundaDocument("hello")))),
            List.of());

    assertThat(expander.expand(snapshot, new DocumentHandling.Untracked(), documentFactory))
        .isSameAs(snapshot);
  }
}
