/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.agentcore.memory.model.request;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(id = "retrieve", label = "Retrieve Memory Records")
public record RetrieveOperation(
    @NotBlank
        @TemplateProperty(
            group = "retrieve",
            label = "Search query",
            description =
                "Semantic search query to find relevant memory records (up to 10,000 characters).")
        String query,
    @TemplateProperty(
            id = "retrieve.memoryStrategyId",
            group = "retrieve",
            label = "Memory strategy ID",
            description =
                "Optional. Limits the search to memories created by a specific extraction strategy.",
            optional = true)
        String memoryStrategyId,
    @TemplateProperty(
            id = "retrieve.maxResults",
            group = "retrieve",
            label = "Max results",
            description = "Maximum number of results to return (1–100). Defaults to 10.",
            defaultValue = "10",
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            optional = true)
        @Min(1)
        @Max(100)
        Integer maxResults,
    @TemplateProperty(
            id = "retrieve.nextToken",
            group = "retrieve",
            label = "Pagination token",
            description = "Pagination token from a previous response to fetch the next page.",
            optional = true)
        String nextToken)
    implements MemoryOperation {}
