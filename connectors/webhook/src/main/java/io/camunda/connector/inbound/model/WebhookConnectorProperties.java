/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.api.inbound.webhook.WebhookHttpResponse;
import io.camunda.connector.api.inbound.webhook.WebhookResultContext;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.inbound.signature.HMACAlgoCustomerChoice;
import io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice;
import io.camunda.connector.inbound.utils.HttpMethods;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

public record WebhookConnectorProperties(
    @TemplateProperty(
            id = "method",
            label = "Webhook method",
            group = "endpoint",
            description = "Select HTTP method",
            type = PropertyType.Dropdown,
            choices = {
              @DropdownPropertyChoice(label = "Any", value = "any"),
              @DropdownPropertyChoice(label = "GET", value = "get"),
              @DropdownPropertyChoice(label = "POST", value = "post"),
              @DropdownPropertyChoice(label = "PUT", value = "put"),
              @DropdownPropertyChoice(label = "DELETE", value = "delete")
            },
            defaultValue = "any")
        String method,
    @TemplateProperty(
            id = "context",
            label = "Webhook ID",
            group = "endpoint",
            description = "The webhook ID is a part of the URL",
            feel = FeelMode.disabled)
        @NotBlank
        @Pattern(
            regexp = "^[a-zA-Z0-9]+([-_][a-zA-Z0-9]+)*$",
            message =
                "can only contain letters, numbers, or single underscores/hyphens and cannot begin or end with an underscore/hyphen")
        String context,
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
    WebhookAuthorization auth,
    @TemplateProperty(
            id = "responseExpression",
            label = "Response expression",
            type = PropertyType.Text,
            group = "webhookResponse",
            description = "Expression used to generate the HTTP response",
            feel = FeelMode.required,
            optional = true)
        Function<WebhookResultContext, WebhookHttpResponse> responseExpression,
    @TemplateProperty(ignore = true) Function<WebhookResultContext, Object> responseBodyExpression,
    @TemplateProperty(
            id = "verificationExpression",
            label = "One time verification response expression",
            description =
                "Specify condition and response. Learn more in the <a href='https://docs.camunda.io/docs/components/connectors/protocol/http-webhook/#verification-expression' target='_blank'>documentation</a>",
            type = PropertyType.Text,
            group = "webhookResponse",
            feel = FeelMode.required,
            optional = true)
        Function<Map<String, Object>, WebhookHttpResponse> verificationExpression) {

  public WebhookConnectorProperties(WebhookConnectorPropertiesWrapper wrapper) {
    this(
        wrapper.inbound.method != null ? wrapper.inbound.method : HttpMethods.any.name(),
        wrapper.inbound.context,
        wrapper.inbound.shouldValidateHmac,
        wrapper.inbound.hmacSecret,
        wrapper.inbound.hmacHeader,
        wrapper.inbound.hmacAlgorithm,
        // default to BODY if no scopes are provided
        getOrDefault(wrapper.inbound.hmacScopes, new HMACScope[] {HMACScope.BODY}),
        getOrDefault(wrapper.inbound.auth, new WebhookAuthorization.None()),
        wrapper.inbound.responseExpression,
        wrapper.inbound.responseBodyExpression,
        wrapper.inbound.verificationExpression);
  }

  public record WebhookConnectorPropertiesWrapper(WebhookConnectorProperties inbound) {}

  private static <T> T getOrDefault(T value, T defaultValue) {
    return value != null ? value : defaultValue;
  }

  @Override
  public String toString() {
    return "WebhookConnectorProperties{"
        + "method='"
        + method
        + "'"
        + ", context='"
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
        + ", responseExpression="
        + responseExpression
        + ", responseBodyExpression="
        + responseBodyExpression
        + ", verificationExpression="
        + verificationExpression
        + "}";
  }
}
