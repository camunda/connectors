/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.vector.store;

import static io.camunda.connector.model.embedding.vector.store.EmbeddingsVectorStore.STORE_ELASTICSEARCH;
import static io.camunda.connector.model.embedding.vector.store.EmbeddingsVectorStore.STORE_PG_VECTOR;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "method")
@JsonSubTypes({
  @JsonSubTypes.Type(value = PgVectorVectorStore.class, name = STORE_PG_VECTOR),
  @JsonSubTypes.Type(value = ElasticSearchVectorStore.class, name = STORE_ELASTICSEARCH)
})
@TemplateDiscriminatorProperty(
    name = "name",
    id = "vectorStore",
    group = "embeddingsStore",
    defaultValue = STORE_ELASTICSEARCH,
    label = "Embeddings store",
    description = "Select embedding store")
public sealed interface EmbeddingsVectorStore
    permits PgVectorVectorStore, ElasticSearchVectorStore {
  String STORE_PG_VECTOR = "STORE_PG_VECTOR";
  String STORE_ELASTICSEARCH = "STORE_ELASTICSEARCH";
}
