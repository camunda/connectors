/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.util.ConnectorUtils;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/**
 * Authentication strategies for Anthropic's {@code foundry} backend. Mirrors {@code
 * OpenAiChatModelConfiguration.OpenAiBackend.FoundryAuthentication} but is kept as its own type so
 * the two providers' Foundry auth surfaces can diverge independently.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = AnthropicFoundryAuthentication.ApiKeyAuthentication.class,
      name = "apiKey"),
  @JsonSubTypes.Type(
      value = AnthropicFoundryAuthentication.ClientCredentialsAuthentication.class,
      name = "clientCredentials"),
  @JsonSubTypes.Type(
      value = AnthropicFoundryAuthentication.ManagedIdentityAuthentication.class,
      name = "managedIdentity")
})
@TemplateDiscriminatorProperty(
    label = "Authentication",
    group = "provider",
    name = "type",
    defaultValue = "apiKey",
    description = "Specify the Microsoft Foundry authentication strategy.")
public sealed interface AnthropicFoundryAuthentication {

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
      implements AnthropicFoundryAuthentication {

    @Override
    public String toString() {
      return "ApiKeyAuthentication{apiKey=[REDACTED]}";
    }
  }

  @TemplateSubType(id = "clientCredentials", label = "Entra ID: Client credentials")
  record ClientCredentialsAuthentication(
      @NotBlank
          @TemplateProperty(
              group = "provider",
              label = "Client ID",
              description = "Microsoft Entra ID application (client) ID.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String clientId,
      @NotBlank
          @TemplateProperty(
              group = "provider",
              label = "Client secret",
              description = "Microsoft Entra ID application client secret.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String clientSecret,
      @NotBlank
          @TemplateProperty(
              group = "provider",
              label = "Tenant ID",
              description = "Microsoft Entra ID tenant (directory) ID.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String tenantId,
      @TemplateProperty(
              group = "provider",
              label = "Authority host",
              description =
                  "Overrides the Microsoft Entra ID authority host, e.g. for sovereign clouds. "
                      + "Leave unset to use the public cloud authority.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              optional = true)
          @Nullable String authorityHost,
      @TemplateProperty(
              group = "provider",
              label = "Entra ID scope",
              description =
                  "Overrides the Microsoft Entra ID token scope requested for this authentication "
                      + "flow. Leave unset to let the automatically detected Azure cloud pick its "
                      + "own default.",
              type = TemplateProperty.PropertyType.Hidden,
              feel = FeelMode.disabled,
              optional = true)
          @Nullable String entraIdScope)
      implements AnthropicFoundryAuthentication {

    @Override
    public String toString() {
      return "ClientCredentialsAuthentication{clientId="
          + clientId
          + ", clientSecret=[REDACTED], tenantId="
          + tenantId
          + ", authorityHost="
          + authorityHost
          + ", entraIdScope="
          + entraIdScope
          + "}";
    }
  }

  @TemplateSubType(
      id = "managedIdentity",
      label = "Entra ID: Managed identity (Hybrid/Self-Managed only)")
  record ManagedIdentityAuthentication(
      @TemplateProperty(
              id = "managedIdentity.clientId",
              group = "provider",
              label = "Client ID",
              description =
                  "Client ID of a user-assigned managed identity. Leave unset to use the "
                      + "system-assigned managed identity.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              optional = true)
          @Nullable String clientId,
      @TemplateProperty(
              id = "managedIdentity.entraIdScope",
              group = "provider",
              label = "Entra ID scope",
              description =
                  "Overrides the Microsoft Entra ID token scope requested for this authentication "
                      + "flow. Leave unset to let the automatically detected Azure cloud pick its "
                      + "own default.",
              type = TemplateProperty.PropertyType.Hidden,
              feel = FeelMode.disabled,
              optional = true)
          @Nullable String entraIdScope)
      implements AnthropicFoundryAuthentication {

    @JsonIgnore
    @AssertFalse(message = "Managed identity authentication is not supported on SaaS")
    public boolean isUsedInSaaS() {
      return ConnectorUtils.isSaaS();
    }
  }
}
