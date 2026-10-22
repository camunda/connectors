/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.auth;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import org.apache.commons.lang3.builder.ToStringBuilder;

@TemplateSubType(id = BearerAuthentication.TYPE, label = "Bearer token")
public record BearerAuthentication(
    @FEEL @NotBlank @TemplateProperty(group = "authentication", label = "Bearer token")
        String token)
    implements Authentication {

  @TemplateProperty(ignore = true)
  public static final String TYPE = "bearer";

  @Override
  public String toString() {
    return new ToStringBuilder(this).append("token", "REDACTED").toString();
  }
}
