/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingmodel;

import dev.langchain4j.model.bedrock.BedrockTitanEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel.OllamaEmbeddingModelBuilder;
import io.camunda.connector.model.embedding.models.BedrockEmbeddingModel;
import io.camunda.connector.model.embedding.models.BedrockModels;
import io.camunda.connector.model.embedding.models.EmbeddingModelProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

public class DefaultEmbeddingModelFactory {

  public EmbeddingModel initializeModel(EmbeddingModelProvider embeddingModelProvider) {
    return switch (embeddingModelProvider) {
      case BedrockEmbeddingModel bedrockEmbeddingModel ->
          initializeBedrockEmbeddingModel(bedrockEmbeddingModel);
      case io.camunda.connector.model.embedding.models.OllamaEmbeddingModel ollamaEmbeddingModel ->
          initializeOllamaModel(ollamaEmbeddingModel);
    };
  }

  private EmbeddingModel initializeOllamaModel(
      io.camunda.connector.model.embedding.models.OllamaEmbeddingModel embeddingModelProvider) {
    return new OllamaEmbeddingModelBuilder()
        .baseUrl(embeddingModelProvider.baseUrl())
        .modelName(embeddingModelProvider.modeName())
        .build();
  }

  private EmbeddingModel initializeBedrockEmbeddingModel(
      BedrockEmbeddingModel bedrockEmbeddingModel) {
    return BedrockTitanEmbeddingModel.builder()
        .model(bedrockEmbeddingModel.resolveSelectedModelName())
        .dimensions(
            bedrockEmbeddingModel.modelName() == BedrockModels.TitanEmbedTextV2
                ? bedrockEmbeddingModel.dimensions().getDimensions()
                : null)
        .normalize(
            bedrockEmbeddingModel.modelName() == BedrockModels.TitanEmbedTextV2
                ? bedrockEmbeddingModel.normalize()
                : null)
        .region(Region.of(bedrockEmbeddingModel.region()))
        .maxRetries(bedrockEmbeddingModel.maxRetries())
        // TODO: AWS Bedrock will be revisited in v2 implementation
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    bedrockEmbeddingModel.accessKey(), bedrockEmbeddingModel.secretKey())))
        .build();
  }
}
