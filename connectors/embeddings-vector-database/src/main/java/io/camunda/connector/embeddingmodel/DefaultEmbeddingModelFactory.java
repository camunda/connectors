/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingmodel;

import dev.langchain4j.model.bedrock.BedrockTitanEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.camunda.connector.model.embedding.models.BedrockEmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.BedrockModels;
import io.camunda.connector.model.embedding.models.EmbeddingModelProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

public class DefaultEmbeddingModelFactory {

  public EmbeddingModel initializeModel(EmbeddingModelProvider embeddingModelProvider) {
    return switch (embeddingModelProvider) {
      case BedrockEmbeddingModelProvider bedrockEmbeddingModelProvider ->
          initializeBedrockEmbeddingModel(bedrockEmbeddingModelProvider);
    };
  }

  private EmbeddingModel initializeBedrockEmbeddingModel(
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
}
