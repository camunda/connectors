/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.inbound.model.WebhookAuthorization.ApiKeyAuth;
import io.camunda.connector.inbound.model.WebhookAuthorization.BasicAuth;
import io.camunda.connector.inbound.model.WebhookAuthorization.JwtAuth;
import io.camunda.connector.inbound.model.WebhookAuthorization.None;
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
public sealed interface WebhookAuthorization {

  @TemplateSubType(id = "NONE", label = "None")
  final class None implements WebhookAuthorization {}

  @TemplateSubType(id = "BASIC", label = "Basic")
  record BasicAuth(
      @TemplateProperty(
              label = "Username",
              description = "Username for basic authentication",
              group = "authorization")
          @FEEL
          String username,
      @TemplateProperty(
              label = "Password",
              description = "Password for basic authentication",
              group = "authorization")
          @FEEL
          String password)
      implements WebhookAuthorization {}

  @TemplateSubType(id = "APIKEY", label = "API key")
  record ApiKeyAuth(
      @TemplateProperty(
              label = "API key",
              description = "Expected API key",
              group = "authorization")
          @FEEL
          String apiKey,
      @TemplateProperty(
              label = "API key locator",
              description =
                  "A FEEL expression that extracts API key from the request. <a href='https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/http-webhook/#how-to-configure-api-key-authorization'>See documentation</a>",
              group = "authorization",
              feel = FeelMode.required)
          Function<Object, String> apiKeyLocator)
      implements WebhookAuthorization {}

  @TemplateSubType(id = "JWT", label = "JWT")
  record JwtAuth(JWTProperties jwt) implements WebhookAuthorization {}
}
