/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference.InlineDocumentReference;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The documents the agent knows, in registration order and addressed by the {@link
 * AgentDocumentId}. Immutable: registering documents returns the collection that holds them.
 *
 * <p>Serializes as the JSON list of its documents. The first document of an id wins.
 *
 * <p>The reference of an inline document holds its content in the context and in the tool result,
 * both of which are part of the job completion. An inline document whose content exceeds {@link
 * #MAX_INLINE_CONTENT_LENGTH} is therefore rejected.
 */
public final class AgentDocuments {

  /**
   * The largest content of an inline document the agent accepts, counted in characters of the
   * content the reference holds (base64 for binary documents, text otherwise). A larger document is
   * rejected so that the job completion, which carries the content several times, stays well below
   * the message size limit of the broker.
   */
  public static final int MAX_INLINE_CONTENT_LENGTH = 1024 * 1024;

  private static final AgentDocuments EMPTY = new AgentDocuments(Map.of());

  private final Map<AgentDocumentId, AgentDocument> documentsById;

  private AgentDocuments(Map<AgentDocumentId, AgentDocument> documentsById) {
    this.documentsById = documentsById;
  }

  public static AgentDocuments empty() {
    return EMPTY;
  }

  /** The collection of the given documents; the first document of an id wins. */
  @JsonCreator
  public static AgentDocuments of(List<AgentDocument> documents) {
    final var byId = new LinkedHashMap<AgentDocumentId, AgentDocument>();
    documents.forEach(document -> byId.putIfAbsent(document.id(), document));
    return byId.isEmpty() ? EMPTY : new AgentDocuments(Collections.unmodifiableMap(byId));
  }

  /** A document that enters the conversation, with where it comes from. */
  public record NewDocument(Document document, DocumentOrigin origin) {}

  public Optional<AgentDocument> find(AgentDocumentId id) {
    return Optional.ofNullable(documentsById.get(id));
  }

  /** The documents in registration order. */
  @JsonValue
  public List<AgentDocument> all() {
    return List.copyOf(documentsById.values());
  }

  /**
   * The collection with the document registered. A document the collection already knows is left as
   * it is, with its first origin.
   *
   * @throws InlineDocumentTooLargeException if the document is new and an inline document with more
   *     than {@link #MAX_INLINE_CONTENT_LENGTH} characters of content
   * @throws DocumentIdCollisionException if the document gets the id of a different document
   * @throws UnsupportedDocumentReferenceException if the reference type of the document is not
   *     supported
   */
  public AgentDocuments register(Document document, DocumentOrigin origin) {
    return register(List.of(new NewDocument(document, origin)));
  }

  /**
   * The collection with the documents registered, in order. A document the collection already knows
   * is left as it is, with its first origin.
   *
   * @throws InlineDocumentTooLargeException if a new document is an inline document with more than
   *     {@link #MAX_INLINE_CONTENT_LENGTH} characters of content
   * @throws DocumentIdCollisionException if a document gets the id of a different document, one the
   *     collection holds or one of the batch
   * @throws UnsupportedDocumentReferenceException if the reference type of a document is not
   *     supported
   */
  public AgentDocuments register(List<NewDocument> newDocuments) {
    if (newDocuments.isEmpty()) {
      return this;
    }
    final var byId = new LinkedHashMap<>(documentsById);
    for (final var newDocument : newDocuments) {
      final var document = newDocument.document();
      final var id = AgentDocumentId.of(document);
      final var known = byId.get(id);
      if (known == null) {
        final var added = AgentDocument.of(document, newDocument.origin());
        rejectLargeInlineContent(added);
        byId.put(id, added);
      } else if (!known.isSameDocumentAs(document)) {
        throw new DocumentIdCollisionException(id);
      }
    }
    return byId.size() == documentsById.size()
        ? this
        : new AgentDocuments(Collections.unmodifiableMap(byId));
  }

  private static void rejectLargeInlineContent(AgentDocument document) {
    if (document.reference() instanceof InlineDocumentReference inline
        && inline.content() != null
        && inline.content().length() > MAX_INLINE_CONTENT_LENGTH) {
      final var name = document.fileName();
      throw new InlineDocumentTooLargeException(
          name != null ? name : document.id().value(), inline.content().length());
    }
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AgentDocuments that && all().equals(that.all());
  }

  @Override
  public int hashCode() {
    return all().hashCode();
  }

  @Override
  public String toString() {
    return "AgentDocuments" + all();
  }
}
