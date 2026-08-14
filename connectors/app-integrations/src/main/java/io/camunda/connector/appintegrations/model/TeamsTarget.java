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
 * Who a Microsoft Teams message goes to. The three subtypes deliberately name their field
 * differently ({@code channelId} / {@code teamsUser} / {@code conversationId}) — subtype fields
 * inherit the path of the sealed field they hang off with no subtype segment added, so identical
 * names would generate colliding property IDs.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = TeamsTarget.TeamsChannelTarget.class, name = "channel"),
  @JsonSubTypes.Type(value = TeamsTarget.TeamsUserTarget.class, name = "user"),
  @JsonSubTypes.Type(value = TeamsTarget.TeamsConversationTarget.class, name = "conversation")
})
@TemplateDiscriminatorProperty(
    name = "type",
    group = "recipient",
    label = "Teams target",
    description =
        "Post into a channel, send directly to a person, or reply in a conversation a previous send returned",
    defaultValue = "channel")
public sealed interface TeamsTarget {

  @TemplateSubType(id = "channel", label = "Channel")
  record TeamsChannelTarget(
      @NotBlank
          @TemplateProperty(
              group = "recipient",
              label = "Channel ID",
              description = "Microsoft Teams channel ID, e.g. 19:xxx@thread.tacv2.")
          String channelId)
      implements TeamsTarget {}

  @TemplateSubType(id = "user", label = "User")
  record TeamsUserTarget(
      @NotBlank
          @TemplateProperty(
              group = "recipient",
              label = "User ID",
              description =
                  "Microsoft Entra object ID of the recipient — they must have connected the app.")
          String teamsUser)
      implements TeamsTarget {}

  @TemplateSubType(id = "conversation", label = "Conversation")
  record TeamsConversationTarget(
      @NotBlank
          @TemplateProperty(
              group = "recipient",
              label = "Conversation",
              description =
                  "Conversation returned by a previous send; the message is posted as a reply in it.")
          String conversationId)
      implements TeamsTarget {}
}
