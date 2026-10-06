/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.agenticai.aiagent.TestMessagesFixture;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentInput;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentExpander;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistryEntry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.versioning.AgentContextSchemaMigration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Golden-fixture test: a conversation in the form Camunda 8.10 persists documents (a prompt
 * document as bare content, a tag in an older attribute format followed by the content for tool
 * call, inline and event documents, an event message without an id) is loaded, its registry is
 * backfilled, its documents are normalized for the model and a further turn is composed on top of
 * it. The stored messages stay as they were.
 */
class LegacyDocumentConversationTest {

  private static final String FIXTURE =
      "/backwardcompatibility/camunda-8.10/agentContext-documents-inprocess.json";
  private static final String PROMPT_DOC_ID = "11111111-1111-4111-8111-111111111111";
  private static final String TOOL_DOC_ID = "22222222-2222-4222-8222-222222222222";

  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
      TestObjectMapperSupplier.INSTANCE;
  private final DocumentExpander expander = new DocumentExpander(objectMapper);

  private AgentContext loadContext() throws IOException {
    try (var in = getClass().getResourceAsStream(FIXTURE)) {
      JsonNode tree = objectMapper.readTree(in);
      return AgentContextSchemaMigration.migrateAndBindAgentContext(tree, objectMapper);
    }
  }

  private static List<Message> messagesOf(AgentContext context) {
    return ((InProcessConversationContext) context.conversation()).messages();
  }

  private static UserMessage user(Message message) {
    return (UserMessage) message;
  }

  @Test
  void backfillsTheRegistryFromTheStoredMessages() throws IOException {
    final var context = loadContext();
    final var messages = messagesOf(context);

    assertThat(context.documents()).isEmpty();

    final var registry = DocumentRegistryBackfill.backfill(DocumentRegistry.empty(), messages);

    assertThat(registry.entries())
        .extracting(DocumentRegistryEntry::id, DocumentRegistryEntry::source)
        .satisfiesExactly(
            prompt -> {
              assertThat(prompt.toList().get(0)).isEqualTo(PROMPT_DOC_ID);
              assertThat(prompt.toList().get(1)).isEqualTo(DocumentSource.userMessage(null));
            },
            tool -> {
              assertThat(tool.toList().get(0)).isEqualTo(TOOL_DOC_ID);
              assertThat(tool.toList().get(1))
                  .isEqualTo(DocumentSource.toolCall("Download_A_File", "call-1", null));
            },
            event -> {
              assertThat((String) event.toList().get(0)).startsWith("ext-");
              assertThat(event.toList().get(1)).isEqualTo(DocumentSource.event(null));
            });
    // the inline document stays without id and tag
    assertThat(registry.entries()).noneMatch(entry -> entry.id().startsWith("inline-"));
    assertThat(DocumentRegistryBackfill.backfill(registry, messages)).isSameAs(registry);
  }

  @Test
  void normalizesTheDocumentsForTheModelAndContinuesTheTurnWithoutChangingTheStoredMessages()
      throws IOException {
    final var context = loadContext();
    final var messages = messagesOf(context);
    // the leading system message is composed anew for every invocation and not part of the turns
    final JsonNode messagesBefore = objectMapper.valueToTree(messages.subList(1, messages.size()));

    // compose the next turn like the request handler does
    final var gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
    when(gatewayToolHandlers.transformToolCallResults(any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(gatewayToolHandlers.handlerForToolDefinition(any())).thenReturn(Optional.empty());
    final var composer = new AgentConversationTurnInputComposerImpl(gatewayToolHandlers);
    final Document newDocument =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
            .create(
                DocumentCreationRequest.from("new".getBytes(StandardCharsets.UTF_8))
                    .contentType("text/plain")
                    .fileName("new.txt")
                    .build());
    final var configuration =
        new AgentConfiguration(
            TestMessagesFixture.TEST_CHAT_MODEL,
            TestMessagesFixture.TEST_SYSTEM_PROMPT,
            null,
            null,
            null,
            null,
            null);
    final var previous = TurnReconstructor.reconstruct(messages, context.metadata());
    final var composition =
        composer.compose(
            configuration,
            context,
            previous,
            AgentInput.from(new UserPromptConfiguration("Next", List.of(newDocument)), List.of()));
    final var nextTurn = (CompositionResult.NextTurn) composition;

    final var registry =
        DocumentRegistryBackfill.backfill(DocumentRegistry.of(context.documents()), messages)
            .withDocuments(nextTurn.documents());
    final var updatedContext = context.withDocuments(registry.entries());
    final var conversation =
        AgentConversation.rehydrate(
            configuration, updatedContext, previous, null, nextTurn.messages());

    final var snapshot = conversation.window(100, expander).messages();

    assertThat(snapshot).hasSize(8);

    // prompt document: the canonical tag is inserted before the bare content
    assertThat(user(snapshot.get(0)).content())
        .satisfiesExactly(
            text ->
                assertThat(text)
                    .isEqualTo(
                        textContent("Summarize the attached report and download the invoice.")),
            tag ->
                assertThat(tag)
                    .isEqualTo(
                        textContent(
                            "<doc id=\"%s\" fileName=\"report.pdf\" contentType=\"application/pdf\" source=\"user_message\" />"
                                .formatted(PROMPT_DOC_ID))),
            content -> assertThat(content).isInstanceOf(DocumentContent.class));

    // the tool result message is not touched
    assertThat(snapshot.get(2)).isInstanceOf(ToolCallResultMessage.class);

    // tool call documents: the older tag is replaced, the inline pair stays as it was
    assertThat(user(snapshot.get(3)).content())
        .satisfiesExactly(
            preamble ->
                assertThat(preamble)
                    .isEqualTo(
                        textContent(
                            AgentConversationTurnInputComposerImpl.TOOL_CALL_DOCUMENTS_PREAMBLE)),
            tag ->
                assertThat(tag)
                    .isEqualTo(
                        textContent(
                            "<doc id=\"%s\" fileName=\"invoice.pdf\" contentType=\"application/pdf\" toolName=\"Download_A_File\" toolCallId=\"call-1\" />"
                                .formatted(TOOL_DOC_ID))),
            content -> assertThat(content).isInstanceOf(DocumentContent.class),
            inlineTag ->
                assertThat(inlineTag)
                    .isEqualTo(
                        textContent("<doc toolName=\"Download_A_File\" toolCallId=\"call-1\" />")),
            inlineContent -> assertThat(inlineContent).isInstanceOf(DocumentContent.class));

    // event document: the older external tag is replaced by the canonical one
    final var externalId =
        registry.entries().stream()
            .filter(entry -> entry.id().startsWith("ext-"))
            .findFirst()
            .orElseThrow()
            .id();
    assertThat(user(snapshot.get(5)).content().get(2))
        .isEqualTo(
            textContent(
                "<doc id=\"%s\" fileName=\"notes.txt\" source=\"event\" />".formatted(externalId)));
    assertThat(user(snapshot.get(5)).content().get(3)).isInstanceOf(DocumentContent.class);

    // the new prompt document is a tag in the stored message and tag plus content for the model
    final var newId = DocumentHandle.idFor(newDocument);
    final var storedNewMessage = nextTurn.messages().getFirst();
    assertThat(user(storedNewMessage).content()).noneMatch(DocumentContent.class::isInstance);
    final List<Content> expandedNew = user(snapshot.get(7)).content();
    assertThat(expandedNew).hasSize(3);
    assertThat(expandedNew.get(1))
        .isEqualTo(
            new TextContent(
                "<doc id=\"%s\" fileName=\"new.txt\" contentType=\"text/plain\" source=\"user_message\" />"
                    .formatted(newId),
                Map.of("documentRef", newId)));
    assertThat(expandedNew.get(2)).isInstanceOf(DocumentContent.class);

    // the stored conversation keeps the messages as they were persisted
    final var storedBefore = conversation.allMessages().subList(0, messages.size() - 1);
    final JsonNode messagesAfter = objectMapper.valueToTree(storedBefore);
    assertThat(messagesAfter).isEqualTo(messagesBefore);
    assertThat(conversation.toAgentContext().documents()).hasSize(4);
    assertThat(conversation.toAgentContext().documents().stream().map(DocumentRegistryEntry::id))
        .contains(PROMPT_DOC_ID, TOOL_DOC_ID, externalId, newId);
  }
}
