/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.models;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DefaultValueType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(label = "AWS Bedrock", id = BedrockEmbeddingModelProvider.BEDROCK_MODEL_PROVIDER)
public record BedrockEmbeddingModelProvider(@Valid @NotNull Configuration bedrock)
    implements EmbeddingModelProvider {

  @TemplateProperty(ignore = true)
  public static final String BEDROCK_MODEL_PROVIDER = "bedrockModelProvider";

  public record Configuration(
      @NotBlank
          @TemplateProperty(
              group = "embeddingModel",
              label = "Access key",
              description =
                  "Provide an IAM access key tailored to a user, equipped with the necessary permissions")
          String accessKey,
      @NotBlank
          @TemplateProperty(
              group = "embeddingModel",
              label = "Secret key",
              description = "Provide a secret key associated with the access key")
          String secretKey,
      @NotBlank
          @TemplateProperty(
              group = "embeddingModel",
              label = "Region",
              description = "AWS Bedrock region")
          String region,
      @FEEL
          @NotBlank
          @TemplateProperty(
              group = "embeddingModel",
              label = "Model",
              feel = FeelMode.optional,
              placeholder = "amazon.titan-embed-text-v2:0",
              tooltip = "Model ID compatible with the Amazon Titan embedding API.")
          String modelName,
      @TemplateProperty(ignore = true) String customModelName,
      @TemplateProperty(
              group = "embeddingModel",
              label = "Embedding dimensions",
              description = "The size of the vector used to represent data",
              feel = FeelMode.required,
              type = TemplateProperty.PropertyType.Dropdown,
              defaultValue = "D1024")
          BedrockDimensions dimensions,
      @TemplateProperty(
              group = "embeddingModel",
              label = "Normalize",
              description = "Normalize vector",
              defaultValueType = DefaultValueType.Boolean,
              defaultValue = "false")
          Boolean normalize,
      @TemplateProperty(
              group = "embeddingModel",
              label = "Max retries",
              description = "Max retries",
              defaultValueType = DefaultValueType.Number,
              defaultValue = "3")
          Integer maxRetries) {

    public String resolveSelectedModelName() {
      if (BedrockModels.Custom.name().equalsIgnoreCase(modelName)) {
        return customModelName;
      }
      for (BedrockModels legacyModel : BedrockModels.values()) {
        if (legacyModel.name().equalsIgnoreCase(modelName)) {
          return legacyModel.getModelName();
        }
      }
      return modelName;
    }

    @Override
    public String toString() {
      return "Configuration{accessKey='[REDACTED]', secretKey='[REDACTED]', region='%s', modelName='%s', customModelName='%s', dimensions=%s, normalize=%s, maxRetries=%d}"
          .formatted(region, modelName, customModelName, dimensions, normalize, maxRetries);
    }
  }
}
