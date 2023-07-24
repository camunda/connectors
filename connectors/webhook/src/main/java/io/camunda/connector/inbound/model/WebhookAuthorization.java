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
public sealed interface WebhookAuthorization permits ApiKeyAuth, BasicAuth, JwtAuth, None {

  record BasicAuth(@FEEL String username, @FEEL String password) implements WebhookAuthorization {}

  record ApiKeyAuth(@FEEL String apiKey, Function<Object, String> apiKeyLocator)
      implements WebhookAuthorization {}

  record JwtAuth(JWTProperties jwt) implements WebhookAuthorization {}

  final class None implements WebhookAuthorization {}
}
