/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

import io.camunda.connector.model.embedding.models.EmbeddingModelProvider;
import io.camunda.connector.model.embedding.vector.store.EmbeddingsVectorStore;
import io.camunda.connector.model.operation.VectorDatabaseConnectorOperation;

public record EmbeddingsVectorDBRequest(
    VectorDatabaseConnectorOperation vectorDatabaseConnectorOperation,
    EmbeddingModelProvider embeddingModelProvider,
    EmbeddingsVectorStore vectorStore) {}
