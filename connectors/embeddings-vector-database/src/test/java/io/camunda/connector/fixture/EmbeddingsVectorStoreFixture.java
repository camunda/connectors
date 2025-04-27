/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.fixture;

import io.camunda.connector.model.embedding.vector.store.AmazonManagedOpenSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.ElasticSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.PgVectorVectorStore;

public class EmbeddingsVectorStoreFixture {

  public static PgVectorVectorStore createPgVectorVectorStore() {
    return new PgVectorVectorStore(
        "pgvector.local:5432", "vector_db", "usr", "passwodr", "embeddings_table");
  }

  public static ElasticSearchVectorStore createElasticSearchVectorStore() {
    return new ElasticSearchVectorStore(
        "https://elastic.local:9200", "elastic", "changeme", "embeddings_idx");
  }

  public static AmazonManagedOpenSearchVectorStore createAmazonManagedOpenVectorStore() {
    return new AmazonManagedOpenSearchVectorStore(
        "ACCESS_KEY", "SECRET_KEY", "https://opensearch.aws", "us-east-1", "embeddings_idx");
  }
}
