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
 * effects of the results: the content of documents the model gets, and the response documents of
 * the agent context.
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
    final var applied = applyEffects(agentContext, documentFlow, entries, documentIdsWithContent);
    final var input =
        documentFlow
            ? buildWithDocumentTags(
                applied.entries(), applied.tagsWithContent(), applied.noContentNotes())
            : buildWithDocumentMessage(applied.entries());
    return new Input(applied.agentContext(), input.messages(), input.documents());
  }

  /**
   * The entries after their effects were applied, the references with the delivery content of the
   * documents to add, in call order, the notes for the documents of tools with the delivery content
   * that got none, and the agent context with the response documents selected.
   */
  private record Applied(
      List<Entry> entries,
      List<Content> tagsWithContent,
      List<Content> noContentNotes,
      AgentContext agentContext) {}

  /**
   * Applies the effects in the order of the entries. An entry sees the documents of the registry
   * and its own documents only, never the ones of another entry. An entry whose content is rejected
   * has no effect at all.
   */
  private Applied applyEffects(
      AgentContext agentContext,
      boolean documentFlow,
      List<Entry> entries,
      Set<DocumentId> documentIdsWithContent) {
    final var registry = agentContext.documentRegistry();
    final var handledIds = new HashSet<>(documentIdsWithContent);
    final var appliedEntries = new ArrayList<Entry>();
    final var tagsWithContent = new ArrayList<Content>();
    final var noContentNotes = new ArrayList<Content>();
    final var notedIds = new HashSet<DocumentId>();
    var updatedContext = agentContext;

    for (final var entry : entries) {
      // an agent instance without the documents feature ignores the delivery of a tool
      final boolean delivery = documentFlow && entry.documentDelivery() == DocumentDelivery.CONTENT;
      if (entry.documentContent().isEmpty() && entry.responseDocuments().isEmpty() && !delivery) {
        appliedEntries.add(entry);
        continue;
      }

      // an agent instance without the documents feature knows no document
      final var known =
          documentFlow
              ? DocumentRegistries.withDocuments(registry, ingestedDocumentsOf(entry))
              : registry;
      requireKnownResponseDocuments(entry, known);
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
      if (delivery) {
        deliverContent(entry, handledIds, notedIds, tagsWithContent, noContentNotes);
      }
      for (final var selection : entry.responseDocuments()) {
        updatedContext = updatedContext.withResponseDocument(selection);
      }
      appliedEntries.add(entry);
    }
    return new Applied(appliedEntries, tagsWithContent, noContentNotes, updatedContext);
  }

  /**
   * Adds the content of the documents of an entry of a tool with the delivery content: the tag with
   * content of a document that passes the content guard, a note for one that does not. A document
   * whose content is handled already is left out. Only a document that gets content is handled: a
   * rejected one stays rejected for a later result that asks for its content, and gets one note.
   */
  private void deliverContent(
      Entry entry,
      Set<DocumentId> handledIds,
      Set<DocumentId> notedIds,
      List<Content> tagsWithContent,
      List<Content> noContentNotes) {
    final var source = DocumentSource.toolCall(entry.result().name(), entry.result().id());
    for (final var document : entry.documents()) {
      final var reference = DocumentReferences.from(document, source);
      if (handledIds.contains(reference.id()) || notedIds.contains(reference.id())) {
        continue;
      }
      final var rejection = contentGuard.check(document).orElse(null);
      if (rejection == null) {
        handledIds.add(reference.id());
        tagsWithContent.add(reference.withDelivery(DocumentDelivery.CONTENT));
      } else {
        notedIds.add(reference.id());
        noContentNotes.add(textContent(noContentNote(reference.id(), rejection)));
      }
    }
  }

  private static void requireKnownResponseDocuments(Entry entry, DocumentRegistry known) {
    for (final var selection : entry.responseDocuments()) {
      if (known.findById(selection.document()).isEmpty()) {
        throw new IllegalArgumentException(
            "The runtime tool '%s' selected the document '%s' for the response, which the agent does not know"
                .formatted(entry.result().name(), selection.document()));
      }
    }
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

  /**
   * The note that tells the model a document got no content and why. A reason that a retry can
   * overcome says that a tool for loading documents can try again. The note holds the content type
   * and the size at most; the failure behind a document that could not be read is logged only.
   */
  static String noContentNote(DocumentId documentId, Rejection rejection) {
    LOGGER.debug(
        "The content of the document {} is not added: {}",
        documentId,
        rejection.problem(),
        rejection.cause());
    return "Document %s was not loaded automatically: it %s. %s"
        .formatted(
            documentId,
            rejection.problem(),
            rejection.retryable()
                ? "A tool for loading documents can try to load it again."
                : "Pass its tag to a tool that processes it.");
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

  /** The messages of the input and the documents to register. */
  private record Turn(List<Message> messages, List<IngestedDocument> documents) {}

  /**
   * A document that is a whole result is persisted as its tag, like the documents that are not a
   * whole result. The documents of every result are registered with the tool call as their source.
   * The content of the documents the results asked for follows in a message of its own: the tags
   * with content after the preamble, then the notes of the documents without content.
   */
  private static Turn buildWithDocumentTags(
      List<Entry> entries, List<Content> tagsWithContent, List<Content> noContentNotes) {
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
    if (!tagsWithContent.isEmpty() || !noContentNotes.isEmpty()) {
      final var content = new ArrayList<Content>();
      if (!tagsWithContent.isEmpty()) {
        content.add(textContent(DOCUMENT_CONTENT_PREAMBLE));
        content.addAll(tagsWithContent);
      }
      content.addAll(noContentNotes);

      final var metadata = new HashMap<>(defaultMessageMetadata());
      metadata.put(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
      messages.add(UserMessage.builder().content(content).metadata(metadata).build());
    }
    return new Turn(messages, documents);
  }

  private static Turn buildWithDocumentMessage(List<Entry> entries) {
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
    return new Turn(messages, List.of());
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
