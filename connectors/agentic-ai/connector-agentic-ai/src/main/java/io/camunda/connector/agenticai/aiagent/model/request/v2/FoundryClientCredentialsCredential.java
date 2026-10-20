/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/**
 * Reusable, saved credential for the Microsoft Foundry {@code clientCredentials} (Entra ID)
 * authentication variant. {@code authorityHost} identifies which Entra ID authority the
 * credential's identity belongs to, so it travels with the credential; {@code entraIdScope} stays a
 * diagram-level override, not part of the credential.
 */
@Configuration(
    id = "io.camunda:agentic-ai-foundry-client-credentials-credential:1",
    version = 1,
    name = "Microsoft Foundry Entra ID Client Credentials Credential")
public record FoundryClientCredentialsCredential(
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
            secret = true,
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
        @Nullable String authorityHost) {

  @Override
  public String toString() {
    return "FoundryClientCredentialsCredential{clientId="
        + clientId
        + ", clientSecret=[REDACTED], tenantId="
        + tenantId
        + ", authorityHost="
        + authorityHost
        + "}";
  }
}
