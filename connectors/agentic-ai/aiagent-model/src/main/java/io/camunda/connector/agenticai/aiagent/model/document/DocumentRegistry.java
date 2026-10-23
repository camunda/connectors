/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable, insertion-ordered registry of the documents known to the agent, keyed by the stable id
 * of the document. The first registration of an id wins, so registering a document again is a
 * no-op.
 */
public final class DocumentRegistry {

  private static final DocumentRegistry EMPTY = new DocumentRegistry(Map.of());

  private final Map<DocumentId, DocumentRegistryEntry> entriesById;

  private DocumentRegistry(Map<DocumentId, DocumentRegistryEntry> entriesById) {
    this.entriesById = entriesById;
  }

  public static DocumentRegistry empty() {
    return EMPTY;
  }

  /** Creates a registry from the given entries; the first entry of an id wins. */
  public static DocumentRegistry of(List<DocumentRegistryEntry> entries) {
    final var byId = new LinkedHashMap<DocumentId, DocumentRegistryEntry>();
    entries.forEach(entry -> byId.putIfAbsent(entry.id(), entry));
    return byId.isEmpty() ? EMPTY : new DocumentRegistry(Collections.unmodifiableMap(byId));
  }

  /** Returns a registry with the given entries added; already known ids are left untouched. */
  public DocumentRegistry withEntries(List<DocumentRegistryEntry> entries) {
    if (entries.isEmpty()) {
      return this;
    }
    final var byId = new LinkedHashMap<>(entriesById);
    entries.forEach(entry -> byId.putIfAbsent(entry.id(), entry));
    return byId.size() == entriesById.size()
        ? this
        : new DocumentRegistry(Collections.unmodifiableMap(byId));
  }

  public Optional<DocumentRegistryEntry> findById(DocumentId id) {
    return Optional.ofNullable(entriesById.get(id));
  }

  public boolean contains(DocumentId id) {
    return entriesById.containsKey(id);
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
}
