/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A document the agent knows, as persisted in the document registry.
 *
 * <p>{@code reference} is the complete Camunda document reference JSON exactly as the standard
 * {@code DocumentSerializer} emits it ({@code camunda.document.type}, {@code storeId}, {@code
 * documentId}, {@code contentHash}, {@code metadata} for Camunda documents; {@code url} and {@code
 * name} for external ones; {@code content} for inline ones). It is kept as a plain map so reading
 * the registry needs no {@code DocumentFactory}. For external documents it includes the URL, and
 * for inline documents the full content, so it must never be rendered to the model.
 *
 * @param id the {@link DocumentHandle#idFor stable id} the registry is keyed by
 * @param toolName the tool that returned the document, {@code null} for user prompt and event
 *     documents
 * @param toolCallId the tool call that returned the document, {@code null} for user prompt and
 *     event documents
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentRegistryEntry(
    String id,
    Map<String, Object> reference,
    @Nullable String fileName,
    @Nullable String contentType,
    @Nullable String toolName,
    @Nullable String toolCallId) {

  private static final ObjectMapper REFERENCE_MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .registerModule(new JacksonModuleDocumentSerializer())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  private static final TypeReference<LinkedHashMap<String, Object>> REFERENCE_TYPE =
      new TypeReference<>() {};

  public DocumentRegistryEntry {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(reference, "reference must not be null");
    reference = Collections.unmodifiableMap(new LinkedHashMap<>(reference));
  }

  public static DocumentRegistryEntry from(IngestedDocument ingested) {
    final var document = ingested.document();
    final var metadata = document.metadata();
    return new DocumentRegistryEntry(
        DocumentHandle.idFor(document),
        serializeReference(document),
        metadata != null ? metadata.getFileName() : null,
        metadata != null ? metadata.getContentType() : null,
        ingested.toolName(),
        ingested.toolCallId());
  }

  private static Map<String, Object> serializeReference(Document document) {
    return REFERENCE_MAPPER.convertValue(document, REFERENCE_TYPE);
  }
}
