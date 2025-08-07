/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.vector.store;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "storeType")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = ElasticSearchVectorStore.class,
      name = ElasticSearchVectorStore.ELASTICSEARCH_STORE),
  @JsonSubTypes.Type(
      value = OpenSearchVectorStore.class,
      name = OpenSearchVectorStore.OPEN_SEARCH_STORE),
  @JsonSubTypes.Type(
      value = AmazonManagedOpenSearchVectorStore.class,
      name = AmazonManagedOpenSearchVectorStore.AMAZON_MANAGED_OPENSEARCH_STORE),
  @JsonSubTypes.Type(
      value = AzureCosmosDbNoSqlVectorStore.class,
      name = AzureCosmosDbNoSqlVectorStore.AZURE_COSMOS_DB_NO_SQL_STORE),
  @JsonSubTypes.Type(
      value = AzureAiSearchVectorStore.class,
      name = AzureAiSearchVectorStore.AZURE_AI_SEARCH_STORE),
})
@TemplateDiscriminatorProperty(
    name = "storeType",
    id = "vectorStore",
    group = "embeddingsStore",
    defaultValue = ElasticSearchVectorStore.ELASTICSEARCH_STORE,
    label = "Embeddings store",
    description = "Select embedding store")
public sealed interface EmbeddingsVectorStore
    permits AmazonManagedOpenSearchVectorStore,
        AzureAiSearchVectorStore,
        AzureCosmosDbNoSqlVectorStore,
        ElasticSearchVectorStore,
        OpenSearchVectorStore {}
