/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model.auth;

import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotEmpty;

@TemplateSubType(id = BearerAuthentication.TYPE, label = "Bearer token")
public record BearerAuthentication(
    @FEEL @NotEmpty @TemplateProperty(group = "authentication", label = "Bearer token")
        String token)
    implements Authentication {

  @TemplateProperty(ignore = true)
  public static final String TYPE = "bearer";

  @Override
  public String toString() {
    return "BearerAuthentication{" + "token=[REDACTED]" + "}";
  }
}
