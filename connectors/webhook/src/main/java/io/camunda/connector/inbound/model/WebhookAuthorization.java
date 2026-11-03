/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.inbound.model.WebhookAuthorization.ApiKeyAuth;
import io.camunda.connector.inbound.model.WebhookAuthorization.BasicAuth;
import io.camunda.connector.inbound.model.WebhookAuthorization.JwtAuth;
import io.camunda.connector.inbound.model.WebhookAuthorization.None;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.function.Function;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = BasicAuth.class, name = "BASIC"),
  @JsonSubTypes.Type(value = ApiKeyAuth.class, name = "APIKEY"),
  @JsonSubTypes.Type(value = JwtAuth.class, name = "JWT"),
  @JsonSubTypes.Type(value = None.class, name = "NONE")
})
@TemplateDiscriminatorProperty(
    name = "type",
    label = "Authorization type",
    group = "authorization",
    description = "Choose the authorization type",
    defaultValue = "NONE")
public sealed interface WebhookAuthorization permits None, BasicAuth, ApiKeyAuth, JwtAuth {

  @TemplateSubType(id = "NONE", label = "None")
  final class None implements WebhookAuthorization {}

  /**
   * @param basicCredential reusable credential; when bound, it supplies the username and password
   *     (see {@link #username()}, {@link #password()}). Declared first, as the inline fields it
   *     gates must render after it.
   */
  @TemplateSubType(id = "BASIC", label = "Basic")
  record BasicAuth(
      @TemplateProperty(
              id = "basicCredential",
              label = "Basic credential",
              group = "authorization",
              type = PropertyType.Configuration,
              optional = true,
              feel = FeelMode.disabled,
              description =
                  "Choose a reusable Basic authorization credential, or configure one-time"
                      + " parameters below.")
          @FEEL
          @Valid
          WebhookBasicAuthConfiguration basicCredential,
      @TemplateProperty(
              label = "Username",
              description = "Username for basic authentication",
              feel = FeelMode.optional,
              group = "authorization",
              condition =
                  @PropertyCondition(
                      property = "inbound.auth.basicCredential",
                      isEmpty = NullableBoolean.TRUE))
          @FEEL
          String username,
      @TemplateProperty(
              label = "Password",
              description = "Password for basic authentication",
              feel = FeelMode.optional,
              group = "authorization",
              condition =
                  @PropertyCondition(
                      property = "inbound.auth.basicCredential",
                      isEmpty = NullableBoolean.TRUE))
          @FEEL
          String password)
      implements WebhookAuthorization {

    public BasicAuth(String username, String password) {
      this(null, username, password);
    }

    /** The bound credential's username wins over the inline one. */
    @Override
    public String username() {
      return basicCredential != null ? basicCredential.username() : username;
    }

    /** The bound credential's password wins over the inline one. */
    @Override
    public String password() {
      return basicCredential != null ? basicCredential.password() : password;
    }

    @Override
    public String toString() {
      return "BasicAuth{"
          + "username='"
          + username()
          + "'"
          + ", password=[REDACTED], credential="
          + (basicCredential != null ? "bound" : "none")
          + "}";
    }
  }

  /**
   * @param apiKeyCredential reusable credential; when bound, it supplies the API key (see {@link
   *     #apiKey()}). The locator stays on the element, as it depends on the sending service.
   */
  @TemplateSubType(id = "APIKEY", label = "API key")
  record ApiKeyAuth(
      @TemplateProperty(
              id = "apiKeyCredential",
              label = "API key credential",
              group = "authorization",
              type = PropertyType.Configuration,
              optional = true,
              feel = FeelMode.disabled,
              description =
                  "Choose a reusable API key credential, or configure a one-time API key below.")
          @FEEL
          @Valid
          WebhookApiKeyConfiguration apiKeyCredential,
      @TemplateProperty(
              label = "API key",
              description = "Expected API key",
              feel = FeelMode.optional,
              group = "authorization",
              condition =
                  @PropertyCondition(
                      property = "inbound.auth.apiKeyCredential",
                      isEmpty = NullableBoolean.TRUE))
          @FEEL
          String apiKey,
      @TemplateProperty(
              label = "API key locator",
              description =
                  "A FEEL expression that extracts API key from the request. <a href='https://docs.camunda.io/docs/8.10/components/connectors/protocol/http-webhook/#how-to-configure-api-key-authorization'>See documentation</a>",
              group = "authorization",
              feel = FeelMode.required,
              defaultValue = "=split(request.headers.authorization, \" \")[2]")
          @NotEmpty
          Function<Object, String> apiKeyLocator)
      implements WebhookAuthorization {

    public ApiKeyAuth(String apiKey, Function<Object, String> apiKeyLocator) {
      this(null, apiKey, apiKeyLocator);
    }

    /** The bound credential's API key wins over the inline one. */
    @Override
    public String apiKey() {
      return apiKeyCredential != null ? apiKeyCredential.apiKey() : apiKey;
    }

    @Override
    public String toString() {
      return "ApiKeyAuth{"
          + "apiKey=[REDACTED]"
          + ", apiKeyLocator="
          + apiKeyLocator
          + ", credential="
          + (apiKeyCredential != null ? "bound" : "none")
          + "}";
    }
  }

  @TemplateSubType(id = "JWT", label = "JWT")
  record JwtAuth(JWTProperties jwt) implements WebhookAuthorization {
    @Override
    public String toString() {
      return "JwtAuth{jwt=[REDACTED]}";
    }
  }
}
