/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistryEntry;
import io.camunda.connector.agenticai.aiagent.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.api.document.Document;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jspecify.annotations.Nullable;

/**
 * A document that is part of the conversation, as persisted: the document is identified by its
 * {@link DocumentHandle#idFor stable id} and its content is read from the document registry when
 * the model is called. The block carries what the model is told about the document, never a store
 * id, content hash, other metadata, URL (an external URL may be a presigned one) or message id.
 *
 * <p>The model sees the block as a self-closing XML tag (see {@link #toTag()}), e.g.:
 *
 * <pre>{@code
 * <doc id="25ece9fa-aeea-..." fileName="report.pdf" contentType="application/pdf" size="48213" toolName="search" toolCallId="call_abc" />
 * }</pre>
 *
 * <p>Null or blank attributes are omitted; {@code toolName} and {@code toolCallId} are only present
 * for documents that came from a tool result, {@code source} ({@code user_message} or {@code
 * event}) only for the other documents. A file name that equals the id (a document stored without a
 * name of its own) is dropped, it would only repeat the id. {@code size} (bytes) is present for a
 * Camunda document whose reference metadata carries one; it is never looked up by reading the
 * document.
 *
 * <p>A tool result that is a document holds the block without a source, the short tag the model
 * sees for a document of a tool result. A document nested in the object of a tool result stays a
 * document.
 *
 * <p>The {@code delivery} tells how the document reaches the model: with {@link
 * DocumentDelivery#REFERENCE} the model sees the tag only, with {@link DocumentDelivery#CONTENT}
 * the content of the document is added next to the tag. It is stored with every block, so a block
 * with the delivery {@code content} keeps it for as long as it is in the context window. Provider
 * chat models never see this block: it is expanded to its tag text and, for the delivery {@code
 * content}, the document content before the request is built.
 *
 * @param id the stable id of the document
 * @param fileName the file name the model is shown, if known
 * @param contentType the content type the model is shown, if known
 * @param size the size in bytes the model is shown, if known
 * @param source where a user message or event document came from, {@code null} for a tool call
 *     document
 * @param toolName the name of the tool that returned the document
 * @param toolCallId the id of the tool call that returned the document
 * @param delivery how the document reaches the model, {@link DocumentDelivery#REFERENCE} if not
 *     given
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentReferenceContent(
    String id,
    @Nullable String fileName,
    @Nullable String contentType,
    @Nullable Long size,
    @Nullable String source,
    @Nullable String toolName,
    @Nullable String toolCallId,
    DocumentDelivery delivery,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, Object> metadata)
    implements Content {

  public DocumentReferenceContent {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("Document id cannot be null or empty");
    }
    if (fileName != null && fileName.equals(id)) {
      fileName = null;
    }
    if (delivery == null) {
      delivery = DocumentDelivery.REFERENCE;
    }
  }

  public DocumentReferenceContent(
      String id,
      @Nullable String fileName,
      @Nullable String contentType,
      @Nullable Long size,
      @Nullable String source,
      @Nullable String toolName,
      @Nullable String toolCallId,
      DocumentDelivery delivery) {
    this(id, fileName, contentType, size, source, toolName, toolCallId, delivery, null);
  }

  /** Creates the block of a document and where it entered the conversation. */
  public static DocumentReferenceContent from(
      Document document, DocumentSource documentSource, DocumentDelivery delivery) {
    return new DocumentReferenceContent(
        DocumentHandle.idFor(document),
        DocumentHandle.fileNameFor(document),
        DocumentHandle.contentTypeFor(document),
        DocumentHandle.sizeFor(document),
        documentSource.type().tagValue(),
        documentSource.toolName(),
        documentSource.toolCallId(),
        delivery);
  }

  /**
   * Creates the block of a document and where it entered the conversation, with the delivery {@code
   * reference}.
   */
  public static DocumentReferenceContent from(Document document, DocumentSource documentSource) {
    return from(document, documentSource, DocumentDelivery.REFERENCE);
  }

  /** Creates the block of a document without a source, with the delivery {@code reference}. */
  public static DocumentReferenceContent from(Document document) {
    return new DocumentReferenceContent(
        DocumentHandle.idFor(document),
        DocumentHandle.fileNameFor(document),
        DocumentHandle.contentTypeFor(document),
        DocumentHandle.sizeFor(document),
        null,
        null,
        null,
        DocumentDelivery.REFERENCE);
  }

  /** Creates the block of a registry entry. */
  public static DocumentReferenceContent from(
      DocumentRegistryEntry entry, DocumentDelivery delivery) {
    final var documentSource = entry.source();
    return new DocumentReferenceContent(
        entry.id(),
        DocumentHandle.fileNameFor(entry.reference()),
        DocumentHandle.contentTypeFor(entry.reference()),
        DocumentHandle.sizeFor(entry.reference()),
        documentSource.type().tagValue(),
        documentSource.toolName(),
        documentSource.toolCallId(),
        delivery);
  }

  /** Creates the block of a registry entry, with the delivery {@code reference}. */
  public static DocumentReferenceContent from(DocumentRegistryEntry entry) {
    return from(entry, DocumentDelivery.REFERENCE);
  }

  /** Returns the block with the given delivery. */
  public DocumentReferenceContent withDelivery(DocumentDelivery delivery) {
    return new DocumentReferenceContent(
        id, fileName, contentType, size, source, toolName, toolCallId, delivery, metadata);
  }

  /** The tag the model sees for the document. */
  public String toTag() {
    final var sb = new StringBuilder("<doc");
    appendAttribute(sb, "id", id);
    appendAttribute(sb, "fileName", fileName);
    appendAttribute(sb, "contentType", contentType);
    appendAttribute(sb, "size", size == null ? null : String.valueOf(size));
    appendAttribute(sb, "source", source);
    appendAttribute(sb, "toolName", toolName);
    appendAttribute(sb, "toolCallId", toolCallId);
    return sb.append(" />").toString();
  }

  /**
   * The failure of a provider chat model that is handed this block: the block must have been
   * expanded to its tag text and document content before the request is built.
   */
  public IllegalStateException notExpandedFailure() {
    return new IllegalStateException(
        "DocumentReferenceContent of document '%s' must be expanded to its tag text and document content before the request"
            .formatted(id));
  }

  private static void appendAttribute(StringBuilder sb, String name, @Nullable String value) {
    if (StringUtils.isNotBlank(value)) {
      sb.append(" %s=\"%s\"".formatted(name, StringEscapeUtils.escapeXml10(value)));
    }
  }
}
