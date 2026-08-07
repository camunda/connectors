/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotEmpty;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Where a channel is created. The shared name and description live on {@link CreateChannelRequest}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ChannelPlatform.TeamsChannelPlatform.class, name = "teams"),
  @JsonSubTypes.Type(value = ChannelPlatform.SlackChannelPlatform.class, name = "slack")
})
@TemplateDiscriminatorProperty(
    name = "type",
    group = "channel",
    label = "Platform",
    description = "Where to create the channel",
    defaultValue = "teams")
public sealed interface ChannelPlatform {

  /** Microsoft Teams channel name limit; Slack allows more, so it cannot be a shared constraint. */
  @TemplateProperty(ignore = true)
  int TEAMS_MAX_DISPLAY_NAME_LENGTH = 50;

  @TemplateSubType(id = "teams", label = "Microsoft Teams")
  record TeamsChannelPlatform(
      @NotEmpty
          @TemplateProperty(
              group = "channel",
              label = "Team ID",
              description =
                  "ID of the Microsoft Teams team, or a full Teams URL"
                      + " (the groupId query parameter will be extracted automatically).")
          String teamId,
      @TemplateProperty(
              group = "channel",
              label = "Channel type",
              description =
                  "Membership type: standard (visible to all), private (invite-only), or shared.",
              // Without an explicit Dropdown type the generator keeps a String field and silently
              // drops the choices, since membershipType is not an enum.
              type = PropertyType.Dropdown,
              choices = {
                @TemplateProperty.DropdownPropertyChoice(label = "Standard", value = "standard"),
                @TemplateProperty.DropdownPropertyChoice(label = "Private", value = "private"),
                @TemplateProperty.DropdownPropertyChoice(label = "Shared", value = "shared")
              },
              defaultValue = DEFAULT_MEMBERSHIP_TYPE)
          String membershipType)
      implements ChannelPlatform {

    @TemplateProperty(ignore = true)
    public static final String DEFAULT_MEMBERSHIP_TYPE = "standard";

    public TeamsChannelPlatform {
      teamId = extractGroupId(teamId);
      // Single runtime source of the channel-type default; the template's defaultValue only
      // pre-fills
      // the editor dropdown and is not guaranteed to be present for non-template callers.
      if (membershipType == null || membershipType.isBlank()) {
        membershipType = DEFAULT_MEMBERSHIP_TYPE;
      }
    }

    /**
     * Accepts either a raw groupId or a full Microsoft Teams URL. For a URL, the {@code groupId}
     * query parameter is extracted and percent-decoded; if the input is not a URL or carries no
     * such parameter it is returned unchanged.
     */
    private static String extractGroupId(String input) {
      if (input == null || !input.startsWith("http")) {
        return input;
      }
      try {
        var query = new URI(input).getRawQuery();
        if (query != null) {
          for (var param : query.split("&")) {
            if (param.startsWith("groupId=")) {
              return URLDecoder.decode(
                  param.substring("groupId=".length()), StandardCharsets.UTF_8);
            }
          }
        }
      } catch (URISyntaxException | IllegalArgumentException e) {
        // Not a parseable URI or malformed percent-encoding — fall back to the input verbatim.
      }
      return input;
    }
  }

  @TemplateSubType(id = "slack", label = "Slack")
  record SlackChannelPlatform(
      @TemplateProperty(
              group = "channel",
              label = "Workspace ID",
              description =
                  "Slack workspace (team) ID. Leave empty to use the workspace the backend is configured for.",
              optional = true)
          String workspaceId,
      @TemplateProperty(
              group = "channel",
              label = "Private channel",
              description = "Create the channel as private rather than public.",
              type = PropertyType.Boolean,
              optional = true)
          Boolean isPrivate)
      implements ChannelPlatform {}
}
