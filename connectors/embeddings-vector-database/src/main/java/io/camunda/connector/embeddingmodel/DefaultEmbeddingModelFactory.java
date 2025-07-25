/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingmodel;

import dev.langchain4j.model.bedrock.BedrockTitanEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.camunda.connector.model.embedding.models.BedrockEmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.BedrockModels;
import io.camunda.connector.model.embedding.models.EmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.OpenAiEmbeddingModelProvider;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

public class DefaultEmbeddingModelFactory {

  public EmbeddingModel createEmbeddingModel(EmbeddingModelProvider embeddingModelProvider) {
    return switch (embeddingModelProvider) {
      case BedrockEmbeddingModelProvider bedrock -> createBedrockEmbeddingModel(bedrock);
      case OpenAiEmbeddingModelProvider openAi -> createOpenAiEmbeddingModel(openAi);
    };
  }

  private EmbeddingModel createBedrockEmbeddingModel(
      BedrockEmbeddingModelProvider bedrockEmbeddingModelProvider) {
    return BedrockTitanEmbeddingModel.builder()
        .model(bedrockEmbeddingModelProvider.resolveSelectedModelName())
        .dimensions(
            bedrockEmbeddingModelProvider.modelName() == BedrockModels.TitanEmbedTextV2
                ? bedrockEmbeddingModelProvider.dimensions().getDimensions()
                : null)
        .normalize(
            bedrockEmbeddingModelProvider.modelName() == BedrockModels.TitanEmbedTextV2
                ? bedrockEmbeddingModelProvider.normalize()
                : null)
        .region(Region.of(bedrockEmbeddingModelProvider.region()))
        .maxRetries(bedrockEmbeddingModelProvider.maxRetries())
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    bedrockEmbeddingModelProvider.accessKey(),
                    bedrockEmbeddingModelProvider.secretKey())))
        .build();
  }

  private EmbeddingModel createOpenAiEmbeddingModel(
      OpenAiEmbeddingModelProvider openAiEmbeddingModelProvider) {
    OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder =
        OpenAiEmbeddingModel.builder()
            .apiKey(openAiEmbeddingModelProvider.apiKey())
            .modelName(openAiEmbeddingModelProvider.modelName());

    Optional.ofNullable(openAiEmbeddingModelProvider.organizationId())
        .ifPresent(builder::organizationId);
    Optional.ofNullable(openAiEmbeddingModelProvider.projectId()).ifPresent(builder::projectId);
    Optional.ofNullable(openAiEmbeddingModelProvider.baseUrl()).ifPresent(builder::baseUrl);
    Optional.ofNullable(openAiEmbeddingModelProvider.customHeaders())
        .ifPresent(builder::customHeaders);
    Optional.ofNullable(openAiEmbeddingModelProvider.dimensions()).ifPresent(builder::dimensions);
    Optional.ofNullable(openAiEmbeddingModelProvider.maxRetries()).ifPresent(builder::maxRetries);

    return builder.build();
  }
}
