/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;

import io.camunda.connector.agenticai.aiagent.document.DocumentReferences;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.document.LegacyDocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolExecution;
import io.camunda.connector.api.document.Document;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Builds the input of the turn that follows a turn with tool calls, from the results of all calls.
 *
 * <p>An agent instance with the documents feature shows a document as its tag inside the tool
 * result and reports it for the document workspace. An agent instance without it keeps the document
 * in a message of its own, next to its tag.
 */
final class ToolCallResultsInputBuilder {

  static final String TOOL_CALL_DOCUMENTS_PREAMBLE =
      "Documents extracted from tool calls (<doc /> tag + content pair):";

  private ToolCallResultsInputBuilder() {}

  record Entry(ToolCallResultContent result, List<Document> documents) {

    Entry {
      documents = List.copyOf(documents);
    }

    /**
     * The entry for the result of a runtime tool; its documents are the ones of its content, so the
     * result of a failed call, whose content is text, has none.
     */
    static Entry of(RuntimeToolExecution execution) {
      final var result = execution.toolCallResult();
      return new Entry(result, ToolCallResultDocumentExtractor.documentsOf(result.content()));
    }
  }

  /**
   * The input of the turn that follows the results.
   *
   * @param messages the messages of the input, in order
   * @param documents the documents of the results to register, in the order of the tool calls;
   *     empty without the documents feature
   */
  record Input(List<Message> messages, List<IngestedDocument> documents) {

    Input {
      messages = List.copyOf(messages);
      documents = List.copyOf(documents);
    }
  }

  /**
   * The input for the results of all tool calls of a turn, in call order.
   *
   * @param documentFlow whether the agent instance has the documents feature
   */
  static Input build(List<Entry> entries, boolean documentFlow) {
    return documentFlow ? buildWithDocumentTags(entries) : buildWithDocumentMessage(entries);
  }

  /**
   * A document that is a whole result is persisted as its tag, like the documents that are not a
   * whole result. The documents of every result are registered with the tool call as their origin.
   */
  private static Input buildWithDocumentTags(List<Entry> entries) {
    final var results =
        entries.stream()
            .map(Entry::result)
            .map(DocumentReferences::withDocumentReferences)
            .toList();
    final var documents = new ArrayList<IngestedDocument>();
    for (final var entry : entries) {
      final var origin = DocumentOrigin.toolCall(entry.result().name(), entry.result().id());
      entry.documents().forEach(document -> documents.add(new IngestedDocument(document, origin)));
    }
    return new Input(
        List.of(
            ToolCallResultMessage.builder()
                .results(results)
                .metadata(MessageMetadata.defaults())
                .build()),
        documents);
  }

  private static Input buildWithDocumentMessage(List<Entry> entries) {
    final var messages = new ArrayList<Message>();
    messages.add(
        ToolCallResultMessage.builder()
            .results(entries.stream().map(Entry::result).toList())
            .metadata(MessageMetadata.defaults())
            .build());

    final var documentGroups =
        entries.stream()
            .filter(entry -> !entry.documents().isEmpty())
            .map(entry ->
                new ToolCallResultDocumentExtractor.ToolCallDocuments(
                    entry.result().id(), entry.result().name(), entry.documents()))
            .toList();
    if (!documentGroups.isEmpty()) {
      final var content = new ArrayList<Content>();
      content.add(textContent(TOOL_CALL_DOCUMENTS_PREAMBLE));
      content.addAll(documentPairs(documentGroups));

      final var metadata = new HashMap<>(MessageMetadata.defaults());
      metadata.put(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
      messages.add(UserMessage.builder().content(content).metadata(metadata).build());
    }
    return new Input(messages, List.of());
  }

  static List<Content> documentPairs(
      List<ToolCallResultDocumentExtractor.ToolCallDocuments> documentGroups) {
    final var content = new ArrayList<Content>();
    for (var group : documentGroups) {
      for (var doc : group.documents()) {
        content.add(
            textContent(
                LegacyDocumentReferenceXmlTag.from(doc, group.toolCallId(), group.toolCallName())
                    .toXml()));
        content.add(DocumentContent.documentContent(doc));
      }
    }
    return content;
  }
}
