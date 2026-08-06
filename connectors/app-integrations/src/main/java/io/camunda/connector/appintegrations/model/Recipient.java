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
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Who the message is sent to: a Camunda-side identity, or a Microsoft Teams channel. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = Recipient.CamundaRecipient.class, name = "camunda"),
  @JsonSubTypes.Type(value = Recipient.TeamsRecipient.class, name = "teams")
})
@TemplateDiscriminatorProperty(
    name = "type",
    group = "recipient",
    label = "Recipient source",
    description =
        "Choose whether the recipient comes from Camunda or is a Microsoft Teams channel directly",
    defaultValue = "camunda")
public sealed interface Recipient {

  /**
   * A Camunda-side recipient. Any combination of the three fields may be given; the backend
   * resolves each to the corresponding Teams users.
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
          List<String> candidateGroups)
      implements Recipient {

    @TemplateProperty(ignore = true)
    public static final String TYPE = "camunda";

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
      @NotEmpty
          @TemplateProperty(
              group = "recipient",
              label = "Channel ID",
              description =
                  "Microsoft Teams channel ID to send to, e.g. 19:xxx@thread.tacv2. Use when sending to a channel rather than a user.")
          String channelId)
      implements Recipient {

    @TemplateProperty(ignore = true)
    public static final String TYPE = "teams";
  }
}
