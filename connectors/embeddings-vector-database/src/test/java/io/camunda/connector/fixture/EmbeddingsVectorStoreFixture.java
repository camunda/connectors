/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.fixture;

import io.camunda.connector.model.embedding.vector.store.AmazonManagedOpenSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.AzureAiSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.AzureAuthentication;
import io.camunda.connector.model.embedding.vector.store.AzureCosmosDbNoSqlVectorStore;
import io.camunda.connector.model.embedding.vector.store.ElasticsearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.OpenSearchVectorStore;

public class EmbeddingsVectorStoreFixture {

  public static ElasticsearchVectorStore createElasticsearchVectorStore() {
    return new ElasticsearchVectorStore(
        new ElasticsearchVectorStore.Configuration(
            "https://elastic.local:9200", "elastic", "changeme", "embeddings_idx"));
  }

  public static OpenSearchVectorStore createOpenSearchVectorStore() {
    return new OpenSearchVectorStore(
        new OpenSearchVectorStore.Configuration(
            "https://opensearch.local:9200", "opensearch", "changeme", "embeddings_idx"));
  }

  public static AmazonManagedOpenSearchVectorStore createAmazonManagedOpenVectorStore() {
    return new AmazonManagedOpenSearchVectorStore(
        new AmazonManagedOpenSearchVectorStore.Configuration(
            "ACCESS_KEY", "SECRET_KEY", "https://opensearch.aws", "us-east-1", "embeddings_idx"));
  }

  public static AzureCosmosDbNoSqlVectorStore createAzureCosmosDbNoSqlVectorStore() {
    return new AzureCosmosDbNoSqlVectorStore(
        new AzureCosmosDbNoSqlVectorStore.Configuration(
            "https://example.documents.azure.com:443/",
            new AzureAuthentication.AzureApiKeyAuthentication("api-key"),
            "database-name",
            "container-name",
            AzureCosmosDbNoSqlVectorStore.ConsistencyLevel.STRONG,
            AzureCosmosDbNoSqlVectorStore.DistanceFunction.COSINE,
            AzureCosmosDbNoSqlVectorStore.IndexType.FLAT));
  }

  public static AzureAiSearchVectorStore createAzureAiSearchVectorStore() {
    return new AzureAiSearchVectorStore(
        new AzureAiSearchVectorStore.Configuration(
            "https://your-search-service.search.windows.net",
            new AzureAuthentication.AzureApiKeyAuthentication("api-key"),
            "searchindex"));
  }
}
