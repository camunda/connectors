/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

import io.camunda.connector.action.DefaultActionProcessor;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.model.EmbeddingsVectorDBRequest;
import io.camunda.connector.model.embedding.models.AzureOpenAiEmbeddingCredential;
import io.camunda.connector.model.embedding.models.BedrockEmbeddingCredential;
import io.camunda.connector.model.embedding.models.GoogleVertexAiEmbeddingCredential;
import io.camunda.connector.model.embedding.models.OpenAiEmbeddingCredential;
import io.camunda.connector.model.embedding.vector.store.AmazonManagedOpenSearchCredential;
import io.camunda.connector.model.embedding.vector.store.AzureAiSearchCredential;
import io.camunda.connector.model.embedding.vector.store.AzureCosmosDbNoSqlCredential;
import io.camunda.connector.model.embedding.vector.store.ElasticsearchCredential;
import io.camunda.connector.model.embedding.vector.store.OpenSearchCredential;

@OutboundConnector(
    name = "Embeddings Vector Database Connector",
    inputVariables = {"vectorDatabaseConnectorOperation", "embeddingModelProvider", "vectorStore"},
    type = "io.camunda:embeddings-vector-database:1")
@ElementTemplate(
    engineVersion = "^8.11",
    id = "io.camunda.connectors.EmbeddingsVectorDB.v1",
    name = "Embeddings Vector DB Outbound Connector",
    description = "Embed and download documents with vector databases",
    inputDataClass = EmbeddingsVectorDBRequest.class,
    configurations = {
      OpenAiEmbeddingCredential.class,
      AzureOpenAiEmbeddingCredential.class,
      GoogleVertexAiEmbeddingCredential.class,
      BedrockEmbeddingCredential.class,
      AzureAiSearchCredential.class,
      AzureCosmosDbNoSqlCredential.class,
      AmazonManagedOpenSearchCredential.class,
      OpenSearchCredential.class,
      ElasticsearchCredential.class
    },
    version = 4,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation"),
      @ElementTemplate.PropertyGroup(id = "query", label = "Query"),
      @ElementTemplate.PropertyGroup(id = "embeddingModel", label = "Embedding model"),
      @ElementTemplate.PropertyGroup(id = "embeddingsStore", label = "Vector store"),
      @ElementTemplate.PropertyGroup(id = "document", label = "Document")
    },
    documentationRef =
        "https://docs.camunda.io/docs/8.10/components/connectors/out-of-the-box-connectors/embeddings-vector-db/",
    icon = "icon.svg")
public class EmbeddingsVectorDBFunction implements OutboundConnectorFunction {

  private final DefaultActionProcessor actionProcessor;

  public EmbeddingsVectorDBFunction() {
    this(new DefaultActionProcessor());
  }

  public EmbeddingsVectorDBFunction(final DefaultActionProcessor actionProcessor) {
    this.actionProcessor = actionProcessor;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    final var vectorDBRequest = context.bindVariables(EmbeddingsVectorDBRequest.class);
    return actionProcessor.handleFlow(vectorDBRequest, context);
  }
}
