/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agents.aiagent.provider;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import io.camunda.connector.agents.aiagent.model.request.AgentRequest;
import io.camunda.connector.agents.aiagent.model.request.ProviderConfiguration;
import io.camunda.connector.agents.aiagent.model.request.ProviderConfiguration.AnthropicProviderConfiguration;
import io.camunda.connector.agents.aiagent.model.request.ProviderConfiguration.BedrockProviderConfiguration;
import io.camunda.connector.agents.aiagent.model.request.ProviderConfiguration.OpenAiProviderConfiguration;
import io.camunda.connector.aws.model.impl.AwsAuthentication.AwsDefaultCredentialsChainAuthentication;
import io.camunda.connector.aws.model.impl.AwsAuthentication.AwsStaticCredentialsAuthentication;
import java.net.URI;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

public class ChatModelFactory {

  public ChatLanguageModel createChatModel(AgentRequest request) {
    return switch (request.provider()) {
      case AnthropicProviderConfiguration anthropic -> createAnthropicChatModel(anthropic);
      case BedrockProviderConfiguration bedrock -> createBedrockChatModel(bedrock);
      case OpenAiProviderConfiguration openai -> createOpenaiChatModel(openai);
    };
  }

  private AnthropicChatModel createAnthropicChatModel(
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
    Optional.ofNullable(modelParameters.temperature()).ifPresent(builder::temperature);
    Optional.ofNullable(modelParameters.maxOutputTokens()).ifPresent(builder::maxTokens);
    Optional.ofNullable(modelParameters.topP()).ifPresent(builder::topP);
    Optional.ofNullable(modelParameters.topK()).ifPresent(builder::topK);

    return builder.build();
  }

  private BedrockChatModel createBedrockChatModel(BedrockProviderConfiguration configuration) {
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

    return BedrockChatModel.builder()
        .client(bedrockClientBuilder.build())
        .modelId(connection.model().model())
        .defaultRequestParameters(
            applyModelParameters(
                    DefaultChatRequestParameters.builder(), connection.model().parameters())
                .build())
        .build();
  }

  private OpenAiChatModel createOpenaiChatModel(OpenAiProviderConfiguration configuration) {
    final var connection = configuration.openai();

    final var modelBuilder =
        OpenAiChatModel.builder()
            .apiKey(connection.authentication().apiKey())
            .modelName(connection.model().model())
            .defaultRequestParameters(
                applyModelParameters(
                        OpenAiChatRequestParameters.builder(), connection.model().parameters())
                    .build());

    Optional.ofNullable(connection.authentication().organization())
        .ifPresent(modelBuilder::organizationId);
    Optional.ofNullable(connection.authentication().project()).ifPresent(modelBuilder::projectId);
    Optional.ofNullable(connection.endpoint()).ifPresent(modelBuilder::baseUrl);

    return modelBuilder.build();
  }

  private DefaultChatRequestParameters.Builder<?> applyModelParameters(
      DefaultChatRequestParameters.Builder<?> builder,
      ProviderConfiguration.ModelParameters modelParameters) {
    Optional.ofNullable(modelParameters.temperature()).ifPresent(builder::temperature);
    Optional.ofNullable(modelParameters.maxOutputTokens()).ifPresent(builder::maxOutputTokens);
    Optional.ofNullable(modelParameters.topP()).ifPresent(builder::topP);
    Optional.ofNullable(modelParameters.topK()).ifPresent(builder::topK);
    return builder;
  }
}
