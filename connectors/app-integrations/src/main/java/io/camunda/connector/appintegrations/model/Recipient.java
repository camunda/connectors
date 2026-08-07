/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Who the message is sent to: a Camunda-side identity, a Microsoft Teams channel, or Slack.
 *
 * <p>Each subtype also carries its own "Additional content" switch, because the rich-content
 * formats a platform accepts differ (Teams takes an Adaptive Card, Slack takes Block Kit, Camunda
 * neither) and an element-template dropdown's choices are static — the options can only vary by
 * having one dropdown per recipient, conditioned on this discriminator.
 *
 * <p>The three fields holding those switches are named distinctly ({@code camundaExtra}, {@code
 * teamsExtra}, {@code slackExtra}) rather than all being called e.g. {@code additionalContent}:
 * subtype fields inherit the path of the sealed field they hang off with no subtype segment added,
 * so identical names would all generate {@code recipient.additionalContent.type} and collide. The
 * Modeler label is the same on all three, so this is invisible to modelers.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = Recipient.CamundaRecipient.class, name = "camunda"),
  @JsonSubTypes.Type(value = Recipient.TeamsRecipient.class, name = "teams"),
  @JsonSubTypes.Type(value = Recipient.SlackRecipient.class, name = "slack")
})
@TemplateDiscriminatorProperty(
    name = "type",
    group = "recipient",
    label = "Recipient source",
    description =
        "Choose whether the recipient comes from Camunda, or is a Microsoft Teams or Slack destination",
    defaultValue = "camunda")
public sealed interface Recipient {

  /** The additional content selected on this recipient's own platform-specific switch. */
  AdditionalContent additionalContent();

  /**
   * A Camunda-side recipient. Any combination of the three fields may be given; the backend
   * resolves each to the corresponding platform users.
   */
  @TemplateSubType(id = CamundaRecipient.TYPE, label = "Camunda")
  record CamundaRecipient(
      @TemplateProperty(
              group = "recipient",
              label = "Assignee email",
              description =
                  "Email address of the recipient. Use a FEEL expression to reference a process variable, e.g. =assigneeEmail.",
              optional = true)
          String email,
      @TemplateProperty(
              group = "recipient",
              label = "Candidate users",
              description =
                  "List of candidate usernames, e.g. <code>= [\"alice\", \"bob\"]</code>.",
              feel = FeelMode.required,
              optional = true)
          List<String> candidateUsers,
      @TemplateProperty(
              group = "recipient",
              label = "Candidate groups",
              description = "List of candidate group names, e.g. <code>= [\"approvers\"]</code>.",
              feel = FeelMode.required,
              optional = true)
          List<String> candidateGroups,
      @NotNull @Valid CamundaExtra camundaExtra)
      implements Recipient {

    @TemplateProperty(ignore = true)
    public static final String TYPE = "camunda";

    @Override
    public AdditionalContent additionalContent() {
      return camundaExtra;
    }

    @AssertTrue(
        message = "At least one of 'email', 'candidateUsers' or 'candidateGroups' must be provided")
    public boolean isRecipientProvided() {
      return (email != null && !email.isBlank())
          || (candidateUsers != null && !candidateUsers.isEmpty())
          || (candidateGroups != null && !candidateGroups.isEmpty());
    }
  }

  /** A Microsoft Teams channel, addressed by its channel ID. */
  @TemplateSubType(id = TeamsRecipient.TYPE, label = "Microsoft Teams")
  record TeamsRecipient(
      @NotBlank
          @TemplateProperty(
              group = "recipient",
              label = "Channel ID",
              description =
                  "Microsoft Teams channel ID to send to, e.g. 19:xxx@thread.tacv2. Use when sending to a channel rather than a user.")
          String channelId,
      @NotNull @Valid TeamsExtra teamsExtra)
      implements Recipient {

    @TemplateProperty(ignore = true)
    public static final String TYPE = "teams";

    @Override
    public AdditionalContent additionalContent() {
      return teamsExtra;
    }
  }

  /** A Slack destination — either a channel or a person, see {@link SlackTarget}. */
  @TemplateSubType(id = SlackRecipient.TYPE, label = "Slack")
  record SlackRecipient(
      @NotNull @Valid SlackTarget slackTarget, @NotNull @Valid SlackExtra slackExtra)
      implements Recipient {

    @TemplateProperty(ignore = true)
    public static final String TYPE = "slack";

    @Override
    public AdditionalContent additionalContent() {
      return slackExtra;
    }
  }
}
