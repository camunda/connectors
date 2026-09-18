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
 * Authentication strategies for OpenAI's {@code custom}-backend endpoint. Unlike Anthropic, the
 * openai-java SDK requires some credential source to build a client at all, so {@link
 * NoAuthentication} sends a placeholder credential rather than genuinely no header; kept as its own
 * variant so the "no auth" choice is still available for self-hosted, unauthenticated
 * OpenAI-compatible servers.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = OpenAiCustomEndpointAuthentication.NoAuthentication.class,
      name = "none"),
  @JsonSubTypes.Type(
      value = OpenAiCustomEndpointAuthentication.ApiKeyAuthentication.class,
      name = "apiKey"),
  @JsonSubTypes.Type(
      value = OAuthClientCredentialsAuthentication.class,
      name = OAuthClientCredentialsAuthentication.TYPE)
})
@TemplateDiscriminatorProperty(
    label = "Authentication",
    group = "provider",
    name = "type",
    defaultValue = "none",
    description = "Authentication for the compatible API.")
public sealed interface OpenAiCustomEndpointAuthentication
    permits OpenAiCustomEndpointAuthentication.NoAuthentication,
        OpenAiCustomEndpointAuthentication.ApiKeyAuthentication,
        OAuthClientCredentialsAuthentication {

  @TemplateSubType(id = "none", label = "None")
  record NoAuthentication() implements OpenAiCustomEndpointAuthentication {}

  @TemplateSubType(id = "apiKey", label = "API key")
  record ApiKeyAuthentication(
      @NotBlank
          @TemplateProperty(
              group = "provider",
              label = "API key",
              secret = true,
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
