/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.document.DocumentReturn;
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
    inputVariables = {"graphql", "authentication", "documentReturnFormat"},
    type = "io.camunda:connector-graphql:1")
@ElementTemplate(
    engineVersion = "^8.10",
    id = "io.camunda.connectors.GraphQL.v1",
    name = "Send GraphQL Request",
    description = "Execute GraphQL query",
    keywords = {
      "API",
      "query",
      "mutation",
      "HTTP",
      "web request",
      "GraphQL",
      "fetch data",
      "data query",
      "execute query",
      "API call"
    },
    inputDataClass = GraphQLRequest.class,
    version = 11,
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

  private final ObjectMapper objectMapper;

  public GraphQLFunction() {
    this(ConnectorsObjectMapperSupplier.getCopy());
  }

  public GraphQLFunction(final ObjectMapper objectMapper) {
    this.httpService = new HttpService();
    this.graphQLRequestMapper = new GraphQLRequestMapper(objectMapper);
    this.objectMapper = objectMapper;
  }

  static final String GRAPHQL_ERROR_CODE = "GRAPHQL_ERROR";

  @Override
  public Object execute(OutboundConnectorContext context) {
    var graphQLRequest = context.bindVariables(GraphQLRequest.class);
    HttpCommonRequest commonRequest = graphQLRequestMapper.toHttpCommonRequest(graphQLRequest);
    LOGGER.debug("Executing graphql connector with request {}", commonRequest);
    var responseChoice = context.readDocumentReturnFormat().map(f -> f.choice()).orElse(null);
    var rawResult = httpService.executeConnectorRequest(commonRequest, context, responseChoice);
    if (rawResult instanceof DocumentReturn<?> documentReturn) {
      // The body is only materialized inside the wrap lambda, so run the error check there too.
      return new DocumentReturn<>(
          documentReturn.payload(),
          (converted, choice) ->
              failOnGraphQLErrors(documentReturn.wrap().apply(converted, choice)));
    }
    return failOnGraphQLErrors(rawResult);
  }

  /** Raises a {@code GRAPHQL_ERROR} when the body is a GraphQL error envelope, else returns it. */
  private Object failOnGraphQLErrors(Object rawResult) {
    if (!(rawResult instanceof HttpCommonResult result)) {
      return rawResult;
    }
    // JSON gives a Map directly; TEXT gives a String we parse so error detection still fires. A
    // DOCUMENT body is a reference we cannot inspect inline (as with the legacy storeResponse
    // path).
    Map<?, ?> body = asErrorEnvelope(result.body());
    if (body != null && body.get("errors") instanceof List<?> errors && !errors.isEmpty()) {
      var firstMessage =
          errors.get(0) instanceof Map<?, ?> firstError
                  && firstError.get("message") instanceof String msg
              ? msg
              : "GraphQL response contains errors";
      var responseVariables = new HashMap<String, Object>();
      responseVariables.put("body", body);
      responseVariables.put("headers", result.headers());
      throw new ConnectorExceptionBuilder()
          .errorCode(GRAPHQL_ERROR_CODE)
          .message(firstMessage)
          .errorVariables(Map.of("response", responseVariables))
          .build();
    }
    return result;
  }

  /**
   * Returns the body as a Map if it is one or parses to one (TEXT choice); {@code null} otherwise.
   */
  private Map<?, ?> asErrorEnvelope(Object body) {
    if (body instanceof Map<?, ?> map) {
      return map;
    }
    if (body instanceof String text) {
      try {
        return objectMapper.readValue(text, Map.class);
      } catch (Exception e) {
        return null; // not JSON, so not a GraphQL error envelope
      }
    }
    return null;
  }
}
