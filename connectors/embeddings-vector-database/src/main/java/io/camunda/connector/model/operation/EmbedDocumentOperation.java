/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.operation;

import static io.camunda.connector.model.operation.EmbedDocumentOperation.EMBED_DOCUMENT_OPERATION;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.java.annotation.TemplateDocumentProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.model.embedding.splitter.DocumentSplitter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * @param documentSource legacy source discriminator, replaced by the unified document source
 *     dropdown on {@code newDocuments} — inline content covers what {@code PlainText} used to. Kept
 *     as a hidden runtime-only input so element templates up to version 3 keep working: when
 *     present it still decides which path {@code DefaultTextSegmentExtractor} takes. Templates from
 *     version 4 on never set it, and the path follows whichever input is populated.
 * @param documentSourceFromProcessVariable legacy plain-text input, kept for the same reason as
 *     {@code documentSource}.
 */
@TemplateSubType(
    label = "Embed document",
    id = EMBED_DOCUMENT_OPERATION,
    description = "Embed a document or text into a vector database for semantic search",
    keywords = {
      "embed document",
      "vectorize",
      "store embedding",
      "index document",
      "semantic index"
    })
public record EmbedDocumentOperation(
    @TemplateProperty(ignore = true) EmbedDocumentSource documentSource,
    @TemplateProperty(ignore = true) String documentSourceFromProcessVariable,
    @TemplateDocumentProperty(
            group = "document",
            id = "newDocuments",
            binding = @TemplateProperty.PropertyBinding(name = "newDocuments"))
        List<Document> newDocuments,
    @NotNull @Valid DocumentSplitter documentSplitter)
    implements VectorDatabaseConnectorOperation {
  @TemplateProperty(ignore = true)
  public static final String EMBED_DOCUMENT_OPERATION = "embedDocumentOperation";
}
