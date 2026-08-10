/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

/**
 * Authentication strategies shared by "custom"-backend endpoints. Extensible: more schemes can be
 * added later without breaking existing configs.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = CustomEndpointAuthentication.NoAuthentication.class, name = "none"),
  @JsonSubTypes.Type(
      value = CustomEndpointAuthentication.ApiKeyAuthentication.class,
      name = "apiKey")
})
@TemplateDiscriminatorProperty(
    label = "Authentication",
    group = "provider",
    name = "type",
    defaultValue = "none",
    description = "Authentication for the compatible API.")
public sealed interface CustomEndpointAuthentication {

  @TemplateSubType(id = "none", label = "None")
  record NoAuthentication() implements CustomEndpointAuthentication {}

  @TemplateSubType(id = "apiKey", label = "API key")
  record ApiKeyAuthentication(
      @NotBlank
          @TemplateProperty(
              group = "provider",
              label = "API key",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String apiKey)
      implements CustomEndpointAuthentication {

    @Override
    public String toString() {
      return "ApiKeyAuthentication{apiKey=[REDACTED]}";
    }
  }
}
