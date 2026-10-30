/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.api.tool.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistryEntry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.api.document.Document;
import org.jspecify.annotations.Nullable;

/**
 * A document of the registry; the document is read from its reference when it is asked for. Safe to
 * read from several threads: reading the reference twice gives equal documents.
 */
final class AgentDocumentImpl implements AgentDocument {

  private final DocumentRegistryEntry entry;
  private final ObjectMapper objectMapper;
  private volatile @Nullable Document document;

  AgentDocumentImpl(DocumentRegistryEntry entry, ObjectMapper objectMapper) {
    this.entry = entry;
    this.objectMapper = objectMapper;
  }

  @Override
  public DocumentId id() {
    return entry.id();
  }

  @Override
  public DocumentSource source() {
    return entry.source();
  }

  @Override
  public Document document() {
    var restored = document;
    if (restored == null) {
      try {
        restored = entry.toDocument(objectMapper);
      } catch (IllegalArgumentException e) {
        throw new IllegalStateException(
            "The document %s cannot be restored from its reference".formatted(entry.id()), e);
      }
      document = restored;
    }
    return restored;
  }
}
