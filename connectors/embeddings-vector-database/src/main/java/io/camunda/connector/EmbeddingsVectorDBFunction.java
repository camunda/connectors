/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.model.EmbeddingsVectorDBRequest;

@OutboundConnector(
    name = "Embeddings Vector DB",
    inputVariables = {"authentication", "data"},
    type = "io.camunda:embeddings-vector-database:1")
@ElementTemplate(
    id = "io.camunda.connectors.EmbeddingsVectorDB.v1",
    name = "Embeddings Vector DB Outbound Connector",
    description = "Add more descriptive description",
    inputDataClass = EmbeddingsVectorDBRequest.class,
    version = 1,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "mode", label = "Mode"),
      @ElementTemplate.PropertyGroup(id = "query", label = "Query"),
      @ElementTemplate.PropertyGroup(id = "model", label = "Model"),
      @ElementTemplate.PropertyGroup(id = "embeddingsStore", label = "Embeddings store"),
      @ElementTemplate.PropertyGroup(id = "document", label = "Document")
    },
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/embeddings-vector-db/",
    icon = "icon.svg")
public class EmbeddingsVectorDBFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) {
    var vectorDBRequest = context.bindVariables(EmbeddingsVectorDBRequest.class);
    return null;
  }
}
