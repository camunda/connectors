/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingmodel;

import com.azure.identity.ClientSecretCredentialBuilder;
import com.google.auth.oauth2.ServiceAccountCredentials;
import dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel;
import dev.langchain4j.model.bedrock.BedrockTitanEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.vertexai.VertexAiEmbeddingModel;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.model.embedding.models.AzureOpenAiEmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.AzureOpenAiEmbeddingModelProvider.AzureAuthentication;
import io.camunda.connector.model.embedding.models.BedrockEmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.BedrockModels;
import io.camunda.connector.model.embedding.models.EmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.GoogleVertexAiEmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.GoogleVertexAiEmbeddingModelProvider.GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication;
import io.camunda.connector.model.embedding.models.OpenAiEmbeddingModelProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

public class DefaultEmbeddingModelFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultEmbeddingModelFactory.class);

  public EmbeddingModel createEmbeddingModel(EmbeddingModelProvider embeddingModelProvider) {
    return switch (embeddingModelProvider) {
      case BedrockEmbeddingModelProvider bedrock -> createBedrockEmbeddingModel(bedrock);
      case OpenAiEmbeddingModelProvider openAi -> createOpenAiEmbeddingModel(openAi);
      case AzureOpenAiEmbeddingModelProvider azureOpenAi ->
          createAzureOpenAiEmbeddingModel(azureOpenAi);
      case GoogleVertexAiEmbeddingModelProvider vertexAi -> createVertexAiEmbeddingModel(vertexAi);
    };
  }

  private EmbeddingModel createAzureOpenAiEmbeddingModel(
      AzureOpenAiEmbeddingModelProvider azureOpenAiEmbeddingModelProvider) {
    final var azureOpenAi = azureOpenAiEmbeddingModelProvider.azureOpenAi();
    AzureOpenAiEmbeddingModel.Builder builder =
        AzureOpenAiEmbeddingModel.builder()
            .endpoint(azureOpenAi.endpoint())
            .deploymentName(azureOpenAi.deploymentName());

    Optional.ofNullable(azureOpenAi.dimensions()).ifPresent(builder::dimensions);
    Optional.ofNullable(azureOpenAi.maxRetries()).ifPresent(builder::maxRetries);
    Optional.ofNullable(azureOpenAi.customHeaders()).ifPresent(builder::customHeaders);

    switch (azureOpenAi.authentication()) {
      case AzureAuthentication.AzureApiKeyAuthentication apiKey -> builder.apiKey(apiKey.apiKey());
      case AzureAuthentication.AzureClientCredentialsAuthentication auth -> {
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
    return builder.build();
  }

  private EmbeddingModel createBedrockEmbeddingModel(
      BedrockEmbeddingModelProvider bedrockEmbeddingModelProvider) {
    final var bedrock = bedrockEmbeddingModelProvider.bedrock();
    return BedrockTitanEmbeddingModel.builder()
        .model(bedrock.resolveSelectedModelName())
        .dimensions(
            bedrock.modelName() == BedrockModels.TitanEmbedTextV2
                ? bedrock.dimensions().getDimensions()
                : null)
        .normalize(
            bedrock.modelName() == BedrockModels.TitanEmbedTextV2 ? bedrock.normalize() : null)
        .region(Region.of(bedrock.region()))
        .maxRetries(bedrock.maxRetries())
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(bedrock.accessKey(), bedrock.secretKey())))
        .build();
  }

  private EmbeddingModel createOpenAiEmbeddingModel(
      OpenAiEmbeddingModelProvider openAiEmbeddingModelProvider) {
    final var openAi = openAiEmbeddingModelProvider.openAi();
    OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder =
        OpenAiEmbeddingModel.builder().apiKey(openAi.apiKey()).modelName(openAi.modelName());

    Optional.ofNullable(openAi.organizationId()).ifPresent(builder::organizationId);
    Optional.ofNullable(openAi.projectId()).ifPresent(builder::projectId);
    Optional.ofNullable(openAi.baseUrl()).ifPresent(builder::baseUrl);
    Optional.ofNullable(openAi.customHeaders()).ifPresent(builder::customHeaders);
    Optional.ofNullable(openAi.dimensions()).ifPresent(builder::dimensions);
    Optional.ofNullable(openAi.maxRetries()).ifPresent(builder::maxRetries);

    return builder.build();
  }

  private EmbeddingModel createVertexAiEmbeddingModel(
      GoogleVertexAiEmbeddingModelProvider provider) {
    final var googleVertexAi = provider.googleVertexAi();
    final var publisher =
        StringUtils.isNotBlank(googleVertexAi.publisher())
            ? googleVertexAi.publisher()
            : GoogleVertexAiEmbeddingModelProvider.VERTEX_AI_DEFAULT_PUBLISHER;
    VertexAiEmbeddingModel.Builder builder =
        VertexAiEmbeddingModel.builder()
            .project(googleVertexAi.projectId())
            .location(googleVertexAi.region())
            .publisher(publisher)
            .modelName(googleVertexAi.modelName())
            .outputDimensionality(googleVertexAi.dimensions())
            .taskType(VertexAiEmbeddingModel.TaskType.RETRIEVAL_DOCUMENT);

    if (googleVertexAi.authentication() instanceof ServiceAccountCredentialsAuthentication sac) {
      builder.credentials(createGoogleServiceAccountCredentials(sac));
    }

    Optional.ofNullable(googleVertexAi.maxRetries()).ifPresent(builder::maxRetries);

    return builder.build();
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
}
