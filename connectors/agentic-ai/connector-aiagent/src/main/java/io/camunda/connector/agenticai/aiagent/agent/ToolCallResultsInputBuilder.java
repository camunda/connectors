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
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Builds the input of the turn that follows a batch of tool calls from its results, in call order.
 *
 * <p>An agent instance with the documents feature shows a document as its tag inside the tool
 * result and reports it for the document workspace. An agent instance without it keeps the document
 * in a message of its own, next to its tag.
 */
public class ToolCallResultsInputBuilder {

  static final String TOOL_CALL_DOCUMENTS_PREAMBLE =
      "Documents extracted from tool calls (<doc /> tag + content pair):";

  /**
   * Builds the input of the turn that follows the batch.
   *
   * @return the messages and, for an agent instance with the documents feature, the documents of
   *     the results to register, in call order
   * @throws IllegalStateException if a call of the batch has no result
   */
  public TurnInput build(AgentContext agentContext, ToolCallBatch batch) {
    final var entries = batch.entriesInCallOrder();
    return agentContext.supports(AgentFeatures.DOCUMENTS)
        ? buildWithDocumentTags(entries)
        : buildWithDocumentMessage(entries);
  }

  /**
   * A document that is a whole result is persisted as its tag, like the documents that are not a
   * whole result. The documents of every result are registered with the tool call as their source.
   */
  private static TurnInput buildWithDocumentTags(List<ToolCallBatch.Entry> entries) {
    final var results =
        entries.stream()
            .map(ToolCallBatch.Entry::result)
            .map(DocumentReferences::withDocumentReferences)
            .toList();
    final var documents = new ArrayList<IngestedDocument>();
    for (final var entry : entries) {
      final var source = DocumentOrigin.toolCall(entry.result().name(), entry.result().id());
      entry.documents().forEach(document -> documents.add(new IngestedDocument(document, source)));
    }
    return new TurnInput(
        List.of(
            ToolCallResultMessage.builder()
                .results(results)
                .metadata(MessageMetadata.defaults())
                .build()),
        documents);
  }

  private static TurnInput buildWithDocumentMessage(List<ToolCallBatch.Entry> entries) {
    final var messages = new ArrayList<Message>();
    messages.add(
        ToolCallResultMessage.builder()
            .results(entries.stream().map(ToolCallBatch.Entry::result).toList())
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
    return new TurnInput(messages);
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
