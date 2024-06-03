/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.model.request.auth;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(id = "tokenBasedAuthentication", label = "Authentication (refresh) token")
public record TokenBasedAuthentication(
    @NotBlank @TemplateProperty(label = "Token", group = "authentication") String token)
    implements Authentication {
  @Override
  public String toString() {
    return "TokenBasedAuthentication{token=[REDACTED]}";
  }
}
