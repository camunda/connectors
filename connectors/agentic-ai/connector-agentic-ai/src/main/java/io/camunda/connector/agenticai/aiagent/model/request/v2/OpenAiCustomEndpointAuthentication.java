/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.http.client.authentication.OAuthConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jspecify.annotations.Nullable;

/**
 * Authentication strategies for OpenAI's {@code custom}-backend endpoint. Unlike Anthropic, there
 * is no genuine no-auth option here: the openai-java SDK requires a credential source to build a
 * client at all, so an apparent "no auth" choice would silently send a placeholder credential
 * instead of actually sending nothing. Modeled as a sealed interface rather than a flat
 * {@code @NotBlank String apiKey} field to stay consistent with the backend-subtype wrapping
 * convention used throughout this provider's config.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = OpenAiCustomEndpointAuthentication.ApiKeyAuthentication.class,
      name = "apiKey"),
  @JsonSubTypes.Type(
      value = OpenAiCustomEndpointAuthentication.OAuthClientCredentialsAuthentication.class,
      name = OpenAiCustomEndpointAuthentication.OAuthClientCredentialsAuthentication.TYPE)
})
@TemplateDiscriminatorProperty(
    label = "Authentication",
    group = "provider",
    name = "type",
    defaultValue = "apiKey",
    description = "Authentication for the compatible API.")
public sealed interface OpenAiCustomEndpointAuthentication {

  @TemplateSubType(id = "apiKey", label = "API key")
  record ApiKeyAuthentication(
      @NotBlank
          @TemplateProperty(
              group = "provider",
              label = "API key",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String apiKey)
      implements OpenAiCustomEndpointAuthentication {

    @Override
    public String toString() {
      return "ApiKeyAuthentication{apiKey=[REDACTED]}";
    }
  }

  @TemplateSubType(
      id = OpenAiCustomEndpointAuthentication.OAuthClientCredentialsAuthentication.TYPE,
      label = "OAuth 2.0")
  record OAuthClientCredentialsAuthentication(
      @FEEL
          @NotEmpty
          @Pattern(
              regexp = "^(=|(http://|https://|secrets|\\{\\{).*$)",
              message = "Must be a http(s) URL")
          @TemplateProperty(
              group = "provider",
              tooltip = "The OAuth token endpoint",
              label = "OAuth 2.0 token endpoint")
          String oauthTokenEndpoint,
      @FEEL
          @NotEmpty
          @TemplateProperty(
              group = "provider",
              tooltip = "Your application's client ID from the OAuth client",
              label = "Client ID")
          String clientId,
      @FEEL
          @NotEmpty
          @TemplateProperty(
              group = "provider",
              tooltip = "Your application's client secret from the OAuth client",
              label = "Client secret",
              secret = true)
          String clientSecret,
      @FEEL
          @Nullable
          @TemplateProperty(
              group = "provider",
              tooltip = "The unique identifier of the target API you want to access",
              optional = true)
          String audience,
      @NotNull
          @TemplateProperty(
              group = "provider",
              type = PropertyType.Dropdown,
              choices = {
                @DropdownPropertyChoice(
                    value = "BASIC_AUTH_HEADER",
                    label = "Send as Basic Auth header"),
                @DropdownPropertyChoice(
                    value = "CREDENTIALS_BODY",
                    label = "Send client credentials in body")
              },
              defaultValue = "BASIC_AUTH_HEADER",
              tooltip =
                  "Send client ID and client secret as Basic Auth request in the header, or as client credentials in the request body")
          ClientAuthenticationMethod clientAuthentication,
      @FEEL
          @Nullable
          @TemplateProperty(
              group = "provider",
              tooltip =
                  "The scopes which you want to request authorization for (e.g., read:contacts)",
              optional = true)
          String scopes)
      implements OpenAiCustomEndpointAuthentication {

    @TemplateProperty(ignore = true)
    public static final String TYPE = "oauth-client-credentials-flow";

    public enum ClientAuthenticationMethod {
      BASIC_AUTH_HEADER(OAuthConstants.BASIC_AUTH_HEADER),
      CREDENTIALS_BODY(OAuthConstants.CREDENTIALS_BODY);

      private final String oauthConstant;

      ClientAuthenticationMethod(String oauthConstant) {
        this.oauthConstant = oauthConstant;
      }

      public String oauthConstant() {
        return oauthConstant;
      }
    }

    public OAuthClientCredentialsAuthentication {
      if (clientAuthentication == null) {
        clientAuthentication = ClientAuthenticationMethod.BASIC_AUTH_HEADER;
      }
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("oauthTokenEndpoint", oauthTokenEndpoint)
          .append("clientId", "[REDACTED]")
          .append("clientSecret", "[REDACTED]")
          .append("audience", audience)
          .append("clientAuthentication", clientAuthentication)
          .append("scopes", scopes)
          .toString();
    }
  }
}
