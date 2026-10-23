/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentGuard;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentGuard.Rejection;
import io.camunda.connector.agenticai.aiagent.document.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.document.DocumentRestorer;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.document.UnsupportedDocumentReferenceException;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.api.document.Document;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the input of the turn that follows a batch of tool calls from its results, in call order.
 * It applies what the results ask of the agent after all calls ran: the documents they return are
 * registered, and the content of the documents a runtime tool names is added.
 *
 * <p>An agent instance that tracks its documents shows a document as its handle inside the tool
 * result and reports it to be registered in its documents. An agent instance that does not keeps
 * the document in a message of its own, next to its tag.
 */
public class ToolCallResultsInputBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(ToolCallResultsInputBuilder.class);

  static final String TOOL_CALL_DOCUMENTS_PREAMBLE =
      "Documents extracted from tool calls (<doc /> tag + content pair):";

  static final String DOCUMENT_CONTENT_PREAMBLE =
      "System message, not written by the user: the content of the documents loaded for the tool results above follows.";

  private final DocumentContentGuard contentGuard;
  private final DocumentRestorer documentRestorer;

  /**
   * @param contentGuard the check of a document whose content a result asks for
   * @param documentRestorer restores the document of an agent document
   */
  public ToolCallResultsInputBuilder(
      DocumentContentGuard contentGuard, DocumentRestorer documentRestorer) {
    this.contentGuard = contentGuard;
    this.documentRestorer = documentRestorer;
  }

  /**
   * Builds the input of the turn that follows the batch.
   *
   * @param documentIdsWithContent the ids of the documents whose content the model reads as of the
   *     start of the batch
   * @throws IllegalStateException if a call of the batch has no result
   * @throws UnsupportedDocumentReferenceException if an agent instance that tracks its documents
   *     gets a document that is not a Camunda document
   * @throws IllegalArgumentException if a result asks for the content of a document that neither
   *     {@code agentContext} nor the result knows
   */
  public TurnInput build(
      AgentContext agentContext, ToolCallBatch batch, Set<AgentDocumentId> documentIdsWithContent) {
    final var entries = batch.entriesInCallOrder();
    final var applied =
        applyDocumentContentEffects(
            agentContext.documentHandling(), entries, documentIdsWithContent);
    return switch (agentContext.documentHandling()) {
      case DocumentHandling.Tracked ignored ->
          buildWithDocumentBlocks(applied.entries(), applied.tagsWithContent());
      case DocumentHandling.Untracked ignored -> buildWithDocumentMessage(applied.entries());
    };
  }

  /**
   * The entries after their effects were applied, and the blocks with the delivery content of the
   * documents to add, in call order.
   */
  private record Applied(List<ToolCallBatch.Entry> entries, List<Content> tagsWithContent) {}

  /**
   * Applies the content effects in the order of the entries. An entry sees the documents of the
   * agent and its own documents only, never the ones of another entry.
   */
  private Applied applyDocumentContentEffects(
      DocumentHandling documentHandling,
      List<ToolCallBatch.Entry> entries,
      Set<AgentDocumentId> documentIdsWithContent) {
    final var handledIds = new HashSet<>(documentIdsWithContent);
    final var appliedEntries = new ArrayList<ToolCallBatch.Entry>();
    final var tagsWithContent = new ArrayList<Content>();

    for (final var entry : entries) {
      if (entry.documentContent().isEmpty()) {
        appliedEntries.add(entry);
        continue;
      }

      final var known =
          switch (documentHandling) {
            case DocumentHandling.Tracked tracked ->
                tracked.documents().register(newDocumentsOf(entry));
            case DocumentHandling.Untracked ignored -> AgentDocuments.empty();
          };
      final var accepted = new ArrayList<AgentDocument>();
      final var rejection = checkDocumentContent(entry, known, handledIds, accepted);
      if (rejection != null) {
        appliedEntries.add(rejectedEntry(entry, rejection));
        continue;
      }
      accepted.forEach(document -> {
        handledIds.add(document.id());
        tagsWithContent.add(
            AgentDocumentContent.of(document.id()).withDelivery(DocumentDelivery.CONTENT));
      });
      appliedEntries.add(entry);
    }
    return new Applied(appliedEntries, tagsWithContent);
  }

  /**
   * Collects the documents of the entry whose content is to be added, without the ones that are
   * handled already.
   *
   * @return the reason that a document of the entry is not allowed as content, or {@code null}
   */
  private @Nullable Rejection checkDocumentContent(
      ToolCallBatch.Entry entry,
      AgentDocuments known,
      Set<AgentDocumentId> handledIds,
      List<AgentDocument> accepted) {
    for (final var id : new LinkedHashSet<>(entry.documentContent())) {
      final var agentDocument =
          known
              .find(id)
              .orElseThrow(() ->
                  new IllegalArgumentException(
                      "The runtime tool '%s' asked for the content of the document '%s', which the agent does not know"
                          .formatted(entry.result().name(), id)));
      if (handledIds.contains(id)) {
        continue;
      }
      final var rejection = contentGuard.check(documentOf(known, agentDocument)).orElse(null);
      if (rejection != null) {
        LOGGER.debug(
            "The content of the document {} named by the runtime tool '{}' is not loaded: {}",
            id,
            entry.result().name(),
            rejection.problem(),
            rejection.cause());
        return rejection;
      }
      accepted.add(agentDocument);
    }
    return null;
  }

  private Document documentOf(AgentDocuments known, AgentDocument agentDocument) {
    return documentRestorer
        .restore(known, agentDocument.id())
        .orElseThrow(() ->
            new IllegalStateException(
                "The document %s cannot be restored from its reference"
                    .formatted(agentDocument.id())));
  }

  /** The error result that replaces a result whose document content was rejected. */
  private static ToolCallBatch.Entry rejectedEntry(ToolCallBatch.Entry entry, Rejection rejection) {
    return ToolCallBatch.Entry.from(
        entry.result().withContent(RuntimeToolResult.error(rejectionText(rejection)).content()));
  }

  /**
   * The text the model reads when a document is not loaded. It holds no detail of a failure: the
   * cause is logged.
   */
  private static String rejectionText(Rejection rejection) {
    return rejection.reason() == Rejection.Reason.UNREADABLE
        ? "The document could not be read; it is not loaded."
        : "The document %s; pass its tag to a tool that processes it."
            .formatted(rejection.problem());
  }

  private static List<AgentDocument> newDocumentsOf(ToolCallBatch.Entry entry) {
    final var origin = AgentDocumentOrigin.toolCall(entry.result().name(), entry.result().id());
    return entry.documents().stream().map(document -> AgentDocument.of(document, origin)).toList();
  }

  /**
   * A document that is a whole result is persisted as a document block, like the documents that are
   * not a whole result. The documents of every result are registered with the tool call as their
   * origin. The content of the documents the results asked for follows in a message of its own.
   */
  private static TurnInput buildWithDocumentBlocks(
      List<ToolCallBatch.Entry> entries, List<Content> tagsWithContent) {
    entries.forEach(ToolCallBatch.Entry::requireCamundaDocuments);
    final var results =
        entries.stream()
            .map(ToolCallBatch.Entry::result)
            .map(ToolCallResultContent::withDocumentsAsReferences)
            .toList();
    final var documents = new ArrayList<AgentDocument>();
    entries.forEach(entry -> documents.addAll(newDocumentsOf(entry)));

    final var messages = new ArrayList<Message>();
    messages.add(
        ToolCallResultMessage.builder()
            .results(results)
            .metadata(MessageMetadata.defaults())
            .build());
    if (!tagsWithContent.isEmpty()) {
      final var content = new ArrayList<Content>();
      content.add(textContent(DOCUMENT_CONTENT_PREAMBLE));
      content.addAll(tagsWithContent);

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
                DocumentReferenceXmlTag.from(doc, group.toolCallId(), group.toolCallName())
                    .toXml()));
        content.add(DocumentContent.documentContent(doc));
      }
    }
    return content;
  }
}
