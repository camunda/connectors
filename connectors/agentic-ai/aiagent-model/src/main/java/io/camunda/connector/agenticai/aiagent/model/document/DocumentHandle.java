/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * What the model reads for a document: a small JSON object that names the document by its id and
 * tells what is known about it without reading it, e.g.
 *
 * <pre>{@code
 * {"type":"camunda.agenticai.document","id":"doc_0123456789ab","fileName":"a.pdf","contentType":"application/pdf","size":123}
 * }</pre>
 *
 * <p>The keys are written in the order {@code type}, {@code id}, {@code fileName}, {@code
 * contentType}, {@code size}, {@code note}; a null or blank value is omitted. The model passes a
 * document to a tool with the {@code type} and the {@code id}; the other fields are information for
 * the model only. The handle holds no origin: the message it sits in shows where the document came
 * from.
 *
 * <p>The text form is compact JSON, stable across requests (prompt caching). The tree form is a
 * JSON object node with the same keys in the same order, for a handle that sits inside the object
 * of a tool result.
 *
 * @param type always {@link #TYPE}
 * @param id the id of the document
 * @param fileName the file name, if known
 * @param contentType the content type, if known without reading the content
 * @param size the size in bytes, if known without reading the content
 * @param note a sentence about the document for the model, e.g. why its content is withheld
 */
@JsonPropertyOrder({"type", "id", "fileName", "contentType", "size", "note"})
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DocumentHandle(
    String type,
    String id,
    @Nullable String fileName,
    @Nullable String contentType,
    @Nullable Long size,
    @Nullable String note) {

  /** The value of the {@code type} of every handle. */
  public static final String TYPE = "camunda.agenticai.document";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public DocumentHandle {
    if (!TYPE.equals(type)) {
      throw new IllegalArgumentException("A document handle has the type '" + TYPE + "'");
    }
    Objects.requireNonNull(id, "id must not be null");
    fileName = StringUtils.defaultIfBlank(fileName, null);
    contentType = StringUtils.defaultIfBlank(contentType, null);
    note = StringUtils.defaultIfBlank(note, null);
  }

  /**
   * The handle of the document with the given id, read from the known documents; the id only for a
   * document the agent does not know.
   */
  public static DocumentHandle of(AgentDocuments documents, AgentDocumentId id) {
    return documents.find(id).map(DocumentHandle::of).orElseGet(() -> of(id));
  }

  /** The handle of a document the agent does not know: the id only. */
  public static DocumentHandle of(AgentDocumentId id) {
    return new DocumentHandle(TYPE, id.value(), null, null, null, null);
  }

  /** The handle of a document known to the agent. */
  public static DocumentHandle of(AgentDocument document) {
    return of(document.id(), AgentDocument.Descriptors.of(document.reference()));
  }

  /**
   * The handle of a document, read from its reference without reading its content.
   *
   * @throws UnsupportedDocumentReferenceException if the reference type of the document is not
   *     supported
   */
  public static DocumentHandle of(Document document) {
    return of(AgentDocumentId.of(document), AgentDocument.Descriptors.of(document.reference()));
  }

  /** The handle as compact JSON text. */
  public String text() {
    try {
      return MAPPER.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to write the document handle", e);
    }
  }

  /** The handle as a JSON object node. */
  public JsonNode tree() {
    return MAPPER.valueToTree(this);
  }

  /** This handle with the given note. */
  public DocumentHandle withNote(@Nullable String note) {
    return new DocumentHandle(type, id, fileName, contentType, size, note);
  }

  private static DocumentHandle of(AgentDocumentId id, AgentDocument.Descriptors descriptors) {
    return new DocumentHandle(
        TYPE,
        id.value(),
        descriptors.fileName(),
        descriptors.contentType(),
        descriptors.size(),
        null);
  }
}
