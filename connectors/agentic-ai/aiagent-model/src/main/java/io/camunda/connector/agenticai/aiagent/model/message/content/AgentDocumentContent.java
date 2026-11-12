/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A document the agent knows, as persisted: the block holds the stable id of the document and how
 * it reaches the model. Everything the model reads about the document, the file name, content type
 * and size, is read from the documents of the agent context when the block is rendered, and the
 * content is read from there when the model is called. The block never carries a store id, content
 * hash, other metadata or URL (an external URL may be a presigned one).
 *
 * <p>It is the persisted form of a document for an agent instance that tracks its documents, and it
 * never reaches a chat model provider: it is expanded to its handle text and, for the delivery
 * {@code content}, a {@link DocumentContent} before the request is built. Only the agent sets it.
 * The content for the model comes from the agent and its delivery, never from a block a tool
 * authors. A document the agent has not seen before is a {@link DocumentContent}.
 *
 * <p>The model sees the block as the {@link AgentDocumentHandle} of the document, e.g.:
 *
 * <pre>{@code
 * {"type":"camunda.agenticai.document","id":"doc_3f9a1c0b27de","fileName":"report.pdf","contentType":"application/pdf","size":48213}
 * }</pre>
 *
 * <p>The {@code delivery} tells how the document reaches the model: with {@link
 * DocumentDelivery#REFERENCE} the model sees the handle only, with {@link DocumentDelivery#CONTENT}
 * the content of the document is added next to the handle. It is stored with every block, so a
 * block with the delivery {@code content} keeps it for as long as it is in the context window.
 *
 * <p>A tool result that is a document holds this block; the model sees its handle for it. A
 * document nested in the object of a tool result stays a document.
 *
 * @param id the stable id of the document
 * @param delivery how the document reaches the model
 * @param metadata the metadata of the content block
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentDocumentContent(
    AgentDocumentId id,
    DocumentDelivery delivery,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, Object> metadata)
    implements Content {

  public AgentDocumentContent {
    if (id == null) {
      throw new IllegalArgumentException("Document id cannot be null or empty");
    }
    if (delivery == null) {
      delivery = DocumentDelivery.REFERENCE;
    }
  }

  /** The block of the document with the delivery {@link DocumentDelivery#REFERENCE}. */
  public static AgentDocumentContent of(AgentDocumentId id) {
    return new AgentDocumentContent(id, DocumentDelivery.REFERENCE, null);
  }

  /** Returns the block with the given delivery. */
  public AgentDocumentContent withDelivery(DocumentDelivery delivery) {
    return new AgentDocumentContent(id, delivery, metadata);
  }
}
