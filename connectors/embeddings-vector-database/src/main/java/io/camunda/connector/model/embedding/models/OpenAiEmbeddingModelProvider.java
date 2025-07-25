/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.models;

import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DefaultValueType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;

@TemplateSubType(label = "OpenAI", id = OpenAiEmbeddingModelProvider.OPEN_AI_MODEL_PROVIDER)
public record OpenAiEmbeddingModelProvider(
    @NotBlank
        @TemplateProperty(
            group = "embeddingModel",
            id = "openAiApiKey",
            label = "OpenAI API key",
            type = TemplateProperty.PropertyType.String,
            feel = Property.FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String apiKey,
    @NotBlank
        @TemplateProperty(
            group = "embeddingModel",
            label = "Model name",
            id = "openAiModelName",
            description =
                "Specify the model name. Details in the <a href=\"https://platform.openai.com/docs/guides/embeddings\" target=\"_blank\">documentation</a>.",
            type = TemplateProperty.PropertyType.String,
            feel = Property.FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String modelName,
    @TemplateProperty(
            group = "embeddingModel",
            label = "Organization ID",
            id = "openAiOrganizationId",
            description =
                "For members of multiple organizations. Details in the <a href=\"https://platform.openai.com/docs/api-reference/authentication\" target=\"_blank\">documentation</a>.",
            type = TemplateProperty.PropertyType.String,
            feel = Property.FeelMode.optional,
            optional = true)
        String organizationId,
    @TemplateProperty(
            group = "embeddingModel",
            label = "Project ID",
            id = "openAiProjectId",
            description =
                "For accounts with multiple projects. Details in the <a href=\"https://platform.openai.com/docs/api-reference/authentication\" target=\"_blank\">documentation</a>.",
            type = TemplateProperty.PropertyType.String,
            feel = Property.FeelMode.optional,
            optional = true)
        String projectId,
    @TemplateProperty(
            group = "embeddingModel",
            id = "openAiDimensions",
            label = "Embedding dimensions",
            description = "Max segment size in chars",
            feel = Property.FeelMode.required,
            type = TemplateProperty.PropertyType.Number,
            optional = true)
        Integer dimensions,
    @TemplateProperty(
            group = "embeddingModel",
            id = "openAiMaxRetries",
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
            id = "openAiCustomHeaders",
            description = "Map of custom HTTP headers to add to the request.",
            feel = Property.FeelMode.required,
            optional = true)
        Map<String, String> customHeaders,
    @TemplateProperty(
            group = "embeddingModel",
            id = "openAiBaseUrl",
            label = "Custom base URL",
            tooltip = "Base URL of OpenAI API. The default is 'https://api.openai.com/v1/'",
            feel = Property.FeelMode.optional,
            type = TemplateProperty.PropertyType.String,
            optional = true)
        String baseUrl)
    implements EmbeddingModelProvider {
  @TemplateProperty(ignore = true)
  public static final String OPEN_AI_MODEL_PROVIDER = "OPEN_AI_MODEL_PROVIDER";

  @Override
  public String toString() {
    return "OpenAiEmbeddingModelProvider{apiKey=[REDACTED], modelName=%s, organizationId=%s, projectId=%s, dimensions=%d, maxRetries=%d, headers=%s, baseUrl='%s'}"
        .formatted(
            modelName, organizationId, projectId, dimensions, maxRetries, customHeaders, baseUrl);
  }
}
