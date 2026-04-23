/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateChannelRequest(
    @NotNull @Valid AppIntegrationsConfiguration configuration,
    @NotEmpty
        @TemplateProperty(
            group = "channel",
            label = "Team ID",
            description = "ID of the Microsoft Teams team to create the channel in.")
        String teamId,
    @NotEmpty
        @TemplateProperty(
            group = "channel",
            label = "Channel name",
            description = "Display name for the new channel (max 50 characters).")
        String displayName,
    @TemplateProperty(
            group = "channel",
            label = "Description",
            description = "Optional description for the channel.",
            optional = true)
        String description,
    @TemplateProperty(
            group = "channel",
            label = "Channel type",
            description =
                "Membership type: standard (visible to all), private (invite-only), or shared.",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(label = "Standard", value = "standard"),
              @TemplateProperty.DropdownPropertyChoice(label = "Private", value = "private"),
              @TemplateProperty.DropdownPropertyChoice(label = "Shared", value = "shared")
            },
            defaultValue = "standard")
        String membershipType) {}
