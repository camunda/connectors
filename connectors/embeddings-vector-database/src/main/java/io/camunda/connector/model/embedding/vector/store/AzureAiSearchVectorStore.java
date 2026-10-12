/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.vector.store;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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
      // Declared before the inline authentication it gates below.
      @FEEL
          @Valid
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Azure AI Search credential",
              type = TemplateProperty.PropertyType.Configuration,
              optional = true,
              description =
                  "Choose a reusable Azure AI Search credential, or configure one-time authentication below.")
          AzureAiSearchCredential aiSearchCredential,
      @NestedProperties(
              condition =
                  @PropertyCondition(
                      property = "vectorStore.aiSearch.aiSearchCredential",
                      isEmpty = NullableBoolean.TRUE))
          AzureAuthentication authentication,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Index name",
              description =
                  "The name of the search index. When storing embeddings this index is created or updated automatically.",
              feel = FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String indexName) {

    /** The bound credential's authentication takes precedence; the inline value is the fallback. */
    @Override
    public AzureAuthentication authentication() {
      return aiSearchCredential != null ? aiSearchCredential.authentication() : authentication;
    }

    @AssertTrue(message = "No authentication provided by the credential or the element template")
    @JsonIgnore
    public boolean isAuthenticationPresent() {
      return authentication() != null;
    }

    /** Validates the inline authentication only when no credential is bound. */
    @Valid
    @JsonIgnore
    public AzureAuthentication getInlineAuthenticationWhenNoCredentialBound() {
      return aiSearchCredential != null ? null : authentication;
    }
  }
}
