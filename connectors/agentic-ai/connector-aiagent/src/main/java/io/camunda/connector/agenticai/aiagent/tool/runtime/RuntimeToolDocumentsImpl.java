/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.api.tool.AgentDocument;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolDocuments;
import io.camunda.connector.agenticai.aiagent.document.DocumentReferences;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import java.util.List;
import java.util.Optional;

/** The documents of a registry, as a runtime tool reads them. */
public final class RuntimeToolDocumentsImpl implements RuntimeToolDocuments {

  private final DocumentRegistry registry;
  private final ObjectMapper objectMapper;

  /**
   * @param registry the documents the agent knows
   * @param objectMapper the mapper that reads a document from its reference
   */
  public RuntimeToolDocumentsImpl(DocumentRegistry registry, ObjectMapper objectMapper) {
    this.registry = registry;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<AgentDocument> find(DocumentId id) {
    return registry.findById(id).map(entry -> new AgentDocumentImpl(entry, objectMapper));
  }

  @Override
  public List<AgentDocument> all() {
    return registry.entries().stream()
        .<AgentDocument>map(entry -> new AgentDocumentImpl(entry, objectMapper))
        .toList();
  }

  @Override
  public DocumentReferenceContent reference(DocumentId id) {
    return DocumentReferences.from(
        registry
            .findById(id)
            .orElseThrow(
                () -> new IllegalArgumentException("The agent does not know the document " + id)));
  }
}
