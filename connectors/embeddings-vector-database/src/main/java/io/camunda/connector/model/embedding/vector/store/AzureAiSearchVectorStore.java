/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.vector.store;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(label = "Azure AI Search", id = AzureAiSearchVectorStore.AZURE_AI_SEARCH_STORE)
public record AzureAiSearchVectorStore(@Valid @NotNull Configuration aiSearch)
    implements EmbeddingsVectorStore {

  @TemplateProperty(ignore = true)
  public static final String AZURE_AI_SEARCH_STORE = "azureAiSearchStore";

  public record Configuration(
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Endpoint",
              description =
                  "Specify Azure AI Search endpoint. Details in the <a href=\"https://learn.microsoft.com/en-us/azure/search/\" target=\"_blank\">documentation</a>.",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String endpoint,
      @Valid @NotNull AzureAuthentication authentication,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Index name",
              description =
                  "The name of the search index. When storing embeddings this index is created or updated automatically.",
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String indexName) {}
}
