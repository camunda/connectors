/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The name and description are shared; everything platform-specific lives in {@link
 * ChannelPlatform}.
 *
 * <p>The shared name cap is Slack's 80. Microsoft's limit is 50, so that tighter bound is asserted
 * only for the Teams platform rather than being imposed on Slack (or, worse, silently dropped).
 */
public record CreateChannelRequest(
    @NotBlank
        @Size(max = 80)
        @TemplateProperty(
            group = "channel",
            label = "Channel name",
            description =
                "Display name for the new channel (max 50 characters for Microsoft Teams, 80 for Slack).")
        String displayName,
    @TemplateProperty(
            group = "channel",
            label = "Description",
            description = "Optional description for the channel.",
            optional = true)
        String description,
    @NotNull @Valid ChannelPlatform platform) {

  @AssertTrue(message = "A Microsoft Teams channel name must be at most 50 characters")
  public boolean isDisplayNameValidForPlatform() {
    if (!(platform instanceof ChannelPlatform.TeamsChannelPlatform)) {
      return true;
    }
    return displayName == null
        || displayName.length() <= ChannelPlatform.TEAMS_MAX_DISPLAY_NAME_LENGTH;
  }
}
