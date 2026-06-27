/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.document;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.api.document.Document;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/** Registry of all documents seen during a conversation, keyed by their stable id. */
@AgenticAiRecord
@JsonDeserialize(builder = DocumentRegistry.DocumentRegistryJacksonProxyBuilder.class)
public record DocumentRegistry(List<DocumentRegistryEntry> entries)
    implements DocumentRegistryBuilder.With {

  public DocumentRegistry {
    // deduplicate by id: first entry wins (keep insertion order via LinkedHashMap)
    final var seen = new LinkedHashMap<String, DocumentRegistryEntry>();
    for (var entry : entries) {
      seen.putIfAbsent(entry.id(), entry);
    }
    entries = List.copyOf(seen.values());
  }

  /** Returns an empty registry. */
  public static DocumentRegistry empty() {
    return new DocumentRegistry(List.of());
  }

  /** Creates a registry from an existing list of entries (deduplication applied). */
  public static DocumentRegistry of(List<DocumentRegistryEntry> entries) {
    return new DocumentRegistry(entries);
  }

  /** Returns a builder. */
  public static DocumentRegistryBuilder builder() {
    return DocumentRegistryBuilder.builder();
  }

  /**
   * Returns a new registry that includes the entries from this registry plus new entries derived
   * from the given documents. Existing entries win on id collision (stable-id deduplication).
   */
  public DocumentRegistry withAddedDocuments(List<Document> docs) {
    final var combined = new ArrayList<>(entries());
    for (var doc : docs) {
      combined.add(DocumentRegistryEntry.from(doc));
    }
    return new DocumentRegistry(combined);
  }

  /** Finds an entry by its stable id. */
  public Optional<DocumentRegistryEntry> findById(String id) {
    return entries().stream().filter(e -> e.id().equals(id)).findFirst();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class DocumentRegistryJacksonProxyBuilder extends DocumentRegistryBuilder {}
}
