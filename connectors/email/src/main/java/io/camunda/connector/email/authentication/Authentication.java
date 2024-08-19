/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.authentication;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.email.authentication.Authentication.OauthAuthentication;
import io.camunda.connector.email.authentication.Authentication.SimpleAuthentication;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "authentication",
    defaultImpl = OauthAuthentication.class)
@JsonSubTypes({
  @JsonSubTypes.Type(value = SimpleAuthentication.class, name = "simple"),
  @JsonSubTypes.Type(value = OauthAuthentication.class, name = "oauth"),
})
@TemplateDiscriminatorProperty(
    label = "Authentication",
    group = "authentication",
    name = "authentication",
    defaultValue = "oauth",
    description = "")
public sealed interface Authentication permits OauthAuthentication, SimpleAuthentication {
  record OauthAuthentication(
      @TemplateProperty(
              group = "authentication",
              label = "Email address",
              description = "Provide email")
          @NotBlank
          String mail,
      @TemplateProperty(
              group = "authentication",
              label = "Oauth2 token",
              description = "Give token")
          @NotBlank
          String token)
      implements Authentication {}

  record SimpleAuthentication(
      @TemplateProperty(
              group = "authentication",
              label = "Email address",
              description = "Provide email")
          @NotBlank
          String mail,
      @TemplateProperty(
              group = "authentication",
              label = "Email password",
              description = "Provide password")
          @NotBlank
          String password)
      implements Authentication {}
}
