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
    // Declared first so it renders (and is emitted in properties[]) before the fallback field it
    // gates below - required both for UX (pick a credential before falling back to the inline
    // token) and by ConditionPropertyOrderRule (a condition's referenced property must appear
    // earlier).
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
    // @NotBlank moved off this field onto isTokenPresent() below: once a credential supplies the
    // token, this inline value is legitimately absent. Dropping @NotBlank also drops the
    // generator-derived notEmpty constraint, so it is restored by hand - the field is still
    // required client-side while it is visible (no credential bound). Hidden and un-required via
    // the isEmpty condition once a credential is chosen above; there is no inline override,
    // because the token *is* the secret the credential exists to hold.
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

  /**
   * A blank token means "not set", not "set to an empty value": Modeler emits an empty string for
   * the hidden inline field once a credential is bound, so collapsing it to {@code null} here lets
   * every downstream check see a real absence instead of special-casing {@code ""}.
   */
  public SlackRequest {
    if (token != null && token.isBlank()) {
      token = null;
    }
  }

  /**
   * The effective OAuth token: the bound credential wins over the inline field. Overriding the
   * record accessor rather than adding a new method means every existing caller of {@code token()}
   * gets the effective value automatically.
   */
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
        + slackCredential
        + ", token=[REDACTED]"
        + ", data="
        + data
        + "}";
  }
}
