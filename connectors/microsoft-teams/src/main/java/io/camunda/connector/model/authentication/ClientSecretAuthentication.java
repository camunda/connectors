/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.authentication;

import jakarta.validation.constraints.NotBlank;

public record ClientSecretAuthentication(
    @NotBlank String clientId, @NotBlank String tenantId, @NotBlank String clientSecret)
    implements MSTeamsAuthentication {

  @Override
  public String toString() {
    return String.format(
        "ClientSecretAuthentication{clientId='%s', tenantId='%s', clientSecret='%s'}",
        clientId, tenantId, "[REDACTED]");
  }
}
