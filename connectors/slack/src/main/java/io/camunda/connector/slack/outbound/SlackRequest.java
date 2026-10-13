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
import com.slack.api.SlackConfig;
import com.slack.api.methods.SlackApiException;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.slack.outbound.model.ChatPostMessageData;
import io.camunda.connector.slack.outbound.model.ConversationsCreateData;
import io.camunda.connector.slack.outbound.model.ConversationsInviteData;
import io.camunda.connector.slack.outbound.model.PinsAddData;
import io.camunda.connector.slack.outbound.model.PinsRemoveData;
import io.camunda.connector.slack.outbound.model.ReactionsAddData;
import io.camunda.connector.slack.outbound.model.SlackRequestData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record SlackRequest<T extends SlackRequestData>(
    @TemplateProperty(
            id = "token",
            label = "OAuth token",
            group = "authentication",
            feel = FeelMode.optional)
        @NotBlank
        String token,
    @TemplateProperty(
            id = "baseUrl",
            label = "API base URL",
            group = "authentication",
            type = PropertyType.Hidden,
            feel = FeelMode.disabled,
            defaultValue = DEFAULT_BASE_URL)
        String baseUrl,
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

  /** The public Slack API endpoint, used when the base URL is not overridden. */
  public static final String DEFAULT_BASE_URL = "https://slack.com/api/";

  private static final Logger LOG = LoggerFactory.getLogger(SlackRequest.class);

  public SlackRequest {
    if (baseUrl != null && baseUrl.isBlank()) {
      baseUrl = null;
    }
  }

  public SlackRequest(String token, T data) {
    this(token, null, data);
  }

  public SlackResponse invoke(final Slack slack) throws SlackApiException, IOException {
    if (baseUrl == null || DEFAULT_BASE_URL.equals(withTrailingSlash(baseUrl))) {
      return data.invoke(slack.methods(token));
    }
    final var config = new SlackConfig();
    config.setMethodsEndpointUrlPrefix(withTrailingSlash(baseUrl));
    final Slack scopedSlack = Slack.getInstance(config);
    try {
      return data.invoke(scopedSlack.methods(token));
    } finally {
      closeQuietly(scopedSlack);
    }
  }

  private static String withTrailingSlash(final String url) {
    return url.endsWith("/") ? url : url + "/";
  }

  /** Releases the per-request client's HTTP resources without masking the invocation result. */
  private static void closeQuietly(final Slack slack) {
    try {
      slack.close();
    } catch (Exception e) {
      LOG.warn("Failed to close the Slack client", e);
    }
  }

  @Override
  public String toString() {
    return "SlackRequest{" + "token=[REDACTED]" + ", baseUrl=" + baseUrl + ", data=" + data + "}";
  }
}
