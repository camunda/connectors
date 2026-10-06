/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.toolCallResultMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistryEntry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.ExternalDocument;
import io.camunda.connector.runtime.core.document.InlineDocument;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentRegistryBackfillTest {

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  private Document document(String content, String fileName) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType("text/plain")
            .fileName(fileName)
            .build());
  }

  private static DocumentSource sourceOf(DocumentRegistry registry, Document document) {
    return registry.findById(DocumentHandle.idFor(document)).orElseThrow().source();
  }

  @Test
  void addsToolResultDocumentsWithTheToolAndTheToolCallResultMessage() {
    final var toolDoc = document("tool", "tool.txt");
    final var nestedDoc = document("nested", "nested.txt");
    final var results =
        toolCallResultMessage(
            List.of(
                ToolCallResult.builder()
                    .id("call-1")
                    .name("search")
                    .content(
                        new LinkedHashMap<>(
                            Map.of("result", List.of(toolDoc, Map.of("d", nestedDoc)))))
                    .build()));

    final var registry =
        DocumentRegistryBackfill.backfill(
            DocumentRegistry.empty(), List.<Message>of(assistantMessage("calling"), results));

    final var expected = DocumentSource.toolCall("search", "call-1", results.id().toString());
    assertThat(registry.entries())
        .extracting(DocumentRegistryEntry::id, DocumentRegistryEntry::source)
        .containsExactly(
            org.assertj.core.api.Assertions.tuple(DocumentHandle.idFor(toolDoc), expected),
            org.assertj.core.api.Assertions.tuple(DocumentHandle.idFor(nestedDoc), expected));
  }

  @Test
  void addsDocumentsOfTheToolCallDocumentMessageThatNoToolResultHoldsFromItsTag() {
    final var extracted = document("extracted", "extracted.txt");
    final var results =
        toolCallResultMessage(
            List.of(ToolCallResult.builder().id("call-1").name("mcp").content("done").build()));
    final var documentMessage =
        UserMessage.builder()
            .content(
                List.of(
                    textContent(
                        "Documents extracted from tool calls (<doc /> tag + content pair):"),
                    textContent(
                        "<doc toolName=\"mcp\" toolCallId=\"call-1\" documentId=\"%s\" storeId=\"in-memory\" />"
                            .formatted(DocumentHandle.idFor(extracted))),
                    DocumentContent.documentContent(extracted)))
            .metadata(Map.of(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true))
            .build();

    final var registry =
        DocumentRegistryBackfill.backfill(
            DocumentRegistry.empty(), List.of(results, documentMessage));

    assertThat(sourceOf(registry, extracted))
        .isEqualTo(DocumentSource.toolCall("mcp", "call-1", results.id().toString()));
  }

  @Test
  void aToolResultDocumentKeepsItsToolSourceWhenTheDocumentMessageRepeatsIt() {
    final var toolDoc = document("tool", "tool.txt");
    final var results =
        toolCallResultMessage(
            List.of(
                ToolCallResult.builder()
                    .id("call-1")
                    .name("search")
                    .content(Map.of("file", toolDoc))
                    .build()));
    final var documentMessage =
        UserMessage.builder()
            .content(
                List.of(
                    textContent(
                        "Documents extracted from tool calls (<doc /> tag + content pair):"),
                    textContent("<doc toolName=\"other\" toolCallId=\"call-9\" />"),
                    DocumentContent.documentContent(toolDoc)))
            .metadata(Map.of(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true))
            .build();

    final var registry =
        DocumentRegistryBackfill.backfill(
            DocumentRegistry.empty(), List.of(results, documentMessage));

    assertThat(registry.size()).isEqualTo(1);
    assertThat(sourceOf(registry, toolDoc))
        .isEqualTo(DocumentSource.toolCall("search", "call-1", results.id().toString()));
  }

  @Test
  void addsEventDocumentsAfterThePreambleAndUserPromptDocumentsOtherwise() {
    final var promptDoc = document("prompt", "prompt.txt");
    final var eventDoc = document("event", "event.txt");
    final var prompt =
        userMessage(List.of(textContent("hello"), DocumentContent.documentContent(promptDoc)));
    final var event =
        userMessage(
            List.of(
                textContent("event"),
                textContent(AgentConversationTurnInputComposerImpl.EVENT_DOCUMENTS_PREAMBLE),
                textContent("<doc toolName=\"x\" />"),
                DocumentContent.documentContent(eventDoc)));

    final var registry =
        DocumentRegistryBackfill.backfill(DocumentRegistry.empty(), List.of(prompt, event));

    assertThat(sourceOf(registry, promptDoc))
        .isEqualTo(DocumentSource.userMessage(prompt.id().toString()));
    assertThat(sourceOf(registry, eventDoc)).isEqualTo(DocumentSource.event(event.id().toString()));
  }

  @Test
  void addsExternalDocumentsAndSkipsInlineDocuments() {
    final var external = new ExternalDocument("https://example.com/a.pdf", "a.pdf", u -> null);
    final var inline = new InlineDocument("{\"a\":1}", "data.json", "application/json");
    final var prompt =
        userMessage(
            List.of(
                DocumentContent.documentContent(external),
                DocumentContent.documentContent(inline)));

    final var registry =
        DocumentRegistryBackfill.backfill(DocumentRegistry.empty(), List.of(prompt));

    assertThat(registry.entries())
        .extracting(DocumentRegistryEntry::id)
        .containsExactly(DocumentHandle.idFor(external));
  }

  @Test
  void doesNotAddInlineDocumentsOfToolResults() {
    final var inline = new InlineDocument("{\"a\":1}", "data.json", "application/json");
    final var results =
        toolCallResultMessage(
            List.of(
                ToolCallResult.builder()
                    .id("call-1")
                    .name("gen")
                    .content(Map.of("file", inline))
                    .build()));

    assertThat(
            DocumentRegistryBackfill.backfill(DocumentRegistry.empty(), List.of(results)).isEmpty())
        .isTrue();
  }

  @Test
  void leavesKnownDocumentsAndTheirSourceAlone() {
    final var doc = document("one", "one.txt");
    final var known =
        DocumentRegistry.empty()
            .withDocuments(
                List.of(new IngestedDocument(doc, DocumentSource.toolCall("t", "c", "m-1"))));
    final var messages =
        List.<Message>of(userMessage(List.of(DocumentContent.documentContent(doc))));

    final var registry = DocumentRegistryBackfill.backfill(known, messages);

    assertThat(registry).isSameAs(known);
  }

  @Test
  void isIdempotent() {
    final var doc = document("one", "one.txt");
    final var messages =
        List.<Message>of(userMessage(List.of(DocumentContent.documentContent(doc))));

    final var once = DocumentRegistryBackfill.backfill(DocumentRegistry.empty(), messages);

    assertThat(DocumentRegistryBackfill.backfill(once, messages)).isSameAs(once);
    assertThat(once.size()).isEqualTo(1);
  }

  @Test
  void ignoresFlaggedTagsAndMessagesWithoutDocuments() {
    final var doc = document("one", "one.txt");
    final var tag = DocumentReferenceXmlTag.from(doc, DocumentSource.userMessage(null)).toContent();

    final var registry =
        DocumentRegistryBackfill.backfill(
            DocumentRegistry.empty(),
            List.of(userMessage("hi"), assistantMessage("hello"), userMessage(List.of(tag))));

    assertThat(registry.isEmpty()).isTrue();
  }
}
