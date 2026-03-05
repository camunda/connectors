/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.common.auth;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(label = "Client credentials", id = "clientCredentials")
public record ClientCredentialsAuthentication(
    @FEEL
        @NotBlank
        @TemplateProperty(
            group = "authentication",
            id = "credentials.clientId",
            label = "Client ID",
            description = "The client ID of the application",
            feel = FeelMode.optional)
        String clientId,
    @FEEL
        @NotBlank
        @TemplateProperty(
            group = "authentication",
            id = "credentials.tenantId",
            label = "Tenant ID",
            description = "The tenant ID of the application",
            feel = FeelMode.optional)
        String tenantId,
    @FEEL
        @NotBlank
        @TemplateProperty(
            group = "authentication",
            id = "credentials.clientSecret",
            label = "Client secret",
            description = "The secret value of the Azure AD application",
            feel = FeelMode.optional)
        String clientSecret)
    implements MicrosoftAuthentication {

  @Override
  public String toString() {
    return String.format(
        "ClientCredentialsAuthentication{clientId='%s', tenantId='%s', clientSecret='%s'}",
        clientId, tenantId, "[REDACTED]");
  }
}
