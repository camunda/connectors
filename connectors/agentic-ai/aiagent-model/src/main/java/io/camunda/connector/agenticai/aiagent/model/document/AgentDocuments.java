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
 */
public final class AgentDocuments {

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
   * @throws AgentDocumentIdCollisionException if the document gets the id of a different document
   * @throws UnsupportedDocumentReferenceException if the document is not a Camunda document
   */
  public AgentDocuments register(Document document, AgentDocumentOrigin origin) {
    return register(List.of(AgentDocument.of(document, origin)));
  }

  /**
   * The collection with the documents registered, in order. A document the collection already knows
   * is left as it is, with its first origin.
   *
   * @throws AgentDocumentIdCollisionException if a document gets the id of a different document,
   *     one the collection holds or one of the batch
   */
  public AgentDocuments register(List<AgentDocument> documents) {
    if (documents.isEmpty()) {
      return this;
    }
    final var byId = new LinkedHashMap<>(documentsById);
    for (final var document : documents) {
      final var known = byId.get(document.id());
      if (known == null) {
        byId.put(document.id(), document);
      } else if (!known.isSameDocumentAs(document)) {
        throw new AgentDocumentIdCollisionException(document.id());
      }
    }
    return byId.size() == documentsById.size()
        ? this
        : new AgentDocuments(Collections.unmodifiableMap(byId));
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
