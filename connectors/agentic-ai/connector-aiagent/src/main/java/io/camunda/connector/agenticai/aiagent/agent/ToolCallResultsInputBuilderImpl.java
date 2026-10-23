/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentGuard;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentGuard.Rejection;
import io.camunda.connector.agenticai.aiagent.document.DocumentReferences;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistries;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.document.LegacyDocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistryEntry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.api.document.Document;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the input of a turn from the tool call results of the previous turn and applies the
 * effects of the results of runtime tools.
 */
public class ToolCallResultsInputBuilderImpl implements ToolCallResultsInputBuilder {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ToolCallResultsInputBuilderImpl.class);

  static final String TOOL_CALL_DOCUMENTS_PREAMBLE =
      "Documents extracted from tool calls (<doc /> tag + content pair):";

  static final String DOCUMENT_CONTENT_PREAMBLE =
      "System message, not written by the user: the content of the documents loaded for the tool results above follows.";

  private final DocumentContentGuard contentGuard;
  private final ObjectMapper objectMapper;

  /**
   * @param contentGuard the check of a document whose content a result asks for
   * @param objectMapper the mapper that reads a document from its registry reference
   */
  public ToolCallResultsInputBuilderImpl(
      DocumentContentGuard contentGuard, ObjectMapper objectMapper) {
    this.contentGuard = contentGuard;
    this.objectMapper = objectMapper;
  }

  @Override
  public Input build(
      AgentContext agentContext, List<Entry> entries, Set<DocumentId> documentIdsWithContent) {
    final boolean documentFlow = agentContext.supports(AgentFeatures.DOCUMENTS);
    final var applied =
        applyDocumentContentEffects(
            agentContext.documentRegistry(), documentFlow, entries, documentIdsWithContent);
    return documentFlow
        ? buildWithDocumentTags(applied.entries(), applied.tagsWithContent())
        : buildWithDocumentMessage(applied.entries());
  }

  /**
   * The entries after their effects were applied, and the references with the delivery content of
   * the documents to add, in call order.
   */
  private record Applied(List<Entry> entries, List<Content> tagsWithContent) {}

  /**
   * Applies the content effects in the order of the entries. An entry sees the documents of the
   * registry and its own documents only, never the ones of another entry.
   */
  private Applied applyDocumentContentEffects(
      DocumentRegistry registry,
      boolean documentFlow,
      List<Entry> entries,
      Set<DocumentId> documentIdsWithContent) {
    final var handledIds = new HashSet<>(documentIdsWithContent);
    final var appliedEntries = new ArrayList<Entry>();
    final var tagsWithContent = new ArrayList<Content>();

    for (final var entry : entries) {
      if (entry.documentContent().isEmpty()) {
        appliedEntries.add(entry);
        continue;
      }

      // an agent instance without the documents feature knows no document
      final var known =
          documentFlow
              ? DocumentRegistries.withDocuments(registry, ingestedDocumentsOf(entry))
              : registry;
      final var accepted = new ArrayList<DocumentRegistryEntry>();
      final var rejection = checkDocumentContent(entry, known, handledIds, accepted);
      if (rejection != null) {
        appliedEntries.add(rejectedEntry(entry, rejection));
        continue;
      }
      accepted.forEach(
          document -> {
            handledIds.add(document.id());
            tagsWithContent.add(
                DocumentReferences.from(document).withDelivery(DocumentDelivery.CONTENT));
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
      Entry entry,
      DocumentRegistry known,
      Set<DocumentId> handledIds,
      List<DocumentRegistryEntry> accepted) {
    for (final var id : new LinkedHashSet<>(entry.documentContent())) {
      final var registryEntry =
          known
              .findById(id)
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "The runtime tool '%s' asked for the content of the document '%s', which the agent does not know"
                              .formatted(entry.result().name(), id)));
      if (handledIds.contains(id)) {
        continue;
      }
      final var rejection = contentGuard.check(documentOf(registryEntry)).orElse(null);
      if (rejection != null) {
        LOGGER.debug(
            "The content of the document {} named by the runtime tool '{}' is not loaded: {}",
            id,
            entry.result().name(),
            rejection.problem(),
            rejection.cause());
        return rejection;
      }
      accepted.add(registryEntry);
    }
    return null;
  }

  private Document documentOf(DocumentRegistryEntry entry) {
    try {
      return entry.toDocument(objectMapper);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "The document %s cannot be restored from its reference".formatted(entry.id()), e);
    }
  }

  /** The error result that replaces a result whose document content was rejected. */
  private static Entry rejectedEntry(Entry entry, Rejection rejection) {
    return new Entry(
        entry.result().withContent(RuntimeToolResult.error(rejectionText(rejection)).content()),
        List.of());
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

  private static List<IngestedDocument> ingestedDocumentsOf(Entry entry) {
    final var source = DocumentSource.toolCall(entry.result().name(), entry.result().id());
    return entry.documents().stream()
        .map(document -> new IngestedDocument(document, source))
        .toList();
  }

  /**
   * A document that is a whole result is persisted as its tag, like the documents that are not a
   * whole result. The documents of every result are registered with the tool call as their source.
   * The content of the documents the results asked for follows in a message of its own.
   */
  private static Input buildWithDocumentTags(List<Entry> entries, List<Content> tagsWithContent) {
    final var results =
        entries.stream()
            .map(Entry::result)
            .map(DocumentReferences::withDocumentReferences)
            .toList();
    final var documents = new ArrayList<IngestedDocument>();
    entries.forEach(entry -> documents.addAll(ingestedDocumentsOf(entry)));

    final var messages = new ArrayList<Message>();
    messages.add(
        ToolCallResultMessage.builder()
            .results(results)
            .metadata(defaultMessageMetadata())
            .build());
    if (!tagsWithContent.isEmpty()) {
      final var content = new ArrayList<Content>();
      content.add(textContent(DOCUMENT_CONTENT_PREAMBLE));
      content.addAll(tagsWithContent);

      final var metadata = new HashMap<>(defaultMessageMetadata());
      metadata.put(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
      messages.add(UserMessage.builder().content(content).metadata(metadata).build());
    }
    return new Input(messages, documents);
  }

  private static Input buildWithDocumentMessage(List<Entry> entries) {
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
    return new Input(messages, List.of());
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
    return Map.of("timestamp", ZonedDateTime.now());
  }
}
