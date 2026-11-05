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
import java.util.Map;

@TemplateSubType(label = "OpenAI", id = OpenAiEmbeddingModelProvider.OPEN_AI_MODEL_PROVIDER)
public record OpenAiEmbeddingModelProvider(@Valid @NotNull Configuration openAi)
    implements EmbeddingModelProvider {

  @TemplateProperty(ignore = true)
  public static final String OPEN_AI_MODEL_PROVIDER = "openAiModelProvider";

  public record Configuration(
      @NotBlank
          @TemplateProperty(
              group = "embeddingModel",
              label = "OpenAI API key",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String apiKey,
      @NotBlank
          @TemplateProperty(
              group = "embeddingModel",
              label = "Model name",
              description =
                  "Specify the model name. Details in the <a href=\"https://platform.openai.com/docs/guides/embeddings\" target=\"_blank\">documentation</a>.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String modelName,
      @TemplateProperty(
              group = "embeddingModel",
              label = "Organization ID",
              description =
                  "For members of multiple organizations. Details in the <a href=\"https://platform.openai.com/docs/api-reference/authentication\" target=\"_blank\">documentation</a>.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              optional = true)
          String organizationId,
      @TemplateProperty(
              group = "embeddingModel",
              label = "Project ID",
              description =
                  "For accounts with multiple projects. Details in the <a href=\"https://platform.openai.com/docs/api-reference/authentication\" target=\"_blank\">documentation</a>.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              optional = true)
          String projectId,
      @TemplateProperty(
              group = "embeddingModel",
              label = "Embedding dimensions",
              description =
                  "The size of the vector used to represent data. If not specified, the default model dimensions are used. Details in the <a href=\"https://platform.openai.com/docs/guides/embeddings\" target=\"_blank\">documentation</a>.",
              feel = FeelMode.required,
              type = TemplateProperty.PropertyType.Number,
              optional = true)
          Integer dimensions,
      @TemplateProperty(
              group = "embeddingModel",
              label = "Max retries",
              description = "Max retries",
              defaultValueType = DefaultValueType.Number,
              defaultValue = "3",
              optional = true)
          Integer maxRetries,
      @FEEL
          @TemplateProperty(
              group = "embeddingModel",
              label = "Custom headers",
              description = "Map of custom HTTP headers to add to the request.",
              feel = FeelMode.required,
              optional = true)
          Map<String, String> customHeaders,
      @TemplateProperty(
              group = "embeddingModel",
              label = "Custom base URL",
              tooltip = "Base URL of OpenAI API. The default is 'https://api.openai.com/v1/'",
              feel = FeelMode.optional,
              type = TemplateProperty.PropertyType.String,
              optional = true)
          String baseUrl) {

    @Override
    public String toString() {
      return "Configuration{apiKey=[REDACTED], modelName=%s, organizationId=%s, projectId=%s, dimensions=%d, maxRetries=%d, headers=%s, baseUrl='%s'}"
          .formatted(
              modelName, organizationId, projectId, dimensions, maxRetries, customHeaders, baseUrl);
    }
  }
}
