/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import com.azure.identity.ClientSecretCredentialBuilder;
import com.google.auth.oauth2.ServiceAccountCredentials;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.AwsBedrockRuntimeAuthenticationCustomizer;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureAuthentication.AzureApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration.AzureAuthentication.AzureClientCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration.GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiCompatibleProviderConfiguration.OpenAiCompatibleAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.api.error.ConnectorInputException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

public class ChatModelFactoryImpl implements ChatModelFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChatModelFactoryImpl.class);

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
            .modelName(connection.model().model());

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
            .deploymentName(configuration.azureOpenAi().model().deploymentName());

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

    final var builder =
        BedrockChatModel.builder()
            .client(createBedrockClient(connection))
            .modelId(connection.model().model());

    return applyBedrockModelParametersIfPresent(connection, builder);
  }

  private BedrockRuntimeClient createBedrockClient(
      BedrockProviderConfiguration.BedrockConnection connection) {
    var bedrockClientBuilder =
        BedrockRuntimeClient.builder().region(Region.of(connection.region()));

    var authenticationCustomizer = AwsBedrockRuntimeAuthenticationCustomizer.createFor(connection);
    authenticationCustomizer.provideAuthenticationMechanism(bedrockClientBuilder);

    if (connection.endpoint() != null) {
      bedrockClientBuilder.endpointOverride(URI.create(connection.endpoint()));
    }
    return bedrockClientBuilder.build();
  }

  private BedrockChatModel.Builder applyBedrockModelParametersIfPresent(
      BedrockProviderConfiguration.BedrockConnection connection, BedrockChatModel.Builder builder) {
    final var modelParameters = connection.model().parameters();
    if (modelParameters == null) {
      return builder;
    }

    final var requestParametersBuilder = BedrockChatRequestParameters.builder();
    Optional.ofNullable(modelParameters.maxTokens())
        .ifPresent(requestParametersBuilder::maxOutputTokens);
    Optional.ofNullable(modelParameters.temperature())
        .ifPresent(requestParametersBuilder::temperature);
    Optional.ofNullable(modelParameters.topP()).ifPresent(requestParametersBuilder::topP);

    builder.defaultRequestParameters(requestParametersBuilder.build());

    return builder;
  }

  protected VertexAiGeminiChatModel.VertexAiGeminiChatModelBuilder
      createGoogleVertexAiChatModelBuilder(GoogleVertexAiProviderConfiguration vertexAi) {
    final var connection = vertexAi.googleVertexAi();
    final var builder =
        VertexAiGeminiChatModel.builder()
            .project(connection.projectId())
            .location(connection.region())
            .modelName(connection.model().model());

    if (connection.authentication() instanceof ServiceAccountCredentialsAuthentication sac) {
      builder.credentials(createGoogleServiceAccountCredentials(sac));
    }

    final var modelParameters = connection.model().parameters();
    if (modelParameters != null) {
      Optional.ofNullable(modelParameters.maxOutputTokens()).ifPresent(builder::maxOutputTokens);
      Optional.ofNullable(modelParameters.temperature()).ifPresent(builder::temperature);
      Optional.ofNullable(modelParameters.topP()).ifPresent(builder::topP);
      Optional.ofNullable(modelParameters.topK()).ifPresent(builder::topK);
    }

    return builder;
  }

  private ServiceAccountCredentials createGoogleServiceAccountCredentials(
      ServiceAccountCredentialsAuthentication sac) {
    try {
      return ServiceAccountCredentials.fromStream(
          new ByteArrayInputStream(sac.jsonKey().getBytes(StandardCharsets.UTF_8)));
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
            .modelName(connection.model().model());

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
