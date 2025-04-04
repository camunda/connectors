/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.modes;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(label = "Retrieve document", id = EmbeddingsVectorMode.MODE_RETRIEVE_DOCUMENT)
public record RetrieveDocumentMode(
    @NotBlank
        @TemplateProperty(
            group = "query",
            id = "query.query",
            label = "Query",
            description = "Document lookup query")
        String query,
    @NotBlank
        @TemplateProperty(
            group = "query",
            id = "query.maxResults",
            label = "Max results",
            description = "Limit number of returned documents",
            defaultValue = "5")
        String documentLimit)
    implements EmbeddingsVectorMode {}
