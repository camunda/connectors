/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;

import io.camunda.connector.agenticai.aiagent.document.LegacyDocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.api.document.Document;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the input of the turn that follows a turn with tool calls from the tool call results of
 * all of its calls: the tool call result message and, when a result holds documents, the message
 * that carries them.
 */
final class ToolCallResultsInputBuilder {

  static final String TOOL_CALL_DOCUMENTS_PREAMBLE =
      "Documents extracted from tool calls (<doc /> tag + content pair):";

  private ToolCallResultsInputBuilder() {}

  /**
   * A tool call result with the documents extracted from it.
   *
   * @param result the tool call result that becomes part of the conversation
   * @param documents the documents of the result, in order of appearance
   */
  record Entry(ToolCallResultContent result, List<Document> documents) {

    Entry {
      documents = List.copyOf(documents);
    }

    /** The entry for the result of a runtime tool; its documents are the ones of its content. */
    static Entry of(RuntimeToolExecution execution) {
      return new Entry(
          execution.result(),
          ToolCallResultDocumentExtractor.documentsOf(execution.result().content()));
    }
  }

  /**
   * The messages for the results of all tool calls of a turn.
   *
   * @param entries the results in the order of the tool calls
   * @return the tool call result message, followed by the message with the documents of the results
   *     if there are any
   */
  static List<Message> build(List<Entry> entries) {
    final var messages = new ArrayList<Message>();
    messages.add(
        ToolCallResultMessage.builder()
            .results(entries.stream().map(Entry::result).toList())
            .metadata(defaultMessageMetadata())
            .build());

    final var documentGroups =
        entries.stream()
            .filter(entry -> !entry.documents().isEmpty())
            .map(
                entry ->
                    new ToolCallResultDocumentExtractor.ToolCallDocuments(
                        entry.result().id(), entry.result().name(), entry.documents()))
            .toList();
    if (!documentGroups.isEmpty()) {
      final var content = new ArrayList<Content>();
      content.add(textContent(TOOL_CALL_DOCUMENTS_PREAMBLE));
      content.addAll(documentPairs(documentGroups));

      final var metadata = new HashMap<>(defaultMessageMetadata());
      metadata.put(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
      messages.add(UserMessage.builder().content(content).metadata(metadata).build());
    }
    return List.copyOf(messages);
  }

  /** A document tag followed by the document, for every document of every group. */
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

  static Map<String, Object> defaultMessageMetadata() {
    return Map.of("timestamp", ZonedDateTime.now(ZoneId.systemDefault()));
  }
}
