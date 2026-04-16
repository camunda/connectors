/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorExceptionBuilder;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.http.base.HttpService;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.graphql.model.GraphQLRequest;
import io.camunda.connector.http.graphql.utils.GraphQLRequestMapper;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "GraphQL",
    inputVariables = {"graphql", "authentication"},
    type = "io.camunda:connector-graphql:1")
@ElementTemplate(
    engineVersion = "^8.9",
    id = "io.camunda.connectors.GraphQL.v1",
    name = "GraphQL Outbound Connector",
    description = "Execute GraphQL query",
    inputDataClass = GraphQLRequest.class,
    version = 9,
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

  static final String GRAPHQL_ERROR_CODE = "GRAPHQL_ERROR";

  @Override
  public Object execute(OutboundConnectorContext context) {
    var graphQLRequest = context.bindVariables(GraphQLRequest.class);
    HttpCommonRequest commonRequest = graphQLRequestMapper.toHttpCommonRequest(graphQLRequest);
    LOGGER.debug("Executing graphql connector with request {}", commonRequest);
    var rawResult = httpService.executeConnectorRequest(commonRequest, context);
    if (!(rawResult instanceof HttpCommonResult result)) {
      return rawResult;
    }
    if (result.body() instanceof Map<?, ?> body
        && body.get("errors") instanceof List<?> errors
        && !errors.isEmpty()) {
      var firstMessage =
          errors.get(0) instanceof Map<?, ?> firstError
                  && firstError.get("message") instanceof String msg
              ? msg
              : "GraphQL response contains errors";
      var responseVariables = new HashMap<String, Object>();
      responseVariables.put("body", result.body());
      responseVariables.put("headers", result.headers());
      throw new ConnectorExceptionBuilder()
          .errorCode(GRAPHQL_ERROR_CODE)
          .message(firstMessage)
          .errorVariables(Map.of("response", responseVariables))
          .build();
    }
    return result;
  }
}
