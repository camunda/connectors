/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.fixture;

import io.camunda.connector.model.embedding.vector.store.AmazonManagedOpenSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.ElasticSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.OpenSearchVectorStore;

public class EmbeddingsVectorStoreFixture {

  public static ElasticSearchVectorStore createElasticSearchVectorStore() {
    return new ElasticSearchVectorStore(
        new ElasticSearchVectorStore.Configuration(
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
}
