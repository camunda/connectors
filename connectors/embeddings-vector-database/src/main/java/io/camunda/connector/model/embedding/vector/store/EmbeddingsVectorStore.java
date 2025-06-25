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
      name = ElasticSearchVectorStore.STORE_ELASTICSEARCH),
  @JsonSubTypes.Type(
      value = OpenSearchVectorStore.class,
      name = OpenSearchVectorStore.STORE_OPENSEARCH),
  @JsonSubTypes.Type(
      value = AmazonManagedOpenSearchVectorStore.class,
      name = AmazonManagedOpenSearchVectorStore.STORE_AMAZON_MANAGED_OPENSEARCH),
})
@TemplateDiscriminatorProperty(
    name = "storeType",
    id = "vectorStore",
    group = "embeddingsStore",
    defaultValue = ElasticSearchVectorStore.STORE_ELASTICSEARCH,
    label = "Embeddings store",
    description = "Select embedding store")
public sealed interface EmbeddingsVectorStore
    permits AmazonManagedOpenSearchVectorStore, ElasticSearchVectorStore, OpenSearchVectorStore {}
