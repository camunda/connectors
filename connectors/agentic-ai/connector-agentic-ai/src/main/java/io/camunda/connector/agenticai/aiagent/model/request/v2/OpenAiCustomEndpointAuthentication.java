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
 * Authentication strategies for OpenAI's {@code custom}-backend endpoint. Unlike Anthropic, there
 * is no genuine no-auth option here: the openai-java SDK requires a credential source to build a
 * client at all, so an apparent "no auth" choice would silently send a placeholder credential
 * instead of actually sending nothing. API key is therefore the only, required variant today,
 * modeled as a sealed interface rather than a flat {@code @NotBlank String apiKey} field to stay
 * consistent with the backend-subtype wrapping convention used throughout this provider's config.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = OpenAiCustomEndpointAuthentication.ApiKeyAuthentication.class,
      name = "apiKey")
})
@TemplateDiscriminatorProperty(
    label = "Authentication",
    group = "provider",
    name = "type",
    defaultValue = "apiKey",
    description = "Authentication for the compatible API.")
public sealed interface OpenAiCustomEndpointAuthentication {

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
      implements OpenAiCustomEndpointAuthentication {

    @Override
    public String toString() {
      return "ApiKeyAuthentication{apiKey=[REDACTED]}";
    }
  }
}
