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
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

/**
 * Who a Slack message goes to. The two subtypes deliberately name their field differently ({@code
 * channelId} vs {@code user}) — subtype fields inherit the path of the sealed field they hang off
 * with no subtype segment added, so identical names would generate colliding property IDs.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = SlackTarget.SlackChannelTarget.class, name = "channel"),
  @JsonSubTypes.Type(value = SlackTarget.SlackUserTarget.class, name = "user")
})
@TemplateDiscriminatorProperty(
    name = "type",
    group = "recipient",
    label = "Slack target",
    description = "Send to a Slack channel or directly to a person",
    defaultValue = "channel")
public sealed interface SlackTarget {

  @TemplateSubType(id = "channel", label = "Channel")
  record SlackChannelTarget(
      @NotBlank
          @TemplateProperty(
              group = "recipient",
              label = "Channel ID",
              description = "Slack channel ID, e.g. C0123456789.")
          String channelId)
      implements SlackTarget {}

  @TemplateSubType(id = "user", label = "User")
  record SlackUserTarget(
      @NotBlank
          @TemplateProperty(
              group = "recipient",
              label = "User",
              description = "Slack user email address or member ID, e.g. U0123456789.")
          String user)
      implements SlackTarget {}
}
