/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_INLINE_DOCUMENT_TOO_LARGE;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference.InlineDocumentReference;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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
 * <p>The file name and content type the model is shown are not stored: {@link
 * DocumentHandle#fileNameFor(Map)} and {@link DocumentHandle#contentTypeFor(Map)} read them from
 * the reference.
 *
 * <p>The reference of an inline document holds its content in the registry, in the tool result and
 * in the response documents, all of which are part of the job completion. {@link #from} therefore
 * rejects an inline document whose content exceeds {@link #MAX_INLINE_CONTENT_LENGTH}.
 *
 * @param id the {@link DocumentHandle#idFor stable id} the registry is keyed by
 * @param source where the document entered the conversation
 * @param reference the document reference JSON
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentRegistryEntry(
    String id, DocumentSource source, Map<String, Object> reference) {

  /**
   * The largest content of an inline document the registry accepts, counted in characters of the
   * content the reference holds (base64 for binary documents, text otherwise). A larger document is
   * rejected with {@code ERROR_CODE_INLINE_DOCUMENT_TOO_LARGE} so that the job completion, which
   * carries the content several times, stays well below the message size limit of the broker.
   */
  public static final int MAX_INLINE_CONTENT_LENGTH = 1024 * 1024;

  // the persisted reference keeps the numeric form of durations in the custom properties
  private static final ObjectMapper REFERENCE_MAPPER =
      ConnectorsObjectMapperSupplier.getCopy()
          .registerModule(new JacksonModuleDocumentSerializer())
          .enable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
          .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  private static final TypeReference<LinkedHashMap<String, Object>> REFERENCE_TYPE =
      new TypeReference<>() {};

  public DocumentRegistryEntry {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(reference, "reference must not be null");
    reference = Collections.unmodifiableMap(new LinkedHashMap<>(reference));
  }

  /**
   * Creates the entry of an ingested document.
   *
   * @throws ConnectorException with {@code ERROR_CODE_INLINE_DOCUMENT_TOO_LARGE} if the document is
   *     an inline document with more than {@link #MAX_INLINE_CONTENT_LENGTH} characters of content
   */
  public static DocumentRegistryEntry from(IngestedDocument ingested) {
    final var document = ingested.document();
    rejectLargeInlineContent(document);
    return new DocumentRegistryEntry(
        DocumentHandle.idFor(document), ingested.source(), serializeReference(document));
  }

  private static void rejectLargeInlineContent(Document document) {
    if (document.reference() instanceof InlineDocumentReference inline
        && inline.content() != null
        && inline.content().length() > MAX_INLINE_CONTENT_LENGTH) {
      final var name = DocumentHandle.fileNameFor(document);
      throw new ConnectorException(
          ERROR_CODE_INLINE_DOCUMENT_TOO_LARGE,
          ("The inline document '%s' is too large: its content has %d characters, the limit is %d."
                  + " Model the process so that the file is returned as a Camunda document instead of"
                  + " inline content.")
              .formatted(
                  name != null ? name : DocumentHandle.idFor(document),
                  inline.content().length(),
                  MAX_INLINE_CONTENT_LENGTH));
    }
  }

  private static Map<String, Object> serializeReference(Document document) {
    return REFERENCE_MAPPER.convertValue(document, REFERENCE_TYPE);
  }
}
