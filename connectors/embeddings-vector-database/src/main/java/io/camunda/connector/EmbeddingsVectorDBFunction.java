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

@OutboundConnector(
    name = "Embeddings Vector Database Connector",
    inputVariables = {"vectorDatabaseConnectorOperation", "embeddingModelProvider", "vectorStore"},
    type = "io.camunda:embeddings-vector-database:1")
@ElementTemplate(
    id = "io.camunda.connectors.EmbeddingsVectorDB.v1",
    name = "Embeddings Vector DB Outbound Connector",
    description = "Embed and download documents with vector databases",
    inputDataClass = EmbeddingsVectorDBRequest.class,
    version = 1,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation"),
      @ElementTemplate.PropertyGroup(id = "query", label = "Query"),
      @ElementTemplate.PropertyGroup(id = "embeddingModel", label = "Embedding model"),
      @ElementTemplate.PropertyGroup(id = "embeddingsStore", label = "Vector store"),
      @ElementTemplate.PropertyGroup(id = "document", label = "Document")
    },
    documentationRef =
        "https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/embeddings-vector-db/",
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
