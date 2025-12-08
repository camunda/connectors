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

@TemplateSubType(label = "Bearer token", id = "token")
public record BearerAuthentication(
    @NotBlank
        @TemplateProperty(id = "bearer.token", group = "authentication", label = "Bearer token")
        String token)
    implements MSTeamsAuthentication {

  @Override
  public String toString() {
    return "BearerAuthentication{token='[REDACTED]'}";
  }
}
