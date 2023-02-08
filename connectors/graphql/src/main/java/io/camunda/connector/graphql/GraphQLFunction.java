/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql;

import static io.camunda.connector.common.utils.Timeout.setTimeout;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.Gson;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.common.auth.OAuthAuthentication;
import io.camunda.connector.common.constants.Constants;
import io.camunda.connector.common.services.AuthenticationService;
import io.camunda.connector.common.services.HTTPService;
import io.camunda.connector.graphql.components.GsonComponentSupplier;
import io.camunda.connector.graphql.components.HttpTransportComponentSupplier;
import io.camunda.connector.graphql.model.GraphQLRequest;
import io.camunda.connector.graphql.model.GraphQLResult;
import io.camunda.connector.graphql.utils.JsonSerializeHelper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "GRAPHQL",
    inputVariables = {"graphql", "authentication"},
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
  public Object execute(OutboundConnectorContext context)
      throws IOException, InstantiationException, IllegalAccessException {
    final var json = context.getVariables();
    var connectorRequest = JsonSerializeHelper.serializeRequest(gson, json);
    context.validate(connectorRequest);
    context.replaceSecrets(connectorRequest);
    return executeGraphQLConnector(connectorRequest);
  }

  private GraphQLResult executeGraphQLConnector(final GraphQLRequest connectorRequest)
      throws IOException, InstantiationException, IllegalAccessException {
    // connector logic
    LOGGER.debug("Executing graphql connector with request {}", connectorRequest);
    HTTPService httpService = new HTTPService(gson);
    AuthenticationService authService = new AuthenticationService(gson, requestFactory);
    String bearerToken = null;
    if (connectorRequest.getAuthentication() != null
        && connectorRequest.getAuthentication() instanceof OAuthAuthentication) {
      final HttpRequest oauthRequest = authService.createOAuthRequest(connectorRequest);
      final HttpResponse oauthResponse = httpService.executeHttpRequest(oauthRequest);
      bearerToken = authService.extractOAuthAccessToken(oauthResponse);
    }

    final HttpRequest httpRequest = createRequest(httpService, connectorRequest, bearerToken);
    HttpResponse httpResponse = httpService.executeHttpRequest(httpRequest);
    return httpService.toHttpJsonResponse(httpResponse, GraphQLResult.class);
  }

  public HttpRequest createRequest(
      final HTTPService httpService, final GraphQLRequest request, String bearerToken)
      throws IOException {
    final String method = request.getMethod().toUpperCase();
    final GenericUrl genericUrl = new GenericUrl(request.getUrl());
    HttpContent content = null;
    final HttpHeaders headers = httpService.createHeaders(request, bearerToken);
    String escapedQuery = request.getQuery().replace("\\n", "").replace("\\\"", "\"");
    if (Constants.POST.equalsIgnoreCase(method)) {
      content = constructBodyForPost(escapedQuery, request.getVariables());
    } else {
      final Map<String, String> query = new HashMap<>();
      query.put("query", escapedQuery);
      if (request.getVariables() != null) {
        query.put("variables", gson.toJsonTree(request.getVariables()).toString());
      }
      genericUrl.putAll(query);
    }

    final var httpRequest = requestFactory.buildRequest(method, genericUrl, content);
    httpRequest.setFollowRedirects(false);
    setTimeout(request, httpRequest);
    httpRequest.setHeaders(headers);

    return httpRequest;
  }

  private JsonHttpContent constructBodyForPost(String escapedQuery, Object variables) {
    final Map<String, Object> body = new HashMap<>();
    body.put("query", escapedQuery);
    if (variables != null) {
      body.put("variables", variables);
    }
    return new JsonHttpContent(gsonFactory, body);
  }
}
