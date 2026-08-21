/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend.AnthropicApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseModel.BedrockConverseModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiVertexAiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiVertexAiBackend.GoogleVertexAi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GoogleVertexAiAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel.GeminiModelParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi.CompletionsParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.FoundryAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend.OpenAiApiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiCustomBackend.CustomBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiFoundryBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiFoundryBackend.FoundryBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiCustomEndpointAuthentication.ApiKeyAuthentication;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Maps a v1 {@code ProviderConfiguration} to the equivalent native v2 {@code
 * ProviderConfiguration}, so v1 agent jobs can run against the native providers.
 */
public class V1ToV2ProviderConfigurationMapperImpl implements V1ToV2ProviderConfigurationMapper {

  /**
   * Placeholder API key used for the OpenAI-compatible custom backend when the v1 configuration
   * supplies neither an {@code apiKey} nor a lift-able {@code Authorization: Bearer} header. The
   * native OpenAI SDK requires a credential to build a client at all.
   */
  public static final String MISSING_API_KEY_PLACEHOLDER = "not-required";

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  @Override
  public io.camunda.connector.agenticai.aiagent.model.request.v2.ProviderConfiguration map(
      io.camunda.connector.agenticai.aiagent.model.request.v1.ProviderConfiguration source) {
    return switch (source) {
      case AnthropicProviderConfiguration anthropic -> mapAnthropic(anthropic);
      case OpenAiProviderConfiguration openAi -> mapOpenAi(openAi);
      case OpenAiCompatibleProviderConfiguration openAiCompatible ->
          mapOpenAiCompatible(openAiCompatible);
      case BedrockProviderConfiguration bedrock -> mapBedrock(bedrock);
      case AzureOpenAiProviderConfiguration azureOpenAi -> mapAzureOpenAi(azureOpenAi);
      case GoogleVertexAiProviderConfiguration googleVertexAi -> mapGoogleVertexAi(googleVertexAi);
    };
  }

  private OpenAiChatModelConfiguration mapAzureOpenAi(AzureOpenAiProviderConfiguration source) {
    final var connection = source.azureOpenAi();
    final var model = connection.model();
    final var parameters = model.parameters();

    final var v2Parameters =
        parameters == null
            ? null
            : new CompletionsParameters(
                parameters.maxTokens(), null, parameters.temperature(), parameters.topP());

    return new OpenAiChatModelConfiguration(
        new OpenAiConnection(
            new OpenAiCompletionsApi(v2Parameters),
            new OpenAiFoundryBackend(
                new FoundryBackend(
                    connection.endpoint(),
                    null,
                    mapAzureAuthentication(connection.authentication()),
                    null,
                    null,
                    null)),
            new OpenAiModel(model.deploymentName()),
            connection.timeouts()));
  }

  private FoundryAuthentication mapAzureAuthentication(
      AzureOpenAiProviderConfiguration.AzureAuthentication authentication) {
    return switch (authentication) {
      case AzureOpenAiProviderConfiguration.AzureAuthentication.AzureApiKeyAuthentication apiKey ->
          new FoundryAuthentication.ApiKeyAuthentication(apiKey.apiKey());
      case AzureOpenAiProviderConfiguration.AzureAuthentication.AzureClientCredentialsAuthentication
              clientCredentials ->
          new FoundryAuthentication.ClientCredentialsAuthentication(
              clientCredentials.clientId(),
              clientCredentials.clientSecret(),
              clientCredentials.tenantId(),
              clientCredentials.authorityHost(),
              null);
    };
  }

  private GeminiChatModelConfiguration mapGoogleVertexAi(
      GoogleVertexAiProviderConfiguration source) {
    final var connection = source.googleVertexAi();
    final var model = connection.model();
    final var parameters = model.parameters();

    final var v2Parameters =
        parameters == null
            ? null
            : new GeminiModelParameters(
                parameters.maxOutputTokens(),
                toDouble(parameters.temperature()),
                toDouble(parameters.topP()),
                parameters.topK(),
                null);

    return new GeminiChatModelConfiguration(
        new GeminiConnection(
            new GeminiVertexAiBackend(
                new GoogleVertexAi(
                    connection.projectId(),
                    connection.region(),
                    connection.endpoint(),
                    mapGoogleVertexAiAuthentication(connection.authentication()))),
            new GeminiModel(model.model(), v2Parameters),
            connection.timeouts()));
  }

  private GoogleVertexAiAuthentication mapGoogleVertexAiAuthentication(
      GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication authentication) {
    return switch (authentication) {
      case GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication
                  .ServiceAccountCredentialsAuthentication
              serviceAccount ->
          new GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication(
              serviceAccount.jsonKey());
      case GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication
                  .ApplicationDefaultCredentialsAuthentication
              ignored ->
          new GoogleVertexAiAuthentication.ApplicationDefaultCredentialsAuthentication();
    };
  }

  private static @Nullable Double toDouble(@Nullable Float value) {
    return value == null ? null : value.doubleValue();
  }

  private AnthropicChatModelConfiguration mapAnthropic(AnthropicProviderConfiguration source) {
    final var connection = source.anthropic();
    final var model = connection.model();
    final var parameters = model.parameters();

    final var v2Parameters =
        parameters == null
            ? null
            : new AnthropicChatModelConfiguration.AnthropicModel.AnthropicModelParameters(
                null,
                null,
                null,
                parameters.maxTokens(),
                parameters.temperature(),
                parameters.topP(),
                parameters.topK());

    return new AnthropicChatModelConfiguration(
        new AnthropicChatModelConfiguration.AnthropicConnection(
            new AnthropicApiBackend(
                new AnthropicApi(
                    connection.authentication().apiKey(), connection.endpoint(), null, null, null)),
            new AnthropicChatModelConfiguration.AnthropicModel(model.model(), v2Parameters),
            connection.timeouts()));
  }

  private OpenAiChatModelConfiguration mapOpenAi(OpenAiProviderConfiguration source) {
    final var connection = source.openai();
    final var authentication = connection.authentication();
    final var model = connection.model();

    return new OpenAiChatModelConfiguration(
        new OpenAiConnection(
            new OpenAiCompletionsApi(completionsParametersOf(model.parameters())),
            new OpenAiApiBackend(
                new OpenAiApiConnection(
                    authentication.apiKey(),
                    authentication.organizationId(),
                    authentication.projectId(),
                    null,
                    null,
                    null,
                    null)),
            new OpenAiModel(model.model()),
            connection.timeouts()));
  }

  private OpenAiChatModelConfiguration mapOpenAiCompatible(
      OpenAiCompatibleProviderConfiguration source) {
    final var connection = source.openaiCompatible();
    final var model = connection.model();
    final var parameters = model.parameters();

    final var resolvedAuthentication =
        resolveOpenAiCompatibleAuthentication(connection.authentication(), connection.headers());

    return new OpenAiChatModelConfiguration(
        new OpenAiConnection(
            new OpenAiCompletionsApi(completionsParametersOf(parameters)),
            new OpenAiCustomBackend(
                new CustomBackend(
                    connection.endpoint(),
                    resolvedAuthentication.headers(),
                    connection.queryParameters(),
                    parameters == null ? null : parameters.customParameters(),
                    new ApiKeyAuthentication(resolvedAuthentication.apiKey()))),
            new OpenAiModel(model.model()),
            connection.timeouts()));
  }

  /**
   * Resolves the effective API key for the OpenAI-compatible custom backend and the headers that
   * should pass through alongside it, per the v1-to-v2 auth-lift algorithm: a non-blank {@code
   * authentication.apiKey} wins outright; otherwise a case-insensitive {@code Authorization: Bearer
   * <token>} header is lifted into the key and removed from the passthrough headers; otherwise the
   * placeholder key is used and headers pass through unchanged.
   */
  private ResolvedOpenAiCompatibleAuthentication resolveOpenAiCompatibleAuthentication(
      OpenAiCompatibleProviderConfiguration.OpenAiCompatibleAuthentication authentication,
      @Nullable Map<String, String> headers) {
    final var apiKey = authentication == null ? null : authentication.apiKey();
    if (apiKey != null && !apiKey.isBlank()) {
      return new ResolvedOpenAiCompatibleAuthentication(apiKey, headers);
    }

    if (headers != null) {
      for (final var entry : headers.entrySet()) {
        if (AUTHORIZATION_HEADER.equalsIgnoreCase(entry.getKey())
            && entry.getValue() != null
            && entry.getValue().regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
          final var token = entry.getValue().substring(BEARER_PREFIX.length());
          final var remainingHeaders = new LinkedHashMap<>(headers);
          remainingHeaders.remove(entry.getKey());
          return new ResolvedOpenAiCompatibleAuthentication(token, remainingHeaders);
        }
      }
    }

    return new ResolvedOpenAiCompatibleAuthentication(MISSING_API_KEY_PLACEHOLDER, headers);
  }

  private record ResolvedOpenAiCompatibleAuthentication(
      String apiKey, @Nullable Map<String, String> headers) {}

  private @Nullable CompletionsParameters completionsParametersOf(
      OpenAiProviderConfiguration.OpenAiModel.@Nullable OpenAiModelParameters parameters) {
    return parameters == null
        ? null
        : new CompletionsParameters(
            parameters.maxCompletionTokens(), null, parameters.temperature(), parameters.topP());
  }

  private @Nullable CompletionsParameters completionsParametersOf(
      OpenAiCompatibleProviderConfiguration.OpenAiCompatibleModel.@Nullable
          OpenAiCompatibleModelParameters
          parameters) {
    return parameters == null
        ? null
        : new CompletionsParameters(
            parameters.maxCompletionTokens(), null, parameters.temperature(), parameters.topP());
  }

  private BedrockConverseChatModelConfiguration mapBedrock(BedrockProviderConfiguration source) {
    final var connection = source.bedrock();
    final var model = connection.model();
    final var parameters = model.parameters();

    final var v2Parameters =
        parameters == null
            ? null
            : new BedrockConverseModelParameters(
                null, parameters.maxTokens(), parameters.temperature(), parameters.topP());

    return new BedrockConverseChatModelConfiguration(
        new BedrockConverseConnection(
            connection.region(),
            connection.endpoint(),
            mapAwsAuthentication(connection.authentication()),
            null,
            null,
            null,
            connection.timeouts(),
            new BedrockConverseModel(model.model(), v2Parameters)));
  }

  private AwsAuthentication mapAwsAuthentication(
      BedrockProviderConfiguration.AwsAuthentication authentication) {
    return switch (authentication) {
      case BedrockProviderConfiguration.AwsAuthentication.AwsStaticCredentialsAuthentication
              staticCredentials ->
          new AwsAuthentication.AwsStaticCredentialsAuthentication(
              staticCredentials.accessKey(), staticCredentials.secretKey());
      case BedrockProviderConfiguration.AwsAuthentication.AwsApiKeyAuthentication apiKey ->
          new AwsAuthentication.AwsApiKeyAuthentication(apiKey.apiKey());
      case BedrockProviderConfiguration.AwsAuthentication.AwsDefaultCredentialsChainAuthentication
              ignored ->
          new AwsAuthentication.AwsDefaultCredentialsChainAuthentication();
    };
  }
}
