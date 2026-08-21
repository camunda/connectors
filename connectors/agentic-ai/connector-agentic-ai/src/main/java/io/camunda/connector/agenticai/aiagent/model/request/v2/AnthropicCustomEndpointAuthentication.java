/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

/**
 * Authentication strategies for Anthropic's {@code custom}-backend endpoint. Anthropic-compatible
 * endpoints genuinely support sending no authentication header at all, so {@link NoAuthentication}
 * is a real option here. Extensible: more schemes can be added later without breaking existing
 * configs.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = AnthropicCustomEndpointAuthentication.NoAuthentication.class,
      name = "none"),
  @JsonSubTypes.Type(
      value = AnthropicCustomEndpointAuthentication.ApiKeyAuthentication.class,
      name = "apiKey")
})
@TemplateDiscriminatorProperty(
    label = "Authentication",
    group = "provider",
    name = "type",
    defaultValue = "none",
    description = "Authentication for the compatible API.")
public sealed interface AnthropicCustomEndpointAuthentication {

  @TemplateSubType(id = "none", label = "None")
  record NoAuthentication() implements AnthropicCustomEndpointAuthentication {}

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
      implements AnthropicCustomEndpointAuthentication {

    @Override
    public String toString() {
      return "ApiKeyAuthentication{apiKey=[REDACTED]}";
    }
  }
}
