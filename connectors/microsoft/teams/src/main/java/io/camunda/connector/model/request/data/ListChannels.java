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
            tooltip = "The Microsoft Teams group ID.")
        String groupId,
    @TemplateProperty(
            group = "data",
            id = "listChannels.filter",
            label = "Filter",
            optional = true,
            tooltip =
                "Sets the search filter. See the <a href=\"https://learn.microsoft.com/en-us/graph/filter-query-parameter\">filter query parameter</a> reference.")
        String filter)
    implements ChannelData {}
