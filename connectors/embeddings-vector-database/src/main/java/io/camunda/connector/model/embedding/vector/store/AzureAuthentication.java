/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.vector.store;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = AzureAuthentication.AzureApiKeyAuthentication.class, name = "apiKey"),
  @JsonSubTypes.Type(
      value = AzureAuthentication.AzureClientCredentialsAuthentication.class,
      name = "clientCredentials")
})
@TemplateDiscriminatorProperty(
    label = "Authentication",
    group = "embeddingsStore",
    name = "type",
    defaultValue = "apiKey",
    description = "Specify the Azure authentication strategy.")
public sealed interface AzureAuthentication {
  @TemplateSubType(id = "apiKey", label = "API key")
  record AzureApiKeyAuthentication(
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "API key",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String apiKey)
      implements AzureAuthentication {

    @Override
    public @NotNull String toString() {
      return "AzureApiKeyAuthentication{apiKey=[REDACTED]}";
    }
  }

  @TemplateSubType(id = "clientCredentials", label = "Client credentials")
  record AzureClientCredentialsAuthentication(
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Client ID",
              description = "ID of a Microsoft Entra application",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String clientId,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Client secret",
              description = "Secret of a Microsoft Entra application",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String clientSecret,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Tenant ID",
              description =
                  "ID of a Microsoft Entra tenant. Details in the <a href=\"https://learn.microsoft.com/en-us/entra/fundamentals/how-to-find-tenant\" target=\"_blank\">documentation</a>.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional)
          String tenantId,
      @TemplateProperty(
              group = "embeddingsStore",
              label = "Authority host",
              description =
                  "Authority host URL for the Microsoft Entra application. Defaults to <code>https://login.microsoftonline.com</code>. This can also contain an OAuth 2.0 token endpoint.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              optional = true)
          String authorityHost)
      implements AzureAuthentication {

    @Override
    public String toString() {
      return "AzureClientCredentialsAuthentication{clientId=%s, clientSecret=[REDACTED]}, tenantId=%s, authorityHost=%s}"
          .formatted(clientId, tenantId, authorityHost);
    }
  }
}
