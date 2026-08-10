/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import com.azure.identity.ClientSecretCredentialBuilder;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.genai.Client;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureAuthentication.AzureApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureAuthentication.AzureClientCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.AwsAuthentication.AwsDefaultCredentialsChainAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration.AwsAuthentication.AwsStaticCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.api.error.ConnectorInputException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

public class ChatModelFactoryImpl implements ChatModelFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChatModelFactoryImpl.class);

  // Increasing the default timeout reasonably to avoid timeouts reported by several customers
  // Version 8.9 behavior allows customization of the timeouts on the process definitions, however
  // it was not easily backportable, thus a simple backport of the actual functionality for version
  // 8.8
  private static final Duration DEFAULT_MODEL_CALL_TIMEOUT = Duration.ofMinutes(3);

  private static final String GOOGLE_CLOUD_PLATFORM_SCOPE =
      "https://www.googleapis.com/auth/cloud-platform";

  @Override
  public ChatModel createChatModel(ProviderConfiguration providerConfiguration) {
    return switch (providerConfiguration) {
      case AnthropicProviderConfiguration anthropic ->
          createAnthropicChatModelBuilder(anthropic).build();
      case AzureOpenAiProviderConfiguration azureOpenAi ->
          createAzureOpenAiChatModelBuilder(azureOpenAi).build();
      case BedrockProviderConfiguration bedrock -> createBedrockChatModelBuilder(bedrock).build();
      case GoogleVertexAiProviderConfiguration vertexAi ->
          createGoogleVertexAiChatModelBuilder(vertexAi).build();
      case OpenAiProviderConfiguration openai -> createOpenaiChatModelBuilder(openai).build();
      case OpenAiCompatibleProviderConfiguration openaiCompatible ->
          createOpenaiCompatibleChatModelBuilder(openaiCompatible).build();
    };
  }

  protected AnthropicChatModel.AnthropicChatModelBuilder createAnthropicChatModelBuilder(
      AnthropicProviderConfiguration configuration) {
    final var connection = configuration.anthropic();

    final var builder =
        AnthropicChatModel.builder()
            .apiKey(connection.authentication().apiKey())
            .modelName(connection.model().model())
            .timeout(DEFAULT_MODEL_CALL_TIMEOUT);

    if (connection.endpoint() != null) {
      builder.baseUrl(connection.endpoint());
    }

    final var modelParameters = connection.model().parameters();
    if (modelParameters != null) {
      Optional.ofNullable(modelParameters.maxTokens()).ifPresent(builder::maxTokens);
      Optional.ofNullable(modelParameters.temperature()).ifPresent(builder::temperature);
      Optional.ofNullable(modelParameters.topP()).ifPresent(builder::topP);
      Optional.ofNullable(modelParameters.topK()).ifPresent(builder::topK);
    }

    return builder;
  }

  protected AzureOpenAiChatModel.Builder createAzureOpenAiChatModelBuilder(
      AzureOpenAiProviderConfiguration configuration) {
    final var connection = configuration.azureOpenAi();
    final var builder =
        AzureOpenAiChatModel.builder()
            .endpoint(connection.endpoint())
            .deploymentName(configuration.azureOpenAi().model().deploymentName())
            .timeout(DEFAULT_MODEL_CALL_TIMEOUT);

    switch (connection.authentication()) {
      case AzureApiKeyAuthentication azureApiKeyAuthentication ->
          builder.apiKey(azureApiKeyAuthentication.apiKey());
      case AzureClientCredentialsAuthentication auth -> {
        ClientSecretCredentialBuilder clientSecretCredentialBuilder =
            new ClientSecretCredentialBuilder()
                .clientId(auth.clientId())
                .clientSecret(auth.clientSecret())
                .tenantId(auth.tenantId());
        if (StringUtils.isNotBlank(auth.authorityHost())) {
          clientSecretCredentialBuilder.authorityHost(auth.authorityHost());
        }
        builder.tokenCredential(clientSecretCredentialBuilder.build());
      }
    }

    final var modelParameters = connection.model().parameters();
    if (modelParameters != null) {
      Optional.ofNullable(modelParameters.maxTokens()).ifPresent(builder::maxTokens);
      Optional.ofNullable(modelParameters.temperature()).ifPresent(builder::temperature);
      Optional.ofNullable(modelParameters.topP()).ifPresent(builder::topP);
    }

    return builder;
  }

  protected BedrockChatModel.Builder createBedrockChatModelBuilder(
      BedrockProviderConfiguration configuration) {
    final var connection = configuration.bedrock();

    final var bedrockClientBuilder =
        BedrockRuntimeClient.builder()
            .credentialsProvider(
                switch (connection.authentication()) {
                  case AwsDefaultCredentialsChainAuthentication ignored ->
                      DefaultCredentialsProvider.create();
                  case AwsStaticCredentialsAuthentication sca ->
                      StaticCredentialsProvider.create(
                          AwsBasicCredentials.create(sca.accessKey(), sca.secretKey()));
                })
            .overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .apiCallTimeout(DEFAULT_MODEL_CALL_TIMEOUT)
                    .build())
            .httpClientBuilder(
                ApacheHttpClient.builder()
                    .connectionTimeout(Duration.ofSeconds(15))
                    .socketTimeout(DEFAULT_MODEL_CALL_TIMEOUT))
            .region(Region.of(connection.region()));

    if (connection.endpoint() != null) {
      bedrockClientBuilder.endpointOverride(URI.create(connection.endpoint()));
    }

    final var builder =
        BedrockChatModel.builder()
            .client(bedrockClientBuilder.build())
            .modelId(connection.model().model())
            .timeout(DEFAULT_MODEL_CALL_TIMEOUT);

    final var modelParameters = connection.model().parameters();
    if (modelParameters != null) {
      final var requestParametersBuilder = BedrockChatRequestParameters.builder();
      Optional.ofNullable(modelParameters.maxTokens())
          .ifPresent(requestParametersBuilder::maxOutputTokens);
      Optional.ofNullable(modelParameters.temperature())
          .ifPresent(requestParametersBuilder::temperature);
      Optional.ofNullable(modelParameters.topP()).ifPresent(requestParametersBuilder::topP);

      builder.defaultRequestParameters(requestParametersBuilder.build());
    }

    return builder;
  }

  protected GoogleGenAiChatModel.Builder createGoogleVertexAiChatModelBuilder(
      GoogleVertexAiProviderConfiguration vertexAi) {
    final var connection = vertexAi.googleVertexAi();
    final var builder =
        GoogleGenAiChatModel.builder()
            .client(createGoogleGenAiClient(connection))
            .modelName(connection.model().model())
            .maxRetries(0);

    final var modelParameters = connection.model().parameters();
    if (modelParameters != null) {
      Optional.ofNullable(modelParameters.maxOutputTokens()).ifPresent(builder::maxOutputTokens);
      Optional.ofNullable(modelParameters.temperature())
          .map(Float::doubleValue)
          .ifPresent(builder::temperature);
      Optional.ofNullable(modelParameters.topP())
          .map(Float::doubleValue)
          .ifPresent(builder::topP);
      Optional.ofNullable(modelParameters.topK()).ifPresent(builder::topK);
    }

    return builder;
  }

  private Client createGoogleGenAiClient(GoogleVertexAiConnection connection) {
    final var httpOptions =
        HttpOptions.builder()
            .retryOptions(HttpRetryOptions.builder().attempts(1).build())
            .timeout((int) DEFAULT_MODEL_CALL_TIMEOUT.toMillis())
            .build();

    final var clientBuilder =
        Client.builder()
            .vertexAI(true)
            .project(connection.projectId())
            .location(connection.region())
            .httpOptions(httpOptions);

    if (connection.authentication() instanceof ServiceAccountCredentialsAuthentication sac) {
      clientBuilder.credentials(createGoogleServiceAccountCredentials(sac));
    }

    try {
      // application default credentials are resolved eagerly here
      return clientBuilder.build();
    } catch (GenAiIOException | IllegalArgumentException e) {
      LOGGER.error("Failed to create Google Vertex AI client", e);
      throw new ConnectorInputException("Failed to create Google Vertex AI client", e);
    }
  }

  private GoogleCredentials createGoogleServiceAccountCredentials(
      ServiceAccountCredentialsAuthentication sac) {
    try {
      // Credentials read from a key file carry no scopes. google-genai only scopes the
      // application default credentials it resolves itself and passes these through verbatim,
      // so without this the token request fails with invalid_scope.
      return ServiceAccountCredentials.fromStream(
              new ByteArrayInputStream(sac.jsonKey().getBytes(StandardCharsets.UTF_8)))
          .createScoped(GOOGLE_CLOUD_PLATFORM_SCOPE);
    } catch (IOException e) {
      LOGGER.error("Failed to parse service account credentials", e);
      throw new ConnectorInputException(
          "Authentication failed for provided service account credentials", e);
    }
  }

  protected OpenAiChatModel.OpenAiChatModelBuilder createOpenaiChatModelBuilder(
      OpenAiProviderConfiguration configuration) {
    final var connection = configuration.openai();

    final var builder =
        OpenAiChatModel.builder()
            .apiKey(connection.authentication().apiKey())
            .modelName(connection.model().model())
            .timeout(DEFAULT_MODEL_CALL_TIMEOUT);

    Optional.ofNullable(connection.authentication().organizationId())
        .ifPresent(builder::organizationId);
    Optional.ofNullable(connection.authentication().projectId()).ifPresent(builder::projectId);

    final var modelParameters = connection.model().parameters();
    if (modelParameters != null) {
      final var requestParametersBuilder = OpenAiChatRequestParameters.builder();
      Optional.ofNullable(modelParameters.maxCompletionTokens())
          .ifPresent(requestParametersBuilder::maxCompletionTokens);
      Optional.ofNullable(modelParameters.temperature())
          .ifPresent(requestParametersBuilder::temperature);
      Optional.ofNullable(modelParameters.topP()).ifPresent(requestParametersBuilder::topP);

      builder.defaultRequestParameters(requestParametersBuilder.build());
    }

    return builder;
  }

  protected OpenAiChatModel.OpenAiChatModelBuilder createOpenaiCompatibleChatModelBuilder(
      OpenAiCompatibleProviderConfiguration configuration) {
    final var connection = configuration.openaiCompatible();

    final var builder =
        OpenAiChatModel.builder()
            .modelName(connection.model().model())
            .timeout(DEFAULT_MODEL_CALL_TIMEOUT)
            .baseUrl(connection.endpoint());

    Optional.ofNullable(connection.authentication())
        .map(OpenAiCompatibleAuthentication::apiKey)
        .filter(StringUtils::isNotBlank)
        .ifPresent(
            apiKey -> {
              builder.apiKey(apiKey);
              if (connection.headers() != null) {
                if (connection.headers().keySet().stream()
                    .anyMatch("Authorization"::equalsIgnoreCase)) {
                  LOGGER.warn(
                      "Both API key and Authorization header are set. The API key will be ignored.");
                  builder.apiKey(null);
                }
              }
            });
    Optional.ofNullable(connection.headers()).ifPresent(builder::customHeaders);

    final var modelParameters = connection.model().parameters();
    if (modelParameters != null) {
      final var requestParametersBuilder = OpenAiChatRequestParameters.builder();
      Optional.ofNullable(modelParameters.maxCompletionTokens())
          .ifPresent(requestParametersBuilder::maxCompletionTokens);
      Optional.ofNullable(modelParameters.temperature())
          .ifPresent(requestParametersBuilder::temperature);
      Optional.ofNullable(modelParameters.topP()).ifPresent(requestParametersBuilder::topP);
      Optional.ofNullable(modelParameters.customParameters())
          .ifPresent(requestParametersBuilder::customParameters);

      builder.defaultRequestParameters(requestParametersBuilder.build());
    }

    return builder;
  }
}
