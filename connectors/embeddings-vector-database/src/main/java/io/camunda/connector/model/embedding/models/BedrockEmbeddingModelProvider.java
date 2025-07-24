/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.models;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DefaultValueType;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(label = "AWS Bedrock", id = BedrockEmbeddingModelProvider.BEDROCK_MODEL_PROVIDER)
public record BedrockEmbeddingModelProvider(
    @NotBlank
        @TemplateProperty(
            group = "embeddingModel",
            id = "bedrockAccessKey",
            label = "Access key",
            description =
                "Provide an IAM access key tailored to a user, equipped with the necessary permissions")
        String accessKey,
    @NotBlank
        @TemplateProperty(
            group = "embeddingModel",
            id = "bedrockSecretKey",
            label = "Secret key",
            description = "Provide a secret key associated with the access key")
        String secretKey,
    @NotBlank
        @TemplateProperty(
            group = "embeddingModel",
            id = "bedrockRegion",
            label = "Region",
            description = "AWS Bedrock region")
        String region,
    @NotBlank
        @TemplateProperty(
            group = "embeddingModel",
            id = "modelName",
            label = "Model name",
            feel = Property.FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            description = "Bedrock model name or identifier",
            defaultValue = "TitanEmbedTextV2")
        BedrockModels modelName,
    @TemplateProperty(
            group = "embeddingModel",
            id = "bedrockCustomModelName",
            label = "Custom model name",
            feel = FeelMode.optional,
            constraints = @PropertyConstraints(notEmpty = true),
            condition =
                @PropertyCondition(
                    property = "embeddingModelProvider.modelName",
                    equals = "Custom"))
        String customModelName,
    @NotBlank
        @TemplateProperty(
            group = "embeddingModel",
            id = "bedrockDimensions",
            label = "Embedding dimensions",
            description = "Max segment size in chars",
            feel = Property.FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            defaultValue = "D1024",
            condition =
                @PropertyCondition(
                    property = "embeddingModelProvider.modelName",
                    equals = "TitanEmbedTextV2"))
        BedrockDimensions dimensions,
    @NotBlank
        @TemplateProperty(
            group = "embeddingModel",
            id = "bedrockNormalize",
            label = "Normalize",
            description = "Normalize vector",
            defaultValueType = DefaultValueType.Boolean,
            defaultValue = "false",
            condition =
                @PropertyCondition(
                    property = "embeddingModelProvider.modelName",
                    equals = "TitanEmbedTextV2"))
        Boolean normalize,
    @TemplateProperty(
            group = "embeddingModel",
            id = "bedrockMaxRetries",
            label = "Max retries",
            description = "Max retries",
            defaultValueType = DefaultValueType.Number,
            defaultValue = "3")
        Integer maxRetries)
    implements EmbeddingModelProvider {
  @TemplateProperty(ignore = true)
  public static final String BEDROCK_MODEL_PROVIDER = "BEDROCK_MODEL_PROVIDER";

  public String resolveSelectedModelName() {
    if (modelName == BedrockModels.Custom) {
      return customModelName;
    } else {
      return modelName.getModelName();
    }
  }

  @Override
  public String toString() {
    return "BedrockEmbeddingModel{accessKey='[REDACTED]', secretKey='[REDACTED]', region='%s', modelName='%s', customModelName='%s', dimensions=%s, normalize=%s, maxRetries=%d}"
        .formatted(region, modelName, customModelName, dimensions, normalize, maxRetries);
  }
}
