/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.inbound.webhook.model;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.inbound.model.HMACScope;
import io.camunda.connector.inbound.model.WebhookAuthorization;
import io.camunda.connector.inbound.signature.HMACAlgoCustomerChoice;
import io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Arrays;
import java.util.Optional;

public record A2aWebhookProperties(
    @TemplateProperty(
            id = "context",
            label = "Webhook ID",
            group = "endpoint",
            description = "The webhook ID is a part of the URL",
            feel = FeelMode.optional)
        @NotBlank
        @FEEL
        @Pattern(
            regexp = "^[a-zA-Z0-9]+([-_][a-zA-Z0-9]+)*$",
            message =
                "can only contain letters, numbers, or single underscores/hyphens and cannot begin or end with an underscore/hyphen")
        String context,
    @TemplateProperty(
            id = "clientResponse",
            group = "clientResponse",
            label = "A2A Client Response",
            description =
                "The response returned by the A2A client connector. Should contain a task.",
            binding = @TemplateProperty.PropertyBinding(name = "clientResponse"),
            feel = FeelMode.required)
        @NotBlank
        @FEEL
        String clientResponse,
    @TemplateProperty(
            id = "shouldValidateHmac",
            label = "HMAC authentication",
            group = "authentication",
            description =
                "Choose whether HMAC verification is enabled. <a href='https://docs.camunda.io/docs/components/connectors/protocol/http-webhook/#make-your-http-webhook-connector-for-receiving-messages-executable' target='_blank'>See documentation</a> and <a href='https://docs.camunda.io/docs/components/connectors/protocol/http-webhook/#example' target='_blank'>example</a> that explains how to use HMAC-related fields",
            defaultValue = "disabled",
            type = PropertyType.Dropdown)
        HMACSwitchCustomerChoice shouldValidateHmac,
    @TemplateProperty(
            id = "hmacSecret",
            label = "HMAC secret key",
            description = "Shared secret key",
            group = "authentication",
            optional = true,
            feel = FeelMode.optional,
            condition =
                @PropertyCondition(property = "inbound.shouldValidateHmac", equals = "enabled"))
        String hmacSecret,
    @TemplateProperty(
            id = "hmacHeader",
            label = "HMAC header",
            description = "Name of header attribute that will contain the HMAC value",
            group = "authentication",
            feel = FeelMode.optional,
            optional = true,
            condition =
                @PropertyCondition(property = "inbound.shouldValidateHmac", equals = "enabled"))
        String hmacHeader,
    @TemplateProperty(
            id = "hmacAlgorithm",
            label = "HMAC algorithm",
            group = "authentication",
            description = "Choose HMAC algorithm",
            defaultValue = "sha_256",
            type = PropertyType.Dropdown,
            condition =
                @PropertyCondition(property = "inbound.shouldValidateHmac", equals = "enabled"))
        HMACAlgoCustomerChoice hmacAlgorithm,
    @TemplateProperty(
            id = "hmacScopes",
            label = "HMAC scopes",
            group = "authentication",
            description =
                "Set HMAC scopes for calculating signature data. See <a href='https://docs.camunda.io/docs/components/connectors/protocol/http-webhook/' target='_blank'>documentation</a>",
            optional = true,
            type = PropertyType.String,
            feel = FeelMode.required,
            condition =
                @PropertyCondition(property = "inbound.shouldValidateHmac", equals = "enabled"))
        @FEEL
        HMACScope[] hmacScopes,
    @Valid @NotNull WebhookAuthorization auth) {

  public A2aWebhookProperties(A2aWebhookPropertiesWrapper wrapper) {
    this(
        wrapper.inbound.context,
        wrapper.inbound.clientResponse,
        wrapper.inbound.shouldValidateHmac,
        wrapper.inbound.hmacSecret,
        wrapper.inbound.hmacHeader,
        wrapper.inbound.hmacAlgorithm,
        // default to BODY if no scopes are provided
        Optional.ofNullable(wrapper.inbound.hmacScopes).orElse(new HMACScope[] {HMACScope.BODY}),
        Optional.ofNullable(wrapper.inbound.auth).orElse(new WebhookAuthorization.None()));
  }

  public record A2aWebhookPropertiesWrapper(A2aWebhookProperties inbound) {}

  @Override
  public String toString() {
    return "WebhookConnectorProperties{"
        + "context='"
        + context
        + "'"
        + ", shouldValidateHmac='"
        + shouldValidateHmac
        + "'"
        + ", hmacSecret=[REDACTED]"
        + ", hmacHeader='"
        + hmacHeader
        + "'"
        + ", hmacAlgorithm='"
        + hmacAlgorithm
        + "'"
        + ", hmacScopes="
        + Arrays.toString(hmacScopes)
        + ", auth="
        + auth
        + "}";
  }
}
