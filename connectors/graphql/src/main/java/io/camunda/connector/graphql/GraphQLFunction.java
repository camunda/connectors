/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.Gson;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.graphql.auth.OAuthAuthentication;
import io.camunda.connector.graphql.components.GsonComponentSupplier;
import io.camunda.connector.graphql.components.HttpTransportComponentSupplier;
import io.camunda.connector.graphql.model.GraphQLRequest;
import io.camunda.connector.graphql.model.GraphQLResult;
import io.camunda.connector.graphql.services.AuthenticationService;
import io.camunda.connector.graphql.services.HTTPService;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "GRAPHQL",
    inputVariables = {
      "graphql.url",
      "graphql.method",
      "graphql.authentication",
      "graphql.query",
      "graphql.variables",
      "graphql.connectionTimeoutInSeconds"
    },
    type = "io.camunda:connector-graphql:1")
public class GraphQLFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLFunction.class);

  private final Gson gson;
  private final GsonFactory gsonFactory;
  private final HttpRequestFactory requestFactory;

  public GraphQLFunction() {
    this(
        GsonComponentSupplier.gsonInstance(),
        HttpTransportComponentSupplier.httpRequestFactoryInstance(),
        GsonComponentSupplier.gsonFactoryInstance());
  }

  public GraphQLFunction(
      final Gson gson, final HttpRequestFactory requestFactory, final GsonFactory gsonFactory) {
    this.gson = gson;
    this.requestFactory = requestFactory;
    this.gsonFactory = gsonFactory;
  }

  @Override
  public Object execute(OutboundConnectorContext context) throws IOException {
    final var json = context.getVariables();
    final var connectorRequest = gson.fromJson(json, GraphQLRequest.class);
    context.validate(connectorRequest);
    context.replaceSecrets(connectorRequest);
    return executeGraphQLConnector(connectorRequest);
  }

  private GraphQLResult executeGraphQLConnector(final GraphQLRequest connectorRequest)
      throws IOException {
    // connector logic
    LOGGER.info("Executing graphql connector with request {}", connectorRequest);
    HTTPService httpService = HTTPService.getInstance(gson, requestFactory, gsonFactory);
    AuthenticationService authService = AuthenticationService.getInstance(gson, requestFactory);
    String bearerToken = null;
    if (connectorRequest.getAuthentication() != null
        && connectorRequest.getAuthentication() instanceof OAuthAuthentication) {
      final HttpRequest oauthRequest = authService.createOAuthRequest(connectorRequest);
      final HttpResponse oauthResponse = httpService.executeHttpRequest(oauthRequest);
      bearerToken = authService.extractAccessToken(oauthResponse);
    }

    final HttpRequest httpRequest = httpService.createRequest(connectorRequest, bearerToken);
    HttpResponse httpResponse = httpService.executeHttpRequest(httpRequest);
    return httpService.toHttpJsonResponse(httpResponse);
  }
}
