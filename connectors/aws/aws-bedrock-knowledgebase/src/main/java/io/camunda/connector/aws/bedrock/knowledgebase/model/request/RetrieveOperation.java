/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.knowledgebase.model.request;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(id = "retrieve", label = "Retrieve from Knowledge Base")
public record RetrieveOperation(
    @FEEL
        @NotBlank
        @TemplateProperty(
            group = "retrieve",
            label = "Query",
            description = "Natural language query to search the knowledge base.")
        String query,
    @TemplateProperty(
            group = "retrieve",
            label = "Number of results",
            description = "Maximum number of results to return (1–100). Defaults to 5.",
            defaultValue = "5",
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            optional = true)
        @Min(1)
        @Max(100)
        Integer numberOfResults)
    implements KnowledgeBaseOperation {}
