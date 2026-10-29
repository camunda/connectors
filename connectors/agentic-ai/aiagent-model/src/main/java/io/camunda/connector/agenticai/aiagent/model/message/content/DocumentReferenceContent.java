/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.api.document.Document;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jspecify.annotations.Nullable;

/**
 * A document that is part of the conversation, as persisted: the document is identified by its
 * stable id and the block holds the document itself, which is read when the model is called. The
 * model is told the attributes of the tag only, never the store id, content hash, other metadata or
 * URL of the document (an external URL may be a presigned one).
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
 * @param document the document; it is persisted with the block and never shown to the model
 * @param fileName the file name the model is shown, if known
 * @param contentType the content type the model is shown, if known
 * @param size the size in bytes the model is shown, if known
 * @param source where the document entered the conversation; {@code null} for the block of a
 *     document that is a whole tool result, whose source is the tool call of the result. The tag
 *     shows the type of a user message or event source and the tool name and tool call id of a tool
 *     call source; the message id is not part of it
 * @param delivery how the document reaches the model, {@link DocumentDelivery#REFERENCE} if not
 *     given
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentReferenceContent(
    DocumentId id,
    Document document,
    @Nullable String fileName,
    @Nullable String contentType,
    @Nullable Long size,
    @Nullable DocumentSource source,
    DocumentDelivery delivery,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, Object> metadata)
    implements Content {

  public DocumentReferenceContent {
    if (id == null) {
      throw new IllegalArgumentException("Document id cannot be null or empty");
    }
    if (document == null) {
      throw new IllegalArgumentException("Document cannot be null");
    }
    if (fileName != null && fileName.equals(id.value())) {
      fileName = null;
    }
    if (delivery == null) {
      delivery = DocumentDelivery.REFERENCE;
    }
  }

  /**
   * Blocks are equal when everything but the document is equal: the id stands for the document,
   * which has no equality of its own, so a block read back from a store equals the stored one.
   */
  @Override
  public boolean equals(Object other) {
    return other instanceof DocumentReferenceContent that
        && id.equals(that.id)
        && Objects.equals(fileName, that.fileName)
        && Objects.equals(contentType, that.contentType)
        && Objects.equals(size, that.size)
        && Objects.equals(source, that.source)
        && delivery == that.delivery
        && Objects.equals(metadata, that.metadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, fileName, contentType, size, source, delivery, metadata);
  }

  /** Returns the block with the given delivery. */
  public DocumentReferenceContent withDelivery(DocumentDelivery delivery) {
    return new DocumentReferenceContent(
        id, document, fileName, contentType, size, source, delivery, metadata);
  }

  /** The tag the model sees for the document. */
  public String toTag() {
    final var sb = new StringBuilder("<doc");
    appendAttribute(sb, "id", id.value());
    appendAttribute(sb, "fileName", fileName);
    appendAttribute(sb, "contentType", contentType);
    appendAttribute(sb, "size", size == null ? null : String.valueOf(size));
    appendAttribute(sb, "source", source == null ? null : source.type().tagValue());
    appendAttribute(sb, "toolName", source == null ? null : source.toolName());
    appendAttribute(sb, "toolCallId", source == null ? null : source.toolCallId());
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
