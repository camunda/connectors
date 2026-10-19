/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable, insertion-ordered registry of the documents known to the agent, keyed by {@link
 * DocumentHandle#idFor stable id}. The first registration of an id wins, so registering a document
 * again is a no-op.
 */
public final class DocumentRegistry {

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
}
