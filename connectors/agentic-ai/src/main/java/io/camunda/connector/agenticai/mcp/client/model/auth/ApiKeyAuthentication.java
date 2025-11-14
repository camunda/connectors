/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model.auth;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotEmpty;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@TemplateDiscriminatorProperty(
    label = "API key",
    group = "authentication",
    name = "type",
    description = "Send API key as HTTP header")
@TemplateSubType(id = ApiKeyAuthentication.TYPE, label = "API key")
public record ApiKeyAuthentication(
    @FEEL @NotEmpty @TemplateProperty(group = "authentication", label = "API key name") String name,
    @FEEL @NotEmpty @TemplateProperty(group = "authentication", label = "API key value")
        String value)
    implements Authentication {
  @TemplateProperty(ignore = true)
  public static final String TYPE = "apiKey";
}
