/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.api.document.Document;
import org.jspecify.annotations.Nullable;

/**
 * Creates the {@link DocumentReferenceContent} blocks of documents and registry entries, deriving
 * the id and the attributes the model is shown with {@link DocumentHandle}.
 */
public final class DocumentReferences {

  private DocumentReferences() {}

  /**
   * Creates the block of a document that is a whole tool result, the short tag the model sees for
   * it: it has no source and the delivery {@code reference}.
   */
  public static DocumentReferenceContent from(Document document) {
    return from(document, null);
  }

  /**
   * Creates the block of a document with the delivery {@code reference}.
   *
   * @param source where the document entered the conversation, or {@code null} for none
   */
  public static DocumentReferenceContent from(Document document, @Nullable DocumentSource source) {
    return new DocumentReferenceContent(
        DocumentHandle.idFor(document),
        document,
        DocumentHandle.fileNameFor(document),
        DocumentHandle.contentTypeFor(document),
        DocumentHandle.sizeFor(document),
        source,
        DocumentDelivery.REFERENCE,
        null);
  }

  /**
   * The result with every document that is the whole content of the result (a top-level {@link
   * DocumentContent}) as a {@link DocumentReferenceContent} without a source, the tag the model
   * sees for it. Documents nested in an {@code ObjectContent} stay documents. A result that holds
   * no top-level document is returned as it is, so applying this twice gives the same result.
   */
  public static ToolCallResultContent withDocumentReferences(ToolCallResultContent result) {
    if (result.content().stream().noneMatch(DocumentContent.class::isInstance)) {
      return result;
    }
    return result.withContent(
        result.content().stream()
            .<Content>map(
                block ->
                    block instanceof DocumentContent documentContent
                        ? from(documentContent.document())
                        : block)
            .toList());
  }
}
