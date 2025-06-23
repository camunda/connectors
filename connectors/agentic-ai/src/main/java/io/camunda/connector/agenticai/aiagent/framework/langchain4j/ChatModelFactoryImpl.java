/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.BedrockProviderConfiguration.AwsAuthentication.AwsDefaultCredentialsChainAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.BedrockProviderConfiguration.AwsAuthentication.AwsStaticCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.ProviderConfiguration.OpenAiProviderConfiguration;
import java.net.URI;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

public class ChatModelFactoryImpl implements ChatModelFactory {

  @Override
  public ChatModel createChatModel(ProviderConfiguration providerConfiguration) {
    return switch (providerConfiguration) {
      case AnthropicProviderConfiguration anthropic ->
          createAnthropicChatModelBuilder(anthropic).build();
      case BedrockProviderConfiguration bedrock -> createBedrockChatModelBuilder(bedrock).build();
      case OpenAiProviderConfiguration openai -> createOpenaiChatModelBuilder(openai).build();
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
            .region(Region.of(connection.region()));

    if (connection.endpoint() != null) {
      bedrockClientBuilder.endpointOverride(URI.create(connection.endpoint()));
    }

    final var builder =
        BedrockChatModel.builder()
            .client(bedrockClientBuilder.build())
            .modelId(connection.model().model());

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
    Optional.ofNullable(connection.endpoint()).ifPresent(builder::baseUrl);
    Optional.ofNullable(connection.headers()).ifPresent(builder::customHeaders);

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
}
