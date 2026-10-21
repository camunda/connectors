/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.translation;

import io.camunda.connector.agenticai.aiagent.chatmodel.connection.EndpointAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.RequestCustomizations;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.aws.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral.MistralConfiguration;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.V1ToV2ProviderConfigurationMapper;
import io.camunda.connector.agenticai.aiagent.request.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.AwsAuthentication.AwsApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.AwsAuthentication.AwsDefaultCredentialsChainAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.AwsAuthentication.AwsStaticCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.BedrockConverseChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiVertexAiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiBackend.GoogleVertexAiAuthentication.ApplicationDefaultCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiBackend.GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiModel.GeminiThinking;
import io.camunda.connector.agenticai.aiagent.request.v2.GeminiChatModelConfiguration.GeminiModel.GeminiThinkingLevel;
import io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration.MistralBackend.MistralApiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration.MistralBackend.MistralCustomBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.MistralChatModelConfiguration.MistralEffort;
import io.camunda.connector.agenticai.aiagent.request.v2.OAuthClientCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiCustomEndpointAuthentication;
import io.camunda.connector.api.error.ConnectorInputException;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Translates the provider configuration of a request into the domain configuration of its chat
 * model. A request of the v1 flavor is first rewritten to its v2 equivalent, so there is one
 * translation per provider.
 */
public class ChatModelConfigurationTranslator {

  private final V1ToV2ProviderConfigurationMapper v1Mapper;
  private final Duration defaultTimeout;

  /**
   * @param defaultTimeout the timeout of a model call which the request does not configure
   */
  public ChatModelConfigurationTranslator(
      V1ToV2ProviderConfigurationMapper v1Mapper, Duration defaultTimeout) {
    this.v1Mapper = v1Mapper;
    this.defaultTimeout = defaultTimeout;
  }

  public ChatModelConfiguration translate(
      io.camunda.connector.agenticai.aiagent.request.v1.ProviderConfiguration provider) {
    return translate(v1Mapper.map(provider));
  }

  public ChatModelConfiguration translate(
      io.camunda.connector.agenticai.aiagent.request.v2.ProviderConfiguration provider) {
    if (provider instanceof MistralChatModelConfiguration mistral) {
      return translateMistral(mistral);
    }
    if (provider instanceof GeminiChatModelConfiguration gemini) {
      return translateGemini(gemini);
    }
    if (provider instanceof BedrockConverseChatModelConfiguration bedrock) {
      return translateBedrock(bedrock);
    }
    return provider;
  }

  private MistralConfiguration translateMistral(MistralChatModelConfiguration configuration) {
    final var connection = configuration.mistral();
    final var parameters = connection.parameters();
    return new MistralConfiguration(
        switch (connection.backend()) {
          case MistralApiBackend api ->
              new MistralConfiguration.Backend.Api(
                  api.mistral().apiKey(),
                  api.mistral().endpoint() != null
                      ? api.mistral().endpoint()
                      : MistralConfiguration.Backend.Api.DEFAULT_ENDPOINT,
                  api.requestCustomizations());
          case MistralCustomBackend custom ->
              new MistralConfiguration.Backend.Custom(
                  custom.custom().endpoint(),
                  endpointAuthentication(custom.custom().authentication()),
                  custom.requestCustomizations());
        },
        connection.model().model(),
        timeout(connection.timeouts()),
        parameters == null ? null : parameters.maxTokens(),
        parameters == null ? null : mistralEffort(parameters.effort()),
        parameters == null ? null : parameters.temperature(),
        parameters == null ? null : parameters.topP());
  }

  private BedrockConfiguration translateBedrock(
      BedrockConverseChatModelConfiguration configuration) {
    final var connection = configuration.bedrock();
    final var parameters = connection.model().parameters();
    return new BedrockConfiguration(
        connection.region(),
        connection.endpoint(),
        awsAuthentication(connection.authentication()),
        RequestCustomizations.of(
            connection.headers(), connection.queryParameters(), connection.bodyProperties()),
        timeout(connection.timeouts()),
        connection.model().model(),
        parameters != null
            && parameters.promptCaching() != null
            && Boolean.TRUE.equals(parameters.promptCaching().enabled()),
        parameters == null ? null : parameters.maxTokens(),
        parameters == null ? null : parameters.temperature(),
        parameters == null ? null : parameters.topP());
  }

  private static AwsAuthentication awsAuthentication(
      io.camunda.connector.agenticai.aiagent.request.v2.AwsAuthentication authentication) {
    return switch (authentication) {
      case AwsStaticCredentialsAuthentication credentials ->
          new AwsAuthentication.StaticCredentials(credentials.accessKey(), credentials.secretKey());
      case AwsApiKeyAuthentication apiKey -> new AwsAuthentication.ApiKey(apiKey.apiKey());
      case AwsDefaultCredentialsChainAuthentication ignored ->
          new AwsAuthentication.DefaultCredentialsChain();
    };
  }

  private GeminiConfiguration translateGemini(GeminiChatModelConfiguration configuration) {
    final var connection = configuration.googleGemini();
    final var parameters = connection.model().parameters();
    return new GeminiConfiguration(
        switch (connection.backend()) {
          case GeminiApiBackend api ->
              new GeminiConfiguration.Backend.Api(
                  api.googleGeminiApi().apiKey(), api.googleGeminiApi().endpoint());
          case GeminiVertexAiBackend vertexAi ->
              new GeminiConfiguration.Backend.VertexAi(
                  vertexAi.googleVertexAi().projectId(),
                  vertexAi.googleVertexAi().region(),
                  vertexAi.googleVertexAi().endpoint(),
                  switch (vertexAi.googleVertexAi().authentication()) {
                    case ServiceAccountCredentialsAuthentication serviceAccount ->
                        new GeminiConfiguration.Authentication.ServiceAccountKey(
                            serviceAccount.jsonKey());
                    case ApplicationDefaultCredentialsAuthentication ignored ->
                        new GeminiConfiguration.Authentication.ApplicationDefaultCredentials();
                  });
        },
        connection.model().model(),
        timeout(connection.timeouts()),
        parameters == null ? null : parameters.maxTokens(),
        parameters == null ? null : parameters.temperature(),
        parameters == null ? null : parameters.topP(),
        parameters == null ? null : parameters.topK(),
        parameters == null ? null : geminiThinking(parameters.thinking()));
  }

  private static GeminiConfiguration.@Nullable Thinking geminiThinking(
      @Nullable GeminiThinking thinking) {
    if (thinking == null) {
      return null;
    }
    final var budget = thinking.thinkingBudget();
    final var level =
        thinking.thinkingLevel() == null
            ? GeminiThinkingLevel.MODEL_DEFAULT
            : thinking.thinkingLevel();
    final var explicitLevel = level != GeminiThinkingLevel.MODEL_DEFAULT;
    if (budget != null && explicitLevel) {
      throw new ConnectorInputException(
          "thinking.thinkingBudget and thinking.thinkingLevel are mutually exclusive");
    }
    if (budget != null) {
      return new GeminiConfiguration.Thinking.Budget(budget);
    }
    if (!explicitLevel) {
      return null;
    }
    return new GeminiConfiguration.Thinking.Level(
        switch (level) {
          case MINIMAL -> GeminiConfiguration.ThinkingLevel.MINIMAL;
          case LOW -> GeminiConfiguration.ThinkingLevel.LOW;
          case MEDIUM -> GeminiConfiguration.ThinkingLevel.MEDIUM;
          case HIGH -> GeminiConfiguration.ThinkingLevel.HIGH;
          case MODEL_DEFAULT -> throw new IllegalStateException("handled above");
        });
  }

  private static MistralConfiguration.@Nullable Effort mistralEffort(
      @Nullable MistralEffort effort) {
    if (effort == null) {
      return null;
    }
    return switch (effort) {
      case MODEL_DEFAULT -> null;
      case NONE -> MistralConfiguration.Effort.NONE;
      case HIGH -> MistralConfiguration.Effort.HIGH;
    };
  }

  private Duration timeout(@Nullable TimeoutConfiguration timeouts) {
    return Optional.ofNullable(timeouts)
        .map(TimeoutConfiguration::timeout)
        .filter(Duration::isPositive)
        .orElse(defaultTimeout);
  }

  private static EndpointAuthentication endpointAuthentication(
      OpenAiCustomEndpointAuthentication authentication) {
    return switch (authentication) {
      case OpenAiCustomEndpointAuthentication.NoAuthentication ignored ->
          new EndpointAuthentication.None();
      case OpenAiCustomEndpointAuthentication.ApiKeyAuthentication apiKey ->
          new EndpointAuthentication.ApiKey(apiKey.apiKey());
      case OAuthClientCredentialsAuthentication oauth -> oauth(oauth);
    };
  }

  private static EndpointAuthentication.OAuthClientCredentials oauth(
      OAuthClientCredentialsAuthentication oauth) {
    return new EndpointAuthentication.OAuthClientCredentials(
        oauth.oauthTokenEndpoint(),
        oauth.clientId(),
        oauth.clientSecret(),
        oauth.audience(),
        EndpointAuthentication.ClientAuthenticationMethod.valueOf(
            oauth.clientAuthentication().name()),
        oauth.scopes());
  }
}
