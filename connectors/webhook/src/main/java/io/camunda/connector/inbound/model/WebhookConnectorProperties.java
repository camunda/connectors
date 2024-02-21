/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import static io.camunda.connector.api.inbound.webhook.VerifiableWebhook.WebhookHttpVerificationResult;

import io.camunda.connector.api.inbound.webhook.WebhookResultContext;
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyBinding;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.inbound.utils.HttpMethods;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import java.util.function.Function;

/**
 * { "id": "shouldValidateHmac", "label": "HMAC authentication", "group": "authentication",
 * "description": "Choose whether HMAC verification is enabled. <a
 * href='https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/http-webhook/#make-your-http-webhook-connector-for-receiving-messages-executable'
 * target='_blank'>See documentation</a> and <a
 * href='https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/http-webhook/#example'
 * target='_blank'>example</a> that explains how to use HMAC-related fields", "value": "disabled",
 * "type": "Dropdown", "choices": [ { "name": "Enabled", "value": "enabled" }, { "name": "Disabled",
 * "value": "disabled" } ], "binding": { "type": "zeebe:property", "name":
 * "inbound.shouldValidateHmac" } }, { "label": "HMAC secret key", "description": "Shared secret
 * key", "type": "String", "group": "authentication", "optional": true, "binding": { "type":
 * "zeebe:property", "name": "inbound.hmacSecret" }, "condition": { "property":
 * "shouldValidateHmac", "equals": "enabled" } }, { "label": "HMAC header", "description": "Name of
 * header attribute that will contain the HMAC value", "type": "String", "group": "authentication",
 * "optional": true, "binding": { "type": "zeebe:property", "name": "inbound.hmacHeader" },
 * "condition": { "property": "shouldValidateHmac", "equals": "enabled" } }, { "label": "HMAC
 * algorithm", "group": "authentication", "description": "Choose HMAC algorithm", "value":
 * "sha_256", "type": "Dropdown", "choices": [ { "name": "SHA-1", "value": "sha_1" }, { "name":
 * "SHA-256", "value": "sha_256" }, { "name": "SHA-512", "value": "sha_512" } ], "binding": {
 * "type": "zeebe:property", "name": "inbound.hmacAlgorithm" }, "condition": { "property":
 * "shouldValidateHmac", "equals": "enabled" } }, { "label": "HMAC scopes", "group":
 * "authentication", "description": "Set HMAC scopes for calculating signature data. See <a
 * href='https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/http-webhook/'
 * target='_blank'>documentation</a>", "feel": "required", "type": "String", "optional": true,
 * "binding": { "type": "zeebe:property", "name": "inbound.hmacScopes" }, "condition": { "property":
 * "shouldValidateHmac", "equals": "enabled" } }, { "id": "authorizationType", "label":
 * "Authorization type", "group": "authorization", "description": "Choose the authorization type.",
 * "value": "NONE", "type": "Dropdown", "choices": [ { "name": "None", "value": "NONE" }, { "name":
 * "JWT", "value": "JWT" }, { "name": "Basic", "value": "BASIC" }, { "name": "API Key", "value":
 * "APIKEY" } ], "binding": { "type": "zeebe:property", "name": "inbound.auth.type" } }, { "label":
 * "JWK url", "description": "Well-known url of JWKs", "type": "String", "group": "authorization",
 * "feel": "optional", "optional": true, "binding": { "type": "zeebe:property", "name":
 * "inbound.auth.jwt.jwkUrl" }, "condition": { "property": "authorizationType", "equals": "JWT" } },
 * { "label": "JWT role property expression", "description": "Expression to extract the roles from
 * the JWT token. <a
 * href='https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/http-webhook/#how-to-extract-roles-from-jwt-data'>See
 * documentation</a>", "type": "String", "group": "authorization", "feel": "required", "optional":
 * true, "binding": { "type": "zeebe:property", "name": "inbound.auth.jwt.permissionsExpression" },
 * "condition": { "property": "authorizationType", "equals": "JWT" } }, { "label": "Required roles",
 * "description": "List of roles to test JWT roles against", "type": "String", "group":
 * "authorization", "feel": "required", "optional": true, "binding": { "type": "zeebe:property",
 * "name": "inbound.auth.jwt.requiredPermissions" }, "condition": { "property": "authorizationType",
 * "equals": "JWT" } }, { "label": "Username", "description": "Username for basic authentication",
 * "type": "String", "group": "authorization", "feel": "optional", "binding": { "type":
 * "zeebe:property", "name": "inbound.auth.username" }, "condition": { "property":
 * "authorizationType", "equals": "BASIC" }, "constraints": { "notEmpty": true } }, { "label":
 * "Password", "description": "Password for basic authentication", "type": "String", "group":
 * "authorization", "feel": "optional", "binding": { "type": "zeebe:property", "name":
 * "inbound.auth.password" }, "condition": { "property": "authorizationType", "equals": "BASIC" },
 * "constraints": { "notEmpty": true } }, { "label": "API Key", "description": "Expected API key",
 * "type": "String", "group": "authorization", "feel": "optional", "binding": { "type":
 * "zeebe:property", "name": "inbound.auth.apiKey" }, "condition": { "property":
 * "authorizationType", "equals": "APIKEY" }, "constraints": { "notEmpty": true } }, { "label": "API
 * Key locator", "description": "A FEEL expression that extracts API key from the request. <a
 * href='https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/http-webhook/#how-to-configure-api-key-authorization'>See
 * documentation</a>", "type": "String", "group": "authorization", "feel": "required", "binding": {
 * "type": "zeebe:property", "name": "inbound.auth.apiKeyLocator" }, "condition": { "property":
 * "authorizationType", "equals": "APIKEY" }, "constraints": { "notEmpty": true }, "value":
 * "=split(request.headers.authorization, \" \")[2]" }, { "label": "Condition", "type": "String",
 * "group": "activation", "feel": "required", "optional": true, "binding": { "type":
 * "zeebe:property", "name": "activationCondition" }, "description": "Condition under which the
 * connector triggers. Leave empty to catch all events. <a
 * href='https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/http-webhook/#make-your-http-webhook-connector-for-receiving-messages-executable'
 * target='_blank'>See documentation</a>" }, { "label": "Result variable", "type": "String",
 * "group": "variable-mapping", "optional": true, "binding": { "type": "zeebe:property", "name":
 * "resultVariable" }, "description": "Name of variable to store the result of the connector in" },
 * { "label": "Result expression", "type": "String", "group": "variable-mapping", "feel":
 * "required", "optional": true, "binding": { "type": "zeebe:property", "name": "resultExpression"
 * }, "description": "Expression to map the inbound payload to process variables" }, { "label": "One
 * time verification response expression", "description": "Specify condition and response. Learn
 * more in the <a
 * href='https://docs.camunda.io/docs/components/connectors/protocol/http-webhook/#verification-expression'
 * target='_blank'>documentation</a>", "type": "Text", "group": "webhookResponse", "feel":
 * "required", "optional": true, "binding": { "type": "zeebe:property", "name":
 * "inbound.verificationExpression" } }, { "label": "Response body expression", "type": "Text",
 * "group": "webhookResponse", "feel": "required", "optional": true, "binding": { "type":
 * "zeebe:property", "name": "inbound.responseBodyExpression" }, "description": "Specify condition
 * and response" }
 *
 * @param method
 * @param context
 * @param shouldValidateHmac
 * @param hmacSecret
 * @param hmacHeader
 * @param hmacAlgorithm
 * @param hmacScopes
 * @param auth
 * @param responseBodyExpression
 * @param verificationExpression
 */
public record WebhookConnectorProperties(
    @TemplateProperty(
            id = "webhookMethod",
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
            id = "webhookId",
            label = "Webhook ID",
            group = "endpoint",
            description = "The webhook ID is a part of the URL",
            binding = @PropertyBinding(name = "inbound.context"))
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
                "Choose whether HMAC verification is enabled. <a href='https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/http-webhook/#make-your-http-webhook-connector-for-receiving-messages-executable' target='_blank'>See documentation</a> and <a href='https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/http-webhook/#example' target='_blank'>example</a> that explains how to use HMAC-related fields",
            defaultValue = "disabled",
            type = PropertyType.Dropdown,
            choices = {
              @DropdownPropertyChoice(label = "Enabled", value = "enabled"),
              @DropdownPropertyChoice(label = "Disabled", value = "disabled")
            })
        String shouldValidateHmac,
    @TemplateProperty(
            id = "hmacSecret",
            label = "HMAC secret key",
            description = "Shared secret key",
            group = "authentication",
            optional = true,
            condition = @PropertyCondition(property = "shouldValidateHmac", equals = "enabled"))
        String hmacSecret,
    @TemplateProperty(
            id = "hmacHeader",
            label = "HMAC header",
            description = "Name of header attribute that will contain the HMAC value",
            group = "authentication",
            optional = true,
            condition = @PropertyCondition(property = "shouldValidateHmac", equals = "enabled"))
        String hmacHeader,
    @TemplateProperty(
            id = "hmacAlgorithm",
            label = "HMAC algorithm",
            group = "authentication",
            description = "Choose HMAC algorithm",
            defaultValue = "sha_256",
            type = PropertyType.Dropdown,
            choices = {
              @DropdownPropertyChoice(label = "SHA-1", value = "sha_1"),
              @DropdownPropertyChoice(label = "SHA-256", value = "sha_256"),
              @DropdownPropertyChoice(label = "SHA-512", value = "sha_512")
            },
            condition = @PropertyCondition(property = "shouldValidateHmac", equals = "enabled"))
        String hmacAlgorithm,
    @TemplateProperty(
            id = "hmacScopes",
            label = "HMAC scopes",
            group = "authentication",
            description =
                "Set HMAC scopes for calculating signature data. See <a href='https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/http-webhook/' target='_blank'>documentation</a>",
            optional = true,
            condition = @PropertyCondition(property = "shouldValidateHmac", equals = "enabled"))
        @FEEL
        HMACScope[] hmacScopes,
    WebhookAuthorization auth,
    @TemplateProperty(
            id = "responseBodyExpression",
            label = "Response body expression",
            type = PropertyType.Text,
            group = "webhookResponse",
            description = "Specify condition and response",
            feel = FeelMode.required,
            optional = true)
        Function<WebhookResultContext, Object> responseBodyExpression,
    @TemplateProperty(
            id = "verificationExpression",
            label = "One time verification response expression",
            description =
                "Specify condition and response. Learn more in the <a href='https://docs.camunda.io/docs/components/connectors/protocol/http-webhook/#verification-expression' target='_blank'>documentation</a>",
            type = PropertyType.Text,
            group = "webhookResponse",
            feel = FeelMode.required,
            optional = true)
        Function<Map<String, Object>, WebhookHttpVerificationResult> verificationExpression) {

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
        wrapper.inbound.responseBodyExpression,
        wrapper.inbound.verificationExpression);
  }

  public record WebhookConnectorPropertiesWrapper(WebhookConnectorProperties inbound) {}

  private static <T> T getOrDefault(T value, T defaultValue) {
    return value != null ? value : defaultValue;
  }
}
