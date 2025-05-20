/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.operation;

import static io.camunda.connector.model.operation.EmbedDocumentOperation.OPERATION_EMBED_DOCUMENT;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.model.embedding.splitter.DocumentSplitter;
import io.camunda.document.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@TemplateSubType(label = "Embed document", id = OPERATION_EMBED_DOCUMENT)
public record EmbedDocumentOperation(
    @NotBlank
        @TemplateProperty(
            group = "document",
            id = "documentSource",
            label = "Document source",
            feel = Property.FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            description = "Whether you want to embed a Camunda document file or plain text",
            defaultValue = "CamundaDocument")
        EmbedDocumentSource documentSource,
    @NotBlank
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
    @NotBlank
        @TemplateProperty(
            label = "Documents",
            group = "document",
            id = "newDocuments",
            feel = Property.FeelMode.required,
            binding = @TemplateProperty.PropertyBinding(name = "newDocuments"),
            constraints = @PropertyConstraints(notEmpty = true),
            condition =
                @PropertyCondition(
                    property = "vectorDatabaseConnectorOperation.documentSource",
                    equals = "CamundaDocument"))
        List<Document> newDocuments,
    @NotNull DocumentSplitter documentSplitter)
    implements VectorDatabaseConnectorOperation {
  @TemplateProperty(ignore = true)
  public static final String OPERATION_EMBED_DOCUMENT = "OPERATION_EMBED_DOCUMENT";
}
