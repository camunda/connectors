/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
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
 * contentType}, {@code size}; a null or blank value is omitted. The model passes a document to a
 * tool with the {@code type} and the {@code id}; the other fields are information for the model
 * only. The handle holds no origin: the message it sits in shows where the document came from.
 *
 * <p>The handle is a plain record; the code that shows it to the model writes it as compact JSON
 * text, stable across requests (prompt caching), or as a JSON object with the same keys in the same
 * order, for a handle that sits inside an object.
 *
 * @param type always {@link #TYPE}
 * @param id the id of the document
 * @param fileName the file name, if known
 * @param contentType the content type, if known without reading the content
 * @param size the size in bytes, if known without reading the content
 */
@JsonPropertyOrder({"type", "id", "fileName", "contentType", "size"})
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AgentDocumentHandle(
    String type,
    String id,
    @Nullable String fileName,
    @Nullable String contentType,
    @Nullable Long size) {

  /** The value of the {@code type} of every handle. */
  public static final String TYPE = "camunda.agenticai.document";

  /** The JSON field of the {@link #type()}. */
  public static final String FIELD_TYPE = "type";

  /** The JSON field of the {@link #id()}. */
  public static final String FIELD_ID = "id";

  public AgentDocumentHandle {
    if (!TYPE.equals(type)) {
      throw new IllegalArgumentException("A document handle has the type '" + TYPE + "'");
    }
    Objects.requireNonNull(id, "id must not be null");
    fileName = StringUtils.defaultIfBlank(fileName, null);
    contentType = StringUtils.defaultIfBlank(contentType, null);
  }

  /**
   * The handle of the document with the given id, read from the known documents; the id only for a
   * document the agent does not know.
   */
  public static AgentDocumentHandle of(AgentDocuments documents, AgentDocumentId id) {
    return documents.find(id).map(AgentDocumentHandle::of).orElseGet(() -> of(id));
  }

  /** The handle of a document the agent does not know: the id only. */
  public static AgentDocumentHandle of(AgentDocumentId id) {
    return new AgentDocumentHandle(TYPE, id.value(), null, null, null);
  }

  /** The handle of a document known to the agent. */
  public static AgentDocumentHandle of(AgentDocument document) {
    return of(document.id(), AgentDocument.Descriptors.of(document.reference()));
  }

  /**
   * The handle of a document, read from its reference without reading its content.
   *
   * @throws UnsupportedDocumentReferenceException if the document is not a Camunda document
   */
  public static AgentDocumentHandle of(Document document) {
    final var id = AgentDocumentId.of(document);
    return of(id, AgentDocument.Descriptors.of((CamundaDocumentReference) document.reference()));
  }

  private static AgentDocumentHandle of(AgentDocumentId id, AgentDocument.Descriptors descriptors) {
    return new AgentDocumentHandle(
        TYPE, id.value(), descriptors.fileName(), descriptors.contentType(), descriptors.size());
  }
}
