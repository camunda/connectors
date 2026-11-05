/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;

import io.camunda.connector.agenticai.aiagent.document.DocumentContentDelivery;
import io.camunda.connector.agenticai.aiagent.document.LegacyDocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments.NewDocument;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallEffect;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the input of the turn that follows a batch of tool calls from its results, in call order.
 * It applies what the results ask of the agent after all calls ran: the documents they return are
 * registered, and the effects of the results are applied.
 *
 * <p>An agent instance that tracks its documents shows a document as its handle inside the tool
 * result and reports it to be registered in its documents. An agent instance that does not keeps
 * the document in a message of its own, next to its tag.
 */
public class ToolCallResultsInputBuilder {

  static final String TOOL_CALL_DOCUMENTS_PREAMBLE =
      "Documents extracted from tool calls (<doc /> tag + content pair):";

  static final String DOCUMENT_CONTENT_PREAMBLE =
      "System message, not written by the user: the content of the documents loaded for the tool results above follows.";

  private final DocumentContentDelivery contentDelivery;

  /**
   * @param contentDelivery decides whether the content of a document a result asks for is added
   */
  public ToolCallResultsInputBuilder(DocumentContentDelivery contentDelivery) {
    this.contentDelivery = contentDelivery;
  }

  /**
   * Builds the input of the turn that follows the batch.
   *
   * @param documentIdsWithContent the ids of the documents whose content the model reads as of the
   *     start of the batch
   * @throws IllegalStateException if a call of the batch has no result
   * @throws IllegalArgumentException if a result asks for the content of a document that neither
   *     {@code agentContext} nor the result knows
   */
  public TurnInput build(
      AgentContext agentContext, ToolCallBatch batch, Set<AgentDocumentId> documentIdsWithContent) {
    final var entries = batch.entriesInCallOrder();
    final var documentHandling = agentContext.documentHandling();
    final var followUp = applyEffects(documentHandling, entries, documentIdsWithContent);
    return switch (documentHandling) {
      case DocumentHandling.Tracked ignored -> buildWithDocumentBlocks(entries, followUp);
      case DocumentHandling.Untracked ignored -> buildWithDocumentMessage(entries);
    };
  }

  /**
   * Applies the effects in the order of the entries and returns the blocks of the message that
   * follows the tool results: the documents with the delivery content and the notes for the
   * documents that are not loaded. An entry sees the documents of the agent and its own documents
   * only, never the ones of another entry.
   */
  private List<Content> applyEffects(
      DocumentHandling documentHandling,
      List<ToolCallBatch.Entry> entries,
      Set<AgentDocumentId> documentIdsWithContent) {
    final var agentDocuments =
        switch (documentHandling) {
          case DocumentHandling.Tracked tracked -> tracked.documents();
          case DocumentHandling.Untracked ignored -> AgentDocuments.empty();
        };
    final var handledIds = new HashSet<>(documentIdsWithContent);
    final var followUp = new ArrayList<Content>();

    for (final var entry : entries) {
      final var requestedContent = new ArrayList<AgentDocumentId>();
      for (final var effect : entry.effects()) {
        switch (effect) {
          case ToolCallEffect.AddDocumentContent add -> requestedContent.add(add.id());
        }
      }
      if (requestedContent.isEmpty()) {
        continue;
      }

      final var known = agentDocuments.register(newDocumentsOf(entry));
      for (final var outcome : contentDelivery.deliver(known, requestedContent, handledIds)) {
        switch (outcome) {
          case DocumentContentDelivery.Outcome.Delivered delivered ->
              followUp.add(delivered.withContent());
          case DocumentContentDelivery.Outcome.Withheld withheld ->
              followUp.add(textContent(withheld.note()));
        }
      }
      handledIds.addAll(requestedContent);
    }
    return List.copyOf(followUp);
  }

  private static List<NewDocument> newDocumentsOf(ToolCallBatch.Entry entry) {
    final var origin = DocumentOrigin.toolCall(entry.result().name(), entry.result().id());
    return entry.documents().stream().map(document -> new NewDocument(document, origin)).toList();
  }

  /**
   * A document that is a whole result is persisted as a document block, like the documents that are
   * not a whole result. The documents of every result are registered with the tool call as their
   * origin. The content of the documents the results asked for, or a note on why it is not loaded,
   * follows in a message of its own.
   */
  private static TurnInput buildWithDocumentBlocks(
      List<ToolCallBatch.Entry> entries, List<Content> followUp) {
    final var results =
        entries.stream()
            .map(ToolCallBatch.Entry::result)
            .map(ToolCallResultContent::withDocumentsAsReferences)
            .toList();
    final var documents = new ArrayList<NewDocument>();
    entries.forEach(entry -> documents.addAll(newDocumentsOf(entry)));

    final var messages = new ArrayList<Message>();
    messages.add(
        ToolCallResultMessage.builder()
            .results(results)
            .metadata(MessageMetadata.defaults())
            .build());
    if (!followUp.isEmpty()) {
      final var content = new ArrayList<Content>();
      content.add(textContent(DOCUMENT_CONTENT_PREAMBLE));
      content.addAll(followUp);

      final var metadata = new HashMap<>(MessageMetadata.defaults());
      metadata.put(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
      messages.add(UserMessage.builder().content(content).metadata(metadata).build());
    }
    return new TurnInput(messages, documents);
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
}
