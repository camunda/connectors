/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import static io.camunda.connector.agenticai.aiagent.chatmodel.provider.ChatModelProviderSupport.deriveTimeoutSetting;

import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.shared.TimeoutConfiguration;
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
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsConfigurationProperties.ChatModelProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  private static final Logger LOGGER =
      LoggerFactory.getLogger(V1ToV2ProviderConfigurationMapperImpl.class);
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final ChatModelProperties chatModelProperties;

  public V1ToV2ProviderConfigurationMapperImpl(ChatModelProperties chatModelProperties) {
    this.chatModelProperties = chatModelProperties;
  }

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
                    connection.authentication().apiKey(),
                    toNativeAnthropicEndpoint(connection.endpoint()),
                    null,
                    null,
                    null)),
            new AnthropicChatModelConfiguration.AnthropicModel(model.model(), v2Parameters),
            resolveTimeouts(connection.timeouts())));
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
            resolveTimeouts(connection.timeouts())));
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
            resolveTimeouts(connection.timeouts())));
  }

  /**
   * Resolves the effective custom-backend credential and pass-through headers per the v1 contract:
   * an {@code Authorization} header wins over a configured API key (the legacy factory clears the
   * key whenever both are present). A {@code Bearer} token is lifted into the native API key and
   * its header dropped; any other {@code Authorization} scheme passes through with the placeholder
   * key. With no {@code Authorization} header, a non-blank API key is used, else the placeholder.
   */
  private ResolvedOpenAiCompatibleAuthentication resolveOpenAiCompatibleAuthentication(
      OpenAiCompatibleProviderConfiguration.@Nullable OpenAiCompatibleAuthentication authentication,
      @Nullable Map<String, String> headers) {
    if (headers != null) {
      for (final var entry : headers.entrySet()) {
        if (AUTHORIZATION_HEADER.equalsIgnoreCase(entry.getKey()) && entry.getValue() != null) {
          final var value = entry.getValue();
          if (value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            final var remainingHeaders = new LinkedHashMap<>(headers);
            remainingHeaders.remove(entry.getKey());
            return new ResolvedOpenAiCompatibleAuthentication(
                value.substring(BEARER_PREFIX.length()), remainingHeaders);
          }
          return new ResolvedOpenAiCompatibleAuthentication(MISSING_API_KEY_PLACEHOLDER, headers);
        }
      }
    }

    final var apiKey = authentication == null ? null : authentication.apiKey();
    if (apiKey != null && !apiKey.isBlank()) {
      return new ResolvedOpenAiCompatibleAuthentication(apiKey, headers);
    }
    return new ResolvedOpenAiCompatibleAuthentication(MISSING_API_KEY_PLACEHOLDER, headers);
  }

  private record ResolvedOpenAiCompatibleAuthentication(
      String apiKey, @Nullable Map<String, String> headers) {}

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
            resolveTimeouts(connection.timeouts()),
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
            resolveTimeouts(connection.timeouts())));
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
            resolveTimeouts(connection.timeouts())));
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

  /**
   * Normalizes a v1 Anthropic endpoint to the base URL the native Anthropic SDK expects. The v1
   * endpoint carries the {@code /v1} API-version segment, whereas the native SDK appends {@code
   * /v1/messages} to the configured base itself; the segment is stripped here so the two do not
   * combine into a doubled {@code /v1/v1}.
   */
  private static @Nullable String toNativeAnthropicEndpoint(@Nullable String endpoint) {
    if (endpoint == null) {
      return null;
    }
    final var trimmed = StringUtils.stripEnd(endpoint, "/");
    return trimmed.endsWith("/v1")
        ? trimmed.substring(0, trimmed.length() - "/v1".length())
        : endpoint;
  }

  /**
   * Resolves the timeout the way the legacy factories did: the v1 timeout when positive, otherwise
   * the configured {@code chat-model.api.default-timeout}. Native providers otherwise fall back to
   * their SDK default, which would silently change legacy timeout behavior on a rewritten job.
   */
  private TimeoutConfiguration resolveTimeouts(@Nullable TimeoutConfiguration timeouts) {
    return new TimeoutConfiguration(
        deriveTimeoutSetting("v1 provider configuration", chatModelProperties, timeouts, LOGGER));
  }

  private static @Nullable Double toDouble(@Nullable Float value) {
    return value == null ? null : value.doubleValue();
  }
}
