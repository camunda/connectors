/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model.auth;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@TemplateDiscriminatorProperty(
    label = "API key",
    group = "authentication",
    name = "type",
    description = "Send API key in the header, or as parameter in the query parameters")
@TemplateSubType(id = ApiKeyAuthentication.TYPE, label = "API key")
public record ApiKeyAuthentication(
    @FEEL
        @NotNull
        @TemplateProperty(
            group = "authentication",
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "headers",
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "headers", label = "Headers"),
              @TemplateProperty.DropdownPropertyChoice(value = "query", label = "Query parameters")
            },
            description = "Choose type: Send API key in header or as query parameter.")
        ApiKeyLocation apiKeyLocation,
    @FEEL @NotEmpty @TemplateProperty(group = "authentication", label = "API key name") String name,
    @FEEL @NotEmpty @TemplateProperty(group = "authentication", label = "API key value")
        String value)
    implements Authentication {
  @TemplateProperty(ignore = true)
  public static final String TYPE = "apiKey";

  public boolean isQueryLocationApiKeyAuthentication() {
    return ApiKeyLocation.QUERY == apiKeyLocation;
  }

  @Override
  public String toString() {
    return "ApiKeyAuthentication{"
        + "apiKeyLocation="
        + apiKeyLocation
        + ", name='"
        + name
        + "'"
        + ", value=[REDACTED]"
        + "}";
  }
}
