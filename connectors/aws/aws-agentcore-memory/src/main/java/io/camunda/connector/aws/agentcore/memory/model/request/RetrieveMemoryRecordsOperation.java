/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.agentcore.memory.model.request;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@TemplateSubType(id = "retrieve", label = "Retrieve memory records")
public record RetrieveMemoryRecordsOperation(
    @FEEL
        @NotBlank
        @TemplateProperty(
            group = "retrieve",
            label = "Namespace",
            description =
                "Namespace prefix to scope the search. Use a FEEL expression to substitute "
                    + "the actor ID, e.g. <code>\"/strategies/my-strategy/actors/\" + actorId</code>.",
            feel = FeelMode.optional,
            constraints = @PropertyConstraints(notEmpty = true))
        String namespace,
    @FEEL
        @NotBlank
        @TemplateProperty(
            group = "retrieve",
            label = "Search query",
            description =
                "Semantic search query. Describe what you want to retrieve, "
                    + "e.g. <code>\"user preferences and past issues\"</code>.",
            feel = FeelMode.required)
        String searchQuery,
    @FEEL
        @TemplateProperty(
            group = "retrieve",
            label = "Memory strategy ID",
            description = "Optional. Restrict results to a specific memory strategy.",
            feel = FeelMode.optional,
            optional = true)
        String memoryStrategyId,
    @TemplateProperty(
            group = "retrieve",
            label = "Top K",
            description =
                "Optional. Number of top-scoring results considered during semantic ranking.",
            optional = true)
        Integer topK,
    @FEEL
        @TemplateProperty(
            group = "retrieve",
            label = "Metadata filters",
            description =
                "Optional list of metadata key/value filters. "
                    + "Example: <code>[{key: \"category\", operator: \"EQUALS_TO\", value: \"preferences\"}]</code>",
            feel = FeelMode.required,
            optional = true)
        List<MetadataFilter> metadataFilters,
    @FEEL
        @TemplateProperty(
            group = "retrieve",
            label = "Next token",
            description =
                "Pagination token returned by a previous call to retrieve additional pages.",
            feel = FeelMode.optional,
            optional = true)
        String nextToken)
    implements MemoryOperation {}
