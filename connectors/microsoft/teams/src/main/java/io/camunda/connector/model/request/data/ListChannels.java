/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.request.data;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.model.MSTeamsMethodTypes;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(label = "List channels", id = MSTeamsMethodTypes.LIST_CHANNELS)
public record ListChannels(
    @NotBlank
        @TemplateProperty(
            group = "data",
            id = "listChannels.groupId",
            label = "Group ID",
            description = "The group ID for teams")
        String groupId,
    @TemplateProperty(
            group = "data",
            id = "listChannels.filter",
            label = "Filter",
            optional = true,
            description =
                "Sets the search filter. <a href='https://learn.microsoft.com/en-us/graph/filter-query-parameter'>Learn more about filtering</a>")
        String filter)
    implements ChannelData {}
