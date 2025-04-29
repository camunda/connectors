/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.fixture;

import io.camunda.connector.model.embedding.models.BedrockDimensions;
import io.camunda.connector.model.embedding.models.BedrockEmbeddingModel;
import io.camunda.connector.model.embedding.models.BedrockModels;
import io.camunda.connector.model.embedding.models.OllamaEmbeddingModel;

public class EmbeddingModelProviderFixture {

  public static OllamaEmbeddingModel createDefaultOllamaEmbeddingModel() {
    return new OllamaEmbeddingModel("https://ollama.local:12345", "llama3.1");
  }

  public static BedrockEmbeddingModel createDefaultBedrockEmbeddingModel() {
    return new BedrockEmbeddingModel(
        "ACCESS_KEY",
        "SECRET_KEY",
        "us-east-1",
        BedrockModels.TitanEmbedTextV2,
        null,
        BedrockDimensions.D1024,
        false,
        3);
  }
}
