/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.authentication;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(label = "Client credentials", id = "clientCredentials")
public record ClientSecretAuthentication(
    @NotBlank
        @TemplateProperty(
            group = "authentication",
            id = "credentials.clientId",
            label = "Client ID",
            description = "The client ID of the application")
        String clientId,
    @NotBlank
        @TemplateProperty(
            group = "authentication",
            id = "credentials.tenantId",
            label = "Tenant ID",
            description = "The tenant ID of the application")
        String tenantId,
    @NotBlank
        @TemplateProperty(
            group = "authentication",
            id = "credentials.clientSecret",
            label = "Client secret",
            description = "The secret value of the Azure AD application")
        String clientSecret)
    implements MSTeamsAuthentication {

  @Override
  public String toString() {
    return String.format(
        "ClientSecretAuthentication{clientId='%s', tenantId='%s', clientSecret='%s'}",
        clientId, tenantId, "[REDACTED]");
  }
}
