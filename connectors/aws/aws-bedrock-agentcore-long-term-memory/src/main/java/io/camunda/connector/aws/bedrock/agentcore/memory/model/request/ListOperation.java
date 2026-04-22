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

@TemplateSubType(id = "list", label = "List Memory Records")
public record ListOperation(
    @TemplateProperty(
            id = "list.memoryStrategyId",
            group = "list",
            label = "Memory strategy ID",
            description = "Optional. Filter memory records by a specific extraction strategy.",
            optional = true)
        String memoryStrategyId,
    @TemplateProperty(
            id = "list.maxResults",
            group = "list",
            label = "Max results",
            description = "Maximum number of results to return (1–100). Defaults to 20.",
            defaultValue = "20",
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            optional = true)
        @Min(1)
        @Max(100)
        Integer maxResults,
    @TemplateProperty(
            id = "list.nextToken",
            group = "list",
            label = "Next token",
            description = "Pagination token from a previous response to fetch the next page.",
            optional = true)
        String nextToken)
    implements MemoryOperation {}
