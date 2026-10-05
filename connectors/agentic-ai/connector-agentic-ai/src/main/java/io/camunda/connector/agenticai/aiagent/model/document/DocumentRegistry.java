/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import io.camunda.connector.agenticai.aiagent.agent.ContentTreeDocumentWalker;
import io.camunda.connector.agenticai.aiagent.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.api.document.Document;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Immutable, insertion-ordered registry of the documents known to the agent, keyed by {@link
 * DocumentHandle#idFor stable id}. The first registration of an id wins, so registering a document
 * again is a no-op.
 */
public final class DocumentRegistry {

  private static final int MAX_LISTED_DOCUMENTS = 50;

  private static final DocumentRegistry EMPTY = new DocumentRegistry(Map.of());

  private final Map<String, DocumentRegistryEntry> entriesById;

  private DocumentRegistry(Map<String, DocumentRegistryEntry> entriesById) {
    this.entriesById = entriesById;
  }

  public static DocumentRegistry empty() {
    return EMPTY;
  }

  /** Creates a registry from the given entries; the first entry of an id wins. */
  public static DocumentRegistry of(List<DocumentRegistryEntry> entries) {
    final var byId = new LinkedHashMap<String, DocumentRegistryEntry>();
    entries.forEach(entry -> byId.putIfAbsent(entry.id(), entry));
    return byId.isEmpty() ? EMPTY : new DocumentRegistry(Collections.unmodifiableMap(byId));
  }

  /**
   * Builds a registry from the documents found in a stored conversation: user message document
   * content and the content trees of tool call results. Tool call results are scanned before the
   * documents they produced are repeated as document content, so the tool call origin is retained.
   */
  public static DocumentRegistry fromMessages(List<Message> messages) {
    final var ingested = new ArrayList<IngestedDocument>();
    for (final var message : messages) {
      switch (message) {
        case ToolCallResultMessage toolCallResultMessage ->
            toolCallResultMessage
                .results()
                .forEach(
                    result ->
                        result.content().stream()
                            .flatMap(content -> documentsOf(content).stream())
                            .forEach(
                                document ->
                                    ingested.add(
                                        new IngestedDocument(
                                            document, result.id(), result.name()))));
        case UserMessage userMessage ->
            userMessage.content().stream()
                .filter(DocumentContent.class::isInstance)
                .map(content -> ((DocumentContent) content).document())
                .forEach(document -> ingested.add(new IngestedDocument(document, null, null)));
        default -> {
          // system and assistant messages carry no inbound documents
        }
      }
    }
    return empty().withDocuments(ingested);
  }

  private static List<Document> documentsOf(Content content) {
    return switch (content) {
      case DocumentContent documentContent -> List.of(documentContent.document());
      case ObjectContent objectContent ->
          ContentTreeDocumentWalker.extractDocumentsFromContent(objectContent.content());
      default -> List.of();
    };
  }

  /** Returns a registry with the given documents added; already known ids are left untouched. */
  public DocumentRegistry withDocuments(List<IngestedDocument> documents) {
    if (documents.isEmpty()) {
      return this;
    }
    final var byId = new LinkedHashMap<>(entriesById);
    for (final var ingested : documents) {
      final var id = DocumentHandle.idFor(ingested.document());
      if (!byId.containsKey(id)) {
        byId.put(id, DocumentRegistryEntry.from(ingested));
      }
    }
    return byId.size() == entriesById.size()
        ? this
        : new DocumentRegistry(Collections.unmodifiableMap(byId));
  }

  public Optional<DocumentRegistryEntry> findById(String id) {
    return Optional.ofNullable(entriesById.get(id));
  }

  /** Returns the entries in registration order. */
  public List<DocumentRegistryEntry> entries() {
    return List.copyOf(entriesById.values());
  }

  public boolean isEmpty() {
    return entriesById.isEmpty();
  }

  public int size() {
    return entriesById.size();
  }

  /**
   * Describes the known documents for a message to the model: their tags, at most 50 of them with
   * the rest counted, or a sentence when no document is known.
   */
  public String knownDocumentsText() {
    if (isEmpty()) {
      return "No documents are known.";
    }
    final var listed =
        entriesById.values().stream()
            .limit(MAX_LISTED_DOCUMENTS)
            .map(entry -> DocumentReferenceXmlTag.from(entry).toXml())
            .collect(Collectors.joining("\n"));
    final var omitted = entriesById.size() - MAX_LISTED_DOCUMENTS;
    return "Known documents:\n"
        + listed
        + (omitted > 0 ? "\n(and %d more)".formatted(omitted) : "");
  }
}
