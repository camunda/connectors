/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A document known to the agent, as persisted in the agent context: its stable id, where it entered
 * the conversation and its serialized document reference.
 *
 * <p>{@code reference} is the complete Camunda document reference JSON exactly as the standard
 * {@code DocumentSerializer} emits it ({@code camunda.document.type}, {@code storeId}, {@code
 * documentId}, {@code contentHash}, {@code metadata} for Camunda documents; {@code url} and {@code
 * name} for external ones; {@code content} for inline ones). It is kept as a plain map so reading
 * the registry needs no {@code DocumentFactory}. For external documents it includes the URL, and
 * for inline documents the full content, so it must never be rendered to the model.
 *
 * <p>The file name and content type the model is shown are not stored, they are read from the
 * reference.
 *
 * @param id the stable id the registry is keyed by
 * @param source where the document entered the conversation
 * @param reference the document reference JSON
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentRegistryEntry(
    DocumentId id, DocumentSource source, Map<String, Object> reference) {

  public DocumentRegistryEntry {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(reference, "reference must not be null");
    reference = Collections.unmodifiableMap(new LinkedHashMap<>(reference));
  }

  /**
   * Reads the document from its reference.
   *
   * @param objectMapper the mapper that binds a document reference to a {@link Document}
   * @throws IllegalArgumentException if the reference cannot be read as a document
   */
  public Document toDocument(ObjectMapper objectMapper) {
    return objectMapper.convertValue(reference, Document.class);
  }
}
