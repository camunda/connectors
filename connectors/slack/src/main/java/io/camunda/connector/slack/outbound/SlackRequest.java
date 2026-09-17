/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.slack.outbound.model.ChatPostMessageData;
import io.camunda.connector.slack.outbound.model.ConversationsCreateData;
import io.camunda.connector.slack.outbound.model.ConversationsInviteData;
import io.camunda.connector.slack.outbound.model.PinsAddData;
import io.camunda.connector.slack.outbound.model.PinsRemoveData;
import io.camunda.connector.slack.outbound.model.ReactionsAddData;
import io.camunda.connector.slack.outbound.model.SlackRequestData;
import io.camunda.connector.slack.outbound.model.SlackTokenConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;

public record SlackRequest<T extends SlackRequestData>(
    @TemplateProperty(
            id = "slackCredential",
            label = "Slack credential",
            group = "authentication",
            type = PropertyType.Configuration,
            optional = true,
            binding = @TemplateProperty.PropertyBinding(name = "slackCredential"),
            description =
                "Choose a reusable Slack credential, or configure a one-time OAuth token below.")
        @Valid
        SlackTokenConfiguration slackCredential,
    @TemplateProperty(
            id = "token",
            label = "OAuth token",
            group = "authentication",
            feel = FeelMode.optional,
            condition =
                @PropertyCondition(property = "slackCredential", isEmpty = NullableBoolean.TRUE),
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String token,
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
            property = "method")
        @JsonSubTypes(
            value = {
              @JsonSubTypes.Type(value = ChatPostMessageData.class, name = "chat.postMessage"),
              @JsonSubTypes.Type(
                  value = ConversationsCreateData.class,
                  name = "conversations.create"),
              @JsonSubTypes.Type(
                  value = ConversationsInviteData.class,
                  name = "conversations.invite"),
              @JsonSubTypes.Type(value = ReactionsAddData.class, name = "reactions.add"),
              @JsonSubTypes.Type(value = PinsAddData.class, name = "pins.add"),
              @JsonSubTypes.Type(value = PinsRemoveData.class, name = "pins.remove")
            })
        @Valid
        @NotNull
        @NestedProperties(addNestedPath = false)
        T data) {

  public SlackRequest {
    if (token != null && token.isBlank()) {
      token = null;
    }
  }

  public SlackRequest(String token, T data) {
    this(null, token, data);
  }

  @Override
  public String token() {
    return slackCredential != null ? slackCredential.token() : token;
  }

  @AssertTrue(
      message = "No OAuth token provided by the reusable credential or the element template")
  @JsonIgnore
  public boolean isTokenPresent() {
    return token() != null && !token().isBlank();
  }

  public SlackResponse invoke(final Slack slack) throws SlackApiException, IOException {
    MethodsClient methods = slack.methods(token());
    return data.invoke(methods);
  }

  @Override
  public String toString() {
    return "SlackRequest{"
        + "slackCredential="
        + (slackCredential != null ? "[REDACTED]" : "null")
        + ", token=[REDACTED]"
        + ", data=[REDACTED]"
        + "}";
  }
}
