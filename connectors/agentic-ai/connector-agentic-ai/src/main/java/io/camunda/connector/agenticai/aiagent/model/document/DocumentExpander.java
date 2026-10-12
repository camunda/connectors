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
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns the persisted form of the documents in user messages into the form the model providers and
 * the agent instance history see: a document tag next to the content of its document.
 *
 * <p>The persisted form of a document is its tag only, a {@link TextContent} marked with {@link
 * DocumentReferenceXmlTag#KIND_DOCUMENT_REFERENCE}; the document is read from the {@link
 * DocumentRegistry}. Expanding adds a {@link DocumentContent} after a flagged tag, unless one for
 * the same document already follows it. The model gets the content only of the tags that are marked
 * as having content ({@link #expandMessages}); the history shows the document of every flagged tag
 * ({@link #expandContent}). A document the registry does not know, or whose reference cannot be
 * read, leaves the tag alone. Only flagged text is expanded, so a document tag a user typed into a
 * prompt stays text.
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

  /**
   * Returns the messages with the content of their user messages expanded for the model: only the
   * tags {@link DocumentReferenceXmlTag#isLoaded marked with content} get their document.
   */
  public List<Message> expandMessages(List<Message> messages, DocumentRegistry registry) {
    final var expanded = new ArrayList<Message>(messages.size());
    boolean changed = false;
    for (final var message : messages) {
      if (message instanceof UserMessage userMessage) {
        final var content = expand(userMessage.content(), registry, true);
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

  /**
   * Returns the content expanded with the document of every flagged tag, or the same list when
   * nothing needs to change.
   */
  public List<Content> expandContent(List<Content> content, DocumentRegistry registry) {
    return expand(content, registry, false);
  }

  private List<Content> expand(
      List<Content> content, DocumentRegistry registry, boolean onlyTagsWithContent) {
    final var expanded = new ArrayList<Content>(content.size());
    boolean changed = false;
    for (int i = 0; i < content.size(); i++) {
      final var current = content.get(i);
      final var next = i + 1 < content.size() ? content.get(i + 1) : null;
      switch (current) {
        case TextContent text
            when DocumentReferenceXmlTag.documentIdOf(text) != null
                && (!onlyTagsWithContent || DocumentReferenceXmlTag.isLoaded(text)) -> {
          expanded.add(text);
          final var id = Objects.requireNonNull(DocumentReferenceXmlTag.documentIdOf(text));
          if (!(next instanceof DocumentContent following
              && isDocumentWithId(following.document(), id))) {
            final var document = rehydrate(registry, id);
            if (document != null) {
              expanded.add(DocumentContent.documentContent(document));
              changed = true;
            }
          }
        }
        default -> expanded.add(current);
      }
    }
    return changed ? List.copyOf(expanded) : content;
  }

  private static boolean isDocumentWithId(Document document, String id) {
    return isTaggable(document) && id.equals(DocumentHandle.idFor(document));
  }

  /** Only Camunda and external documents get a tag, inline documents are left as they are. */
  private static boolean isTaggable(Document document) {
    return document.reference() instanceof CamundaDocumentReference
        || document.reference() instanceof ExternalDocumentReference;
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
