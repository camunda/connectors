/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.operation;

import static io.camunda.connector.model.operation.EmbedDocumentOperation.EMBED_DOCUMENT_OPERATION;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.model.embedding.splitter.DocumentSplitter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@TemplateSubType(label = "Embed document", id = EMBED_DOCUMENT_OPERATION)
public record EmbedDocumentOperation(
    @NotNull
        @TemplateProperty(
            group = "document",
            id = "documentSource",
            label = "Document source",
            feel = FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            description = "Whether you want to embed a Camunda document file or plain text",
            defaultValue = "CamundaDocument")
        EmbedDocumentSource documentSource,
    @TemplateProperty(
            group = "document",
            id = "documentSourceFromProcessVariable",
            label = "Plain text to embed",
            feel = FeelMode.optional,
            constraints = @PropertyConstraints(notEmpty = true),
            condition =
                @PropertyCondition(
                    property = "vectorDatabaseConnectorOperation.documentSource",
                    equals = "PlainText"))
        String documentSourceFromProcessVariable,
    @TemplateProperty(
            label = "Documents",
            group = "document",
            id = "newDocuments",
            feel = FeelMode.required,
            binding = @TemplateProperty.PropertyBinding(name = "newDocuments"),
            constraints = @PropertyConstraints(notEmpty = true),
            condition =
                @PropertyCondition(
                    property = "vectorDatabaseConnectorOperation.documentSource",
                    equals = "CamundaDocument"))
        List<Document> newDocuments,
    @NotNull @Valid DocumentSplitter documentSplitter)
    implements VectorDatabaseConnectorOperation {
  @TemplateProperty(ignore = true)
  public static final String EMBED_DOCUMENT_OPERATION = "embedDocumentOperation";
}
