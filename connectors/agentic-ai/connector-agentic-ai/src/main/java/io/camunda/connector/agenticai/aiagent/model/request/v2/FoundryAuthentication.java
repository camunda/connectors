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
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.AssertTrue;
import org.jspecify.annotations.Nullable;

/** Authentication strategies for a Microsoft Foundry ({@code foundry}) backend. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = FoundryAuthentication.ApiKeyAuthentication.class, name = "apiKey"),
  @JsonSubTypes.Type(
      value = FoundryAuthentication.ClientCredentialsAuthentication.class,
      name = "clientCredentials"),
  @JsonSubTypes.Type(
      value = FoundryAuthentication.ManagedIdentityAuthentication.class,
      name = "managedIdentity")
})
@TemplateDiscriminatorProperty(
    label = "Authentication",
    group = "provider",
    name = "type",
    defaultValue = "apiKey",
    description = "Specify the Microsoft Foundry authentication strategy.")
public sealed interface FoundryAuthentication {

  @TemplateSubType(id = "apiKey", label = "API key")
  record ApiKeyAuthentication(
      @Valid
          @TemplateProperty(
              group = "provider",
              label = "Foundry API key credential",
              type = TemplateProperty.PropertyType.Configuration,
              optional = true,
              binding = @TemplateProperty.PropertyBinding(name = "foundryApiKeyCredential"),
              description =
                  "Select a saved Microsoft Foundry API key credential, or enter an API key"
                      + " below.")
          @Nullable FoundryApiKeyCredential foundryApiKeyCredential,
      @TemplateProperty(
              group = "provider",
              label = "API key",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              secret = true,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "foundryApiKeyCredential",
                      isEmpty = TemplateProperty.NullableBoolean.TRUE))
          @Nullable String apiKey)
      implements FoundryAuthentication {

    /** The Foundry API key: from the bound credential if present, else the inline value. */
    @JsonIgnore
    public @Nullable String effectiveApiKey() {
      return foundryApiKeyCredential != null ? foundryApiKeyCredential.apiKey() : apiKey;
    }

    @JsonIgnore
    @AssertTrue(
        message =
            "A Microsoft Foundry API key is required from the credential or element" + " template")
    public boolean isApiKeyPresent() {
      String effective = effectiveApiKey();
      return effective != null && !effective.isBlank();
    }

    @Override
    public String toString() {
      return "ApiKeyAuthentication{foundryApiKeyCredential="
          + foundryApiKeyCredential
          + ", apiKey=[REDACTED]}";
    }
  }

  @TemplateSubType(id = "clientCredentials", label = "Entra ID: Client credentials")
  record ClientCredentialsAuthentication(
      @Valid
          @TemplateProperty(
              group = "provider",
              label = "Foundry Entra ID client credentials credential",
              type = TemplateProperty.PropertyType.Configuration,
              optional = true,
              binding =
                  @TemplateProperty.PropertyBinding(name = "foundryClientCredentialsCredential"),
              description =
                  "Select a saved Microsoft Foundry Entra ID client credentials credential, or"
                      + " enter the fields below.")
          @Nullable FoundryClientCredentialsCredential foundryClientCredentialsCredential,
      @TemplateProperty(
              group = "provider",
              label = "Client ID",
              description = "Microsoft Entra ID application (client) ID.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "foundryClientCredentialsCredential",
                      isEmpty = TemplateProperty.NullableBoolean.TRUE))
          @Nullable String clientId,
      @TemplateProperty(
              group = "provider",
              label = "Client secret",
              description = "Microsoft Entra ID application client secret.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              secret = true,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "foundryClientCredentialsCredential",
                      isEmpty = TemplateProperty.NullableBoolean.TRUE))
          @Nullable String clientSecret,
      @TemplateProperty(
              group = "provider",
              label = "Tenant ID",
              description = "Microsoft Entra ID tenant (directory) ID.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "foundryClientCredentialsCredential",
                      isEmpty = TemplateProperty.NullableBoolean.TRUE))
          @Nullable String tenantId,
      @TemplateProperty(
              group = "provider",
              label = "Authority host",
              description =
                  "Overrides the Microsoft Entra ID authority host, e.g. for sovereign clouds. "
                      + "Leave unset to use the public cloud authority.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              optional = true,
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "foundryClientCredentialsCredential",
                      isEmpty = TemplateProperty.NullableBoolean.TRUE))
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
      implements FoundryAuthentication {

    /** The Entra ID client ID: from the bound credential if present, else the inline value. */
    @JsonIgnore
    public @Nullable String effectiveClientId() {
      return foundryClientCredentialsCredential != null
          ? foundryClientCredentialsCredential.clientId()
          : clientId;
    }

    /** The Entra ID client secret: from the bound credential if present, else the inline value. */
    @JsonIgnore
    public @Nullable String effectiveClientSecret() {
      return foundryClientCredentialsCredential != null
          ? foundryClientCredentialsCredential.clientSecret()
          : clientSecret;
    }

    /** The Entra ID tenant ID: from the bound credential if present, else the inline value. */
    @JsonIgnore
    public @Nullable String effectiveTenantId() {
      return foundryClientCredentialsCredential != null
          ? foundryClientCredentialsCredential.tenantId()
          : tenantId;
    }

    /** The Entra ID authority host: from the bound credential if present, else the inline value. */
    @JsonIgnore
    public @Nullable String effectiveAuthorityHost() {
      return foundryClientCredentialsCredential != null
          ? foundryClientCredentialsCredential.authorityHost()
          : authorityHost;
    }

    @JsonIgnore
    @AssertTrue(
        message =
            "Microsoft Foundry client ID, client secret and tenant ID are required from the"
                + " credential or element template")
    public boolean isClientCredentialsPresent() {
      return isPresent(effectiveClientId())
          && isPresent(effectiveClientSecret())
          && isPresent(effectiveTenantId());
    }

    private static boolean isPresent(@Nullable String value) {
      return value != null && !value.isBlank();
    }

    @Override
    public String toString() {
      return "ClientCredentialsAuthentication{foundryClientCredentialsCredential="
          + foundryClientCredentialsCredential
          + ", clientId="
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
      implements FoundryAuthentication {

    @JsonIgnore
    @AssertFalse(message = "Managed identity authentication is not supported on SaaS")
    public boolean isUsedInSaaS() {
      return ConnectorUtils.isSaaS();
    }
  }
}
