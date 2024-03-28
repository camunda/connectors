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

@TemplateSubType(label = "Refresh token", id = "refresh")
public record RefreshTokenAuthentication(
    @NotBlank
        @TemplateProperty(group = "authentication", id = "refresh.token", label = "Refresh token")
        String token,
    @NotBlank
        @TemplateProperty(
            group = "authentication",
            id = "refresh.clientId",
            label = "Client ID",
            description = "The client ID of the application")
        String clientId,
    @NotBlank
        @TemplateProperty(
            group = "authentication",
            id = "refresh.tenantId",
            label = "Tenant ID",
            description = "The tenant ID of the application")
        String tenantId,
    @TemplateProperty(
            group = "authentication",
            id = "refresh.clientSecret",
            label = "Client secret",
            optional = true,
            description =
                "The secret value of the Azure AD application; optional, depends on whether the client is public or private")
        String clientSecret)
    implements MSTeamsAuthentication {

  @Override
  public String toString() {
    return String.format(
        "RefreshTokenAuthentication{clientId='%s', tenantId='%s', clientSecret='%s', token='%s'}",
        clientId, tenantId, "[REDACTED]", "[REDACTED]");
  }
}
