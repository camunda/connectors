/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns the persisted form of the documents in user messages into the form the model providers and
 * the agent instance history see: every document tag next to the content of its document.
 *
 * <p>The persisted form of a document is its tag only, a {@link TextContent} flagged with {@link
 * DocumentReferenceXmlTag#METADATA_DOCUMENT_REF}; the document is read from the {@link
 * DocumentRegistry}. Expanding adds a {@link DocumentContent} after each flagged tag, unless one
 * for the same document already follows it. A document the registry does not know, or whose
 * reference cannot be read, leaves the tag alone. Only flagged text is expanded, so a document tag
 * a user typed into a prompt stays text.
 *
 * <p>Messages that carry the content of a document without a flagged tag (a tag in an older
 * attribute format followed by the content, or the content alone) get the canonical tag of the
 * document: the text of an older tag is replaced and a missing tag is inserted. Such a tag text is
 * not flagged. Inline documents are left as they are. Text that matches no known tag pattern is not
 * touched.
 *
 * <p>The result depends only on the messages and the registry, never on time or call order, so it
 * is stable across requests (prompt caching). Nothing that is stored is changed.
 */
public final class DocumentExpander {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentExpander.class);

  private final ObjectMapper objectMapper;

  /**
   * @param objectMapper the mapper that binds a document reference to a {@link Document}, the one
   *     that reads the documents of persisted messages
   */
  public DocumentExpander(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Returns the messages with the content of their user messages expanded. */
  public List<Message> expandMessages(List<Message> messages, DocumentRegistry registry) {
    final var expanded = new ArrayList<Message>(messages.size());
    boolean changed = false;
    for (final var message : messages) {
      if (message instanceof UserMessage userMessage) {
        final var content = expandContent(userMessage.content(), registry);
        if (content != userMessage.content()) {
          expanded.add(userMessage.withContent(content));
          changed = true;
          continue;
        }
      }
      expanded.add(message);
    }
    return changed ? List.copyOf(expanded) : messages;
  }

  /** Returns the content expanded, or the same list when nothing needs to change. */
  public List<Content> expandContent(List<Content> content, DocumentRegistry registry) {
    final var expanded = new ArrayList<Content>(content.size());
    boolean changed = false;
    for (int i = 0; i < content.size(); i++) {
      final var current = content.get(i);
      final var next = i + 1 < content.size() ? content.get(i + 1) : null;
      switch (current) {
        case TextContent text when DocumentReferenceXmlTag.documentRefOf(text) != null -> {
          expanded.add(text);
          final var id = Objects.requireNonNull(DocumentReferenceXmlTag.documentRefOf(text));
          if (!(next instanceof DocumentContent following
              && isDocumentWithId(following.document(), id))) {
            final var document = rehydrate(registry, id);
            if (document != null) {
              expanded.add(DocumentContent.documentContent(document));
              changed = true;
            }
          }
        }
        case TextContent text
            when next instanceof DocumentContent following
                && isOlderTagOf(text.text(), following.document()) -> {
          final var canonical = canonicalTag(following.document(), registry).toXml();
          if (canonical.equals(text.text())) {
            expanded.add(text);
          } else {
            expanded.add(new TextContent(canonical, text.metadata()));
            changed = true;
          }
        }
        case DocumentContent documentContent -> {
          if (isTaggable(documentContent.document()) && !endsWithTag(expanded)) {
            expanded.add(
                TextContent.textContent(
                    canonicalTag(documentContent.document(), registry).toXml()));
            changed = true;
          }
          expanded.add(documentContent);
        }
        default -> expanded.add(current);
      }
    }
    return changed ? List.copyOf(expanded) : content;
  }

  private static boolean endsWithTag(List<Content> content) {
    return !content.isEmpty()
        && content.getLast() instanceof TextContent text
        && (DocumentReferenceXmlTag.documentRefOf(text) != null
            || DocumentReferenceXmlTag.isTag(text.text()));
  }

  /** Only Camunda and external documents get a tag, inline documents are left as they are. */
  private static boolean isTaggable(Document document) {
    return document.reference() instanceof CamundaDocumentReference
        || document.reference() instanceof ExternalDocumentReference;
  }

  private static boolean isDocumentWithId(Document document, String id) {
    return isTaggable(document) && id.equals(DocumentHandle.idFor(document));
  }

  /**
   * Whether the text is a document tag, in any attribute format, that names the given document: by
   * its {@code id}, its {@code documentId} (Camunda document) or its {@code url} (external
   * document).
   */
  private static boolean isOlderTagOf(String text, Document document) {
    if (!isTaggable(document)) {
      return false;
    }
    final Map<String, String> attributes = DocumentReferenceXmlTag.parseAttributes(text);
    if (attributes == null) {
      return false;
    }
    final var id = DocumentHandle.idFor(document);
    return switch (document.reference()) {
      case CamundaDocumentReference reference ->
          id.equals(attributes.get("id")) || id.equals(attributes.get("documentId"));
      case ExternalDocumentReference reference ->
          id.equals(attributes.get("id")) || reference.url().equals(attributes.get("url"));
      default -> false;
    };
  }

  private static DocumentReferenceXmlTag canonicalTag(
      Document document, DocumentRegistry registry) {
    return registry
        .findById(DocumentHandle.idFor(document))
        .map(DocumentReferenceXmlTag::from)
        .orElseGet(() -> DocumentReferenceXmlTag.from(document));
  }

  private @Nullable Document rehydrate(DocumentRegistry registry, String id) {
    final var entry = registry.findById(id);
    if (entry.isEmpty()) {
      LOGGER.debug("Document {} is not in the registry, leaving its tag without content", id);
      return null;
    }
    try {
      return objectMapper.convertValue(entry.get().reference(), Document.class);
    } catch (RuntimeException e) {
      LOGGER.warn(
          "Document {} could not be read from its registry reference, leaving its tag without content",
          id,
          e);
      return null;
    }
  }
}
