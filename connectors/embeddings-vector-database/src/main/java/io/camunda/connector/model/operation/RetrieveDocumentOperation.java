/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.operation;

import static io.camunda.connector.model.operation.RetrieveDocumentOperation.RETRIEVE_DOCUMENT_OPERATION;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DefaultValueType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@TemplateSubType(label = "Retrieve document", id = RETRIEVE_DOCUMENT_OPERATION)
public record RetrieveDocumentOperation(
    @NotBlank
        @Size(max = 200)
        @TemplateProperty(
            group = "query",
            id = "query.query",
            label = "Search query",
            description = "Document lookup query")
        String query,
    @Min(1)
        @TemplateProperty(
            group = "query",
            id = "query.maxResults",
            label = "Max results",
            description = "Limit number of returned results",
            defaultValue = "5",
            defaultValueType = DefaultValueType.Number)
        Integer documentLimit,
    @TemplateProperty(
            group = "query",
            id = "query.minScore",
            label = "Min score",
            optional = true,
            description =
                "Minimal vector similarity score for result to be included. Must be between 0 and 1 floating point value, e.g. 0.6. Incorrect and empty value resolves to 0.0",
            defaultValueType = DefaultValueType.Number)
        Double minScore)
    implements VectorDatabaseConnectorOperation {
  @TemplateProperty(ignore = true)
  public static final String RETRIEVE_DOCUMENT_OPERATION = "retrieveDocumentOperation";
}
