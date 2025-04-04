/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.modes;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.model.embedding.splitter.DocumentSplitter;
import io.camunda.document.Document;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@TemplateSubType(label = "Embed document", id = EmbeddingsVectorMode.MODE_EMBED_DOCUMENT)
public record EmbedDocumentMode(
    @NotNull
        @TemplateProperty(
            label = "documents",
            group = "document",
            id = "document.newDocuments",
            feel = Property.FeelMode.required,
            binding = @TemplateProperty.PropertyBinding(name = "document.newDocuments"))
        List<Document> newDocuments,
    @NotNull DocumentSplitter documentSplitter)
    implements EmbeddingsVectorMode {}
