/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.embeddingstore;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.model.embedding.vector.store.AmazonManagedOpenSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.AzureAiSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.AzureCosmosDbNoSqlVectorStore;
import io.camunda.connector.model.embedding.vector.store.ElasticsearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.EmbeddingsVectorStore;
import io.camunda.connector.model.embedding.vector.store.OpenSearchVectorStore;
import io.camunda.connector.model.operation.VectorDatabaseConnectorOperation;

public class DefaultEmbeddingStoreFactory {

  private final AzureVectorStoreFactory azureVectorStoreFactory = new AzureVectorStoreFactory();
  private final ElasticsearchVectorStoreFactory elasticsearchVectorStoreFactory;
  private final OpenSearchVectorStoreFactory openSearchVectorStoreFactory;

  public DefaultEmbeddingStoreFactory(ProxyConfiguration proxyConfig) {
    elasticsearchVectorStoreFactory = new ElasticsearchVectorStoreFactory(proxyConfig);
    openSearchVectorStoreFactory = new OpenSearchVectorStoreFactory(proxyConfig);
  }

  public ClosableEmbeddingStore<TextSegment> initializeVectorStore(
      EmbeddingsVectorStore embeddingsVectorStore,
      EmbeddingModel model,
      VectorDatabaseConnectorOperation operation) {
    return switch (embeddingsVectorStore) {
      case ElasticsearchVectorStore elasticsearchVectorStore ->
          elasticsearchVectorStoreFactory.createElasticsearchVectorStore(elasticsearchVectorStore);
      case OpenSearchVectorStore openSearchVectorStore ->
          openSearchVectorStoreFactory.createOpenSearchVectorStore(openSearchVectorStore);
      case AmazonManagedOpenSearchVectorStore amazonManagedOpenSearchVectorStore ->
          openSearchVectorStoreFactory.createAmazonManagedOpenSearchVectorStore(
              amazonManagedOpenSearchVectorStore);
      case AzureCosmosDbNoSqlVectorStore azureCosmosDbNoSqlVectorStore ->
          azureVectorStoreFactory.createCosmosDbNoSqlVectorStore(
              azureCosmosDbNoSqlVectorStore, model);
      case AzureAiSearchVectorStore azureAiSearchVectorStore ->
          azureVectorStoreFactory.createAiSearchVectorStore(
              azureAiSearchVectorStore, model, operation);
    };
  }
}
