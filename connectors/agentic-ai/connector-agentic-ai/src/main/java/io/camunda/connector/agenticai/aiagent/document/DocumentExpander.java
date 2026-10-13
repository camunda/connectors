/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns the persisted {@link DocumentReferenceContent document references} in user messages into
 * the form the model providers and the agent instance history see: the tag text of the document
 * and, depending on the document delivery, the content of the document next to it.
 *
 * <p>The persisted form of a document is its reference only; the document is read from the {@link
 * DocumentRegistry}. The model gets the tag text of every reference and the document content of the
 * references with {@link DocumentDelivery#CONTENT} ({@link #expandMessages}); the history shows the
 * document of every reference ({@link #expandContent}). A document the registry does not know, or
 * whose reference cannot be read, leaves the tag text alone. A tag a user typed into a prompt is
 * text and stays text.
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
   * Returns the messages with the content of their user messages expanded for the model: the tag
   * text of every document reference, followed by the document for the references with the delivery
   * {@code content}.
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
   * Returns the content expanded to the tag text and the document of every document reference, or
   * the same list when nothing needs to change.
   */
  public List<Content> expandContent(List<Content> content, DocumentRegistry registry) {
    return expand(content, registry, false);
  }

  private List<Content> expand(
      List<Content> content, DocumentRegistry registry, boolean onlyDeliveryContent) {
    final var expanded = new ArrayList<Content>(content.size());
    boolean changed = false;
    for (int i = 0; i < content.size(); i++) {
      final var current = content.get(i);
      final var next = i + 1 < content.size() ? content.get(i + 1) : null;
      if (!(current instanceof DocumentReferenceContent reference)) {
        expanded.add(current);
        continue;
      }

      expanded.add(new TextContent(reference.toTag(), reference.metadata()));
      changed = true;
      final var withContent =
          !onlyDeliveryContent || reference.delivery() == DocumentDelivery.CONTENT;
      if (withContent
          && !(next instanceof DocumentContent following
              && isDocumentWithId(following.document(), reference.id()))) {
        final var document = rehydrate(registry, reference.id());
        if (document != null) {
          expanded.add(DocumentContent.documentContent(document));
        }
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
