/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.translation;

import static io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModelErrorCodes.ERROR_CODE_UNSUPPORTED_MODEL_CONFIGURATION;

import io.camunda.connector.agenticai.aiagent.chatmodel.connection.EndpointAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.RequestCustomizations;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.aws.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.FoundryAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral.MistralConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiConfiguration;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.CustomChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.V1ToV2ProviderConfigurationMapper;
import io.camunda.connector.agenticai.aiagent.request.shared.TimeoutConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicAwsBedrockMantleBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicCustomBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicFoundryBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicEffort;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicChatModelConfiguration.AnthropicModel.AnthropicThinking;
import io.camunda.connector.agenticai.aiagent.request.v2.AnthropicCustomEndpointAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.AwsAuthentication.AwsApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.AwsAuthentication.AwsDefaultCredentialsChainAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.AwsAuthentication.AwsStaticCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.BedrockConverseChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.CustomProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.FoundryAuthentication.ApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.FoundryAuthentication.ClientCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.request.v2.FoundryAuthentication.ManagedIdentityAuthentication;
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
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiFoundryBackend;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiChatModelConfiguration.OpenAiEffort;
import io.camunda.connector.agenticai.aiagent.request.v2.OpenAiCustomEndpointAuthentication;
import io.camunda.connector.api.error.ConnectorException;
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
    return switch (provider) {
      case OpenAiChatModelConfiguration openAi -> translateOpenAi(openAi);
      case AnthropicChatModelConfiguration anthropic -> translateAnthropic(anthropic);
      case MistralChatModelConfiguration mistral -> translateMistral(mistral);
      case GeminiChatModelConfiguration gemini -> translateGemini(gemini);
      case BedrockConverseChatModelConfiguration bedrock -> translateBedrock(bedrock);
      case CustomProviderConfiguration custom ->
          new CustomChatModelConfiguration(
              custom.providerType(), custom.model(), custom.parameters());
    };
  }

  private OpenAiConfiguration translateOpenAi(OpenAiChatModelConfiguration configuration) {
    final var connection = configuration.openai();
    return new OpenAiConfiguration(
        switch (connection.api()) {
          case OpenAiResponsesApi responses ->
              responses.responses() == null
                  ? new OpenAiConfiguration.Api.Responses(null, null, null, null)
                  : new OpenAiConfiguration.Api.Responses(
                      responses.responses().maxOutputTokens(),
                      openAiEffort(responses.responses().effort()),
                      responses.responses().temperature(),
                      responses.responses().topP());
          case OpenAiCompletionsApi completions ->
              completions.completions() == null
                  ? new OpenAiConfiguration.Api.Completions(null, null, null, null)
                  : new OpenAiConfiguration.Api.Completions(
                      completions.completions().maxCompletionTokens(),
                      openAiEffort(completions.completions().effort()),
                      completions.completions().temperature(),
                      completions.completions().topP());
        },
        switch (connection.backend()) {
          case OpenAiApiBackend api ->
              new OpenAiConfiguration.Backend.Api(
                  api.openai().apiKey(),
                  nonBlank(api.openai().organizationId()),
                  nonBlank(api.openai().projectId()),
                  api.openai().endpoint(),
                  RequestCustomizations.of(
                      api.openai().headers(),
                      api.openai().queryParameters(),
                      api.openai().bodyProperties()));
          case OpenAiFoundryBackend foundry ->
              new OpenAiConfiguration.Backend.Foundry(
                  foundry.foundry().endpoint(),
                  nonBlank(foundry.foundry().apiVersion()),
                  foundryAuthentication(foundry.foundry().authentication()),
                  RequestCustomizations.of(
                      foundry.foundry().headers(),
                      foundry.foundry().queryParameters(),
                      foundry.foundry().bodyProperties()));
          case OpenAiCustomBackend custom ->
              new OpenAiConfiguration.Backend.Custom(
                  custom.custom().endpoint(),
                  endpointAuthentication(custom.custom().authentication()),
                  RequestCustomizations.of(
                      custom.custom().headers(),
                      custom.custom().queryParameters(),
                      custom.custom().bodyProperties()));
        },
        connection.model().model(),
        timeout(connection.timeouts()));
  }

  private static OpenAiConfiguration.@Nullable Effort openAiEffort(@Nullable OpenAiEffort effort) {
    if (effort == null) {
      return null;
    }
    return switch (effort) {
      case MODEL_DEFAULT -> null;
      case MINIMAL -> OpenAiConfiguration.Effort.MINIMAL;
      case LOW -> OpenAiConfiguration.Effort.LOW;
      case MEDIUM -> OpenAiConfiguration.Effort.MEDIUM;
      case HIGH -> OpenAiConfiguration.Effort.HIGH;
      case XHIGH -> OpenAiConfiguration.Effort.XHIGH;
      case MAX -> OpenAiConfiguration.Effort.MAX;
    };
  }

  private static @Nullable String nonBlank(@Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private AnthropicConfiguration translateAnthropic(AnthropicChatModelConfiguration configuration) {
    final var connection = configuration.anthropic();
    final var model = connection.model();
    final var parameters = model.parameters();
    return new AnthropicConfiguration(
        switch (connection.backend()) {
          case AnthropicApiBackend api ->
              new AnthropicConfiguration.Backend.Api(
                  api.anthropic().apiKey(),
                  api.anthropic().endpoint(),
                  RequestCustomizations.of(
                      api.anthropic().headers(),
                      api.anthropic().queryParameters(),
                      api.anthropic().bodyProperties()));
          case AnthropicAwsBedrockMantleBackend mantle ->
              new AnthropicConfiguration.Backend.AwsBedrockMantle(
                  mantle.awsBedrockMantle().region(),
                  mantle.awsBedrockMantle().endpoint(),
                  awsAuthentication(mantle.awsBedrockMantle().authentication()),
                  RequestCustomizations.of(
                      mantle.awsBedrockMantle().headers(),
                      mantle.awsBedrockMantle().queryParameters(),
                      mantle.awsBedrockMantle().bodyProperties()));
          case AnthropicFoundryBackend foundry ->
              new AnthropicConfiguration.Backend.Foundry(
                  foundry.foundry().endpoint(),
                  foundryAuthentication(foundry.foundry().authentication()),
                  RequestCustomizations.of(
                      foundry.foundry().headers(),
                      foundry.foundry().queryParameters(),
                      foundry.foundry().bodyProperties()));
          case AnthropicCustomBackend custom ->
              new AnthropicConfiguration.Backend.Custom(
                  custom.custom().endpoint(),
                  endpointAuthentication(custom.custom().authentication()),
                  RequestCustomizations.of(
                      custom.custom().headers(),
                      custom.custom().queryParameters(),
                      custom.custom().bodyProperties()));
        },
        model.model(),
        timeout(connection.timeouts()),
        parameters == null ? null : anthropicEffort(parameters.effort()),
        parameters == null ? null : anthropicThinking(parameters.thinking(), model.model()),
        parameters != null
            && parameters.promptCaching() != null
            && Boolean.TRUE.equals(parameters.promptCaching().enabled()),
        parameters != null && parameters.maxTokens() != null
            ? parameters.maxTokens()
            : AnthropicConfiguration.DEFAULT_MAX_TOKENS,
        parameters == null ? null : parameters.temperature(),
        parameters == null ? null : parameters.topP(),
        parameters == null ? null : parameters.topK());
  }

  private static AnthropicConfiguration.@Nullable Effort anthropicEffort(
      @Nullable AnthropicEffort effort) {
    if (effort == null) {
      return null;
    }
    return switch (effort) {
      case MODEL_DEFAULT -> null;
      case LOW -> AnthropicConfiguration.Effort.LOW;
      case MEDIUM -> AnthropicConfiguration.Effort.MEDIUM;
      case HIGH -> AnthropicConfiguration.Effort.HIGH;
      case XHIGH -> AnthropicConfiguration.Effort.XHIGH;
      case MAX -> AnthropicConfiguration.Effort.MAX;
    };
  }

  private static AnthropicConfiguration.@Nullable Thinking anthropicThinking(
      @Nullable AnthropicThinking thinking, String model) {
    if (thinking == null || thinking.mode() == null) {
      return null;
    }
    return switch (thinking.mode()) {
      case MODEL_DEFAULT -> null;
      case ENABLED -> {
        if (thinking.budgetTokens() == null) {
          throw new ConnectorException(
              ERROR_CODE_UNSUPPORTED_MODEL_CONFIGURATION,
              "Thinking mode ENABLED requires a budget tokens value for model '%s'"
                  .formatted(model));
        }
        yield new AnthropicConfiguration.Thinking.Enabled(thinking.budgetTokens());
      }
      case ADAPTIVE ->
          new AnthropicConfiguration.Thinking.Adaptive(
              thinking.display() == null
                  ? null
                  : switch (thinking.display()) {
                    case SUMMARIZED -> AnthropicConfiguration.Display.SUMMARIZED;
                    case OMITTED -> AnthropicConfiguration.Display.OMITTED;
                  });
      case DISABLED -> new AnthropicConfiguration.Thinking.Disabled();
    };
  }

  private static FoundryAuthentication foundryAuthentication(
      io.camunda.connector.agenticai.aiagent.request.v2.FoundryAuthentication authentication) {
    return switch (authentication) {
      case ApiKeyAuthentication apiKey -> new FoundryAuthentication.ApiKey(apiKey.apiKey());
      case ClientCredentialsAuthentication credentials ->
          new FoundryAuthentication.ClientCredentials(
              credentials.clientId(),
              credentials.clientSecret(),
              credentials.tenantId(),
              credentials.authorityHost(),
              credentials.entraIdScope());
      case ManagedIdentityAuthentication managedIdentity ->
          new FoundryAuthentication.ManagedIdentity(
              managedIdentity.clientId(), managedIdentity.entraIdScope());
    };
  }

  private static EndpointAuthentication endpointAuthentication(
      AnthropicCustomEndpointAuthentication authentication) {
    return switch (authentication) {
      case AnthropicCustomEndpointAuthentication.NoAuthentication ignored ->
          new EndpointAuthentication.None();
      case AnthropicCustomEndpointAuthentication.ApiKeyAuthentication apiKey ->
          new EndpointAuthentication.ApiKey(apiKey.apiKey());
      case OAuthClientCredentialsAuthentication oauth -> oauth(oauth);
    };
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
                  RequestCustomizations.of(
                      api.mistral().headers(),
                      api.mistral().queryParameters(),
                      api.mistral().bodyProperties()));
          case MistralCustomBackend custom ->
              new MistralConfiguration.Backend.Custom(
                  custom.custom().endpoint(),
                  endpointAuthentication(custom.custom().authentication()),
                  RequestCustomizations.of(
                      custom.custom().headers(),
                      custom.custom().queryParameters(),
                      custom.custom().bodyProperties()));
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
