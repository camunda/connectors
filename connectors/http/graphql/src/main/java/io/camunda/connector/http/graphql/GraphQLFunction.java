/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.http.base.HttpService;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.graphql.model.GraphQLRequest;
import io.camunda.connector.http.graphql.utils.GraphQLRequestMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "GraphQL",
    inputVariables = {"graphql", "authentication"},
    type = "io.camunda:connector-graphql:1")
@ElementTemplate(
    id = "io.camunda.connectors.GraphQL.v1",
    name = "GraphQL Outbound Connector",
    description = "Execute GraphQL query",
    inputDataClass = GraphQLRequest.class,
    version = 6,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "endpoint", label = "HTTP Endpoint"),
      @ElementTemplate.PropertyGroup(id = "graphql", label = "GraphQL query"),
      @ElementTemplate.PropertyGroup(id = "timeout", label = "Connection timeout"),
    },
    documentationRef = "https://docs.camunda.io/docs/components/connectors/protocol/graphql/",
    icon = "icon.svg")
public class GraphQLFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLFunction.class);

  private final HttpService httpService;

  private final GraphQLRequestMapper graphQLRequestMapper;

  public GraphQLFunction() {
    this(ConnectorsObjectMapperSupplier.getCopy());
  }

  public GraphQLFunction(final ObjectMapper objectMapper) {
    this.httpService = new HttpService();
    this.graphQLRequestMapper = new GraphQLRequestMapper(objectMapper);
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    var graphQLRequest = context.bindVariables(GraphQLRequest.class);
    HttpCommonRequest commonRequest = graphQLRequestMapper.toHttpCommonRequest(graphQLRequest);
    LOGGER.debug("Executing graphql connector with request {}", commonRequest);
    return httpService.executeConnectorRequest(commonRequest);
  }
}
