/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DefaultValueType;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(label = "AWS Bedrock", id = BedrockEmbeddingModelProvider.BEDROCK_MODEL_PROVIDER)
public record BedrockEmbeddingModelProvider(@Valid @NotNull Configuration bedrock)
    implements EmbeddingModelProvider {

  @TemplateProperty(ignore = true)
  public static final String BEDROCK_MODEL_PROVIDER = "bedrockModelProvider";

  public record Configuration(
      // Declared first so the chooser renders before the inline fallback fields it gates below.
      @FEEL
          @Valid
          @TemplateProperty(
              group = "embeddingModel",
              label = "AWS Bedrock credential",
              type = TemplateProperty.PropertyType.Configuration,
              optional = true,
              description =
                  "Choose a reusable AWS Bedrock credential, or configure one-time access keys below.")
          BedrockEmbeddingCredential bedrockCredential,
      @TemplateProperty(
              group = "embeddingModel",
              label = "Access key",
              description =
                  "Provide an IAM access key tailored to a user, equipped with the necessary permissions",
              constraints = @PropertyConstraints(notEmpty = true),
              condition =
                  @PropertyCondition(
                      property = "embeddingModelProvider.bedrock.bedrockCredential",
                      isEmpty = NullableBoolean.TRUE))
          String accessKey,
      @TemplateProperty(
              group = "embeddingModel",
              label = "Secret key",
              description = "Provide a secret key associated with the access key",
              constraints = @PropertyConstraints(notEmpty = true),
              condition =
                  @PropertyCondition(
                      property = "embeddingModelProvider.bedrock.bedrockCredential",
                      isEmpty = NullableBoolean.TRUE))
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
              label = "Model name",
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
              defaultValue = "D1024",
              condition =
                  @PropertyCondition(
                      property = "embeddingModelProvider.bedrock.modelName",
                      oneOf = {"TitanEmbedTextV2", "amazon.titan-embed-text-v2:0"}))
          BedrockDimensions dimensions,
      @TemplateProperty(
              group = "embeddingModel",
              label = "Normalize",
              description = "Normalize vector",
              defaultValueType = DefaultValueType.Boolean,
              defaultValue = "false",
              condition =
                  @PropertyCondition(
                      property = "embeddingModelProvider.bedrock.modelName",
                      oneOf = {"TitanEmbedTextV2", "amazon.titan-embed-text-v2:0"}))
          Boolean normalize,
      @TemplateProperty(
              group = "embeddingModel",
              label = "Max retries",
              description = "Max retries",
              defaultValueType = DefaultValueType.Number,
              defaultValue = "3")
          Integer maxRetries) {

    /** The bound credential's access key takes precedence; the inline accessKey is the fallback. */
    @Override
    public String accessKey() {
      return bedrockCredential != null ? bedrockCredential.accessKey() : accessKey;
    }

    /** The bound credential's secret key takes precedence; the inline secretKey is the fallback. */
    @Override
    public String secretKey() {
      return bedrockCredential != null ? bedrockCredential.secretKey() : secretKey;
    }

    @AssertTrue(message = "No access key provided by the credential or the element template")
    @JsonIgnore
    public boolean isAccessKeyPresent() {
      return accessKey() != null && !accessKey().isBlank();
    }

    @AssertTrue(message = "No secret key provided by the credential or the element template")
    @JsonIgnore
    public boolean isSecretKeyPresent() {
      return secretKey() != null && !secretKey().isBlank();
    }

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
