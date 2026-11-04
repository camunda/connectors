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
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A document that is part of the conversation, as persisted: the block holds the stable id of the
 * document and how it reaches the model. Everything the model reads about the document, the file
 * name, content type, size and origin, is read from the document workspace when the block is
 * rendered, and the content is read from the workspace when the model is called. The block never
 * carries a store id, content hash, other metadata or URL (an external URL may be a presigned one).
 *
 * <p>The model sees the block as a self-closing XML tag rendered from the workspace document, e.g.:
 *
 * <pre>{@code
 * <doc id="doc_3f9a1c0b27de" fileName="report.pdf" contentType="application/pdf" size="48213" toolName="search" toolCallId="call_abc" />
 * }</pre>
 *
 * <p>The {@code delivery} tells how the document reaches the model: with {@link
 * DocumentDelivery#REFERENCE} the model sees the tag only, with {@link DocumentDelivery#CONTENT}
 * the content of the document is added next to the tag. It is stored with every block, so a block
 * with the delivery {@code content} keeps it for as long as it is in the context window. Provider
 * chat models never see this block: it is expanded to its tag text and, for the delivery {@code
 * content}, the document content before the request is built.
 *
 * <p>A tool result that is a document holds this block; the model sees the short tag for it, which
 * has no origin attributes. A document nested in the object of a tool result stays a document.
 *
 * @param id the stable id of the document
 * @param delivery how the document reaches the model, {@link DocumentDelivery#REFERENCE} if not
 *     given
 * @param metadata the metadata of the content block
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentReferenceContent(
    DocumentId id,
    DocumentDelivery delivery,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, Object> metadata)
    implements Content {

  public DocumentReferenceContent {
    if (id == null) {
      throw new IllegalArgumentException("Document id cannot be null or empty");
    }
    if (delivery == null) {
      delivery = DocumentDelivery.REFERENCE;
    }
  }

  /** Returns the block with the given delivery. */
  public DocumentReferenceContent withDelivery(DocumentDelivery delivery) {
    return new DocumentReferenceContent(id, delivery, metadata);
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
}
