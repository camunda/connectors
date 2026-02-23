/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.slack.outbound.model.ChatPostMessageData;
import io.camunda.connector.slack.outbound.model.ConversationsCreateData;
import io.camunda.connector.slack.outbound.model.ConversationsInviteData;
import io.camunda.connector.slack.outbound.model.ReactionsAddData;
import io.camunda.connector.slack.outbound.model.SlackRequestData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;

public record SlackRequest<T extends SlackRequestData>(
    @TemplateProperty(
            id = "token",
            label = "OAuth token",
            group = "authentication",
            feel = FeelMode.optional)
        @NotBlank
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
              @JsonSubTypes.Type(value = ReactionsAddData.class, name = "reactions.add")
            })
        @Valid
        @NotNull
        @NestedProperties(addNestedPath = false)
        T data) {
  public SlackResponse invoke(final Slack slack) throws SlackApiException, IOException {
    MethodsClient methods = slack.methods(token);
    return data.invoke(methods);
  }

  @Override
  public String toString() {
    return "SlackRequest{" + "token=[REDACTED]" + ", data=" + data + "}";
  }
}
