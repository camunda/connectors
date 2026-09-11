/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.v2;

import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.HttpUrl;
import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/** Reusable connection and authentication configurations supported by the native AI Agent v2. */
public final class AgenticAiCredentialConfigurations {

  private AgenticAiCredentialConfigurations() {}

  @Configuration(
      id = "io.camunda:agentic-ai-anthropic-api-credential:1",
      version = 1,
      name = "Anthropic API Credential")
  public record AnthropicApiCredential(
      @NotBlank @TemplateProperty(group = "authentication", secret = true) String apiKey) {}

  @Configuration(
      id = "io.camunda:agentic-ai-openai-api-credential:1",
      version = 1,
      name = "OpenAI API Credential")
  public record OpenAiApiCredential(
      @NotBlank @TemplateProperty(group = "authentication", secret = true) String apiKey,
      @TemplateProperty(group = "connection", label = "Organization ID")
          @Nullable String organizationId,
      @TemplateProperty(group = "connection", label = "Project ID") @Nullable String projectId) {}

  @Configuration(
      id = "io.camunda:agentic-ai-microsoft-foundry-credential:1",
      version = 1,
      name = "Microsoft Foundry Credential")
  public record MicrosoftFoundryCredential(
      @NotBlank @HttpUrl @TemplateProperty(group = "connection", label = "Resource endpoint")
          String endpoint,
      @Valid @NotNull @TemplateProperty(group = "authentication")
          OpenAiChatModelConfiguration.OpenAiBackend.FoundryAuthentication authentication) {}

  @Configuration(
      id = "io.camunda:agentic-ai-gateway-credential:1",
      version = 1,
      name = "AI Gateway Credential")
  public record AiGatewayCredential(
      @NotBlank @HttpUrl @TemplateProperty(group = "connection", label = "Gateway endpoint")
          String endpoint,
      @NotBlank @TemplateProperty(group = "authentication", label = "API key", secret = true)
          String apiKey) {}

  @Configuration(
      id = "io.camunda:agentic-ai-bedrock-api-key-credential:1",
      version = 1,
      name = "Amazon Bedrock API Key Credential")
  public record BedrockApiKeyCredential(
      @NotBlank @TemplateProperty(group = "authentication", label = "API key", secret = true)
          String apiKey,
      @NotBlank @TemplateProperty(group = "connection", label = "AWS region") String region) {}

  @Configuration(
      id = "io.camunda:agentic-ai-google-gemini-api-credential:1",
      version = 1,
      name = "Google Gemini API Credential")
  public record GoogleGeminiApiCredential(
      @NotBlank @TemplateProperty(group = "authentication", label = "Gemini API key", secret = true)
          String apiKey) {}

  @Configuration(
      id = "io.camunda:agentic-ai-vertex-ai-credential:1",
      version = 1,
      name = "Vertex AI Credential")
  public record VertexAiCredential(
      @NotBlank @TemplateProperty(group = "connection", label = "Project ID") String projectId,
      @NotBlank @TemplateProperty(group = "connection", label = "Region") String region,
      @Valid @NotNull @TemplateProperty(group = "authentication")
          GeminiChatModelConfiguration.GeminiBackend.GoogleVertexAiAuthentication authentication) {}
}
