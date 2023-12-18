/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.graphql;

import static io.camunda.connector.http.base.utils.Timeout.setTimeout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.gson.GsonFactory;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.http.base.auth.OAuthAuthentication;
import io.camunda.connector.http.base.constants.Constants;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.base.services.AuthenticationService;
import io.camunda.connector.http.base.services.HttpInteractionService;
import io.camunda.connector.http.base.services.HttpProxyService;
import io.camunda.connector.http.base.services.HttpRequestMapper;
import io.camunda.connector.http.graphql.components.HttpTransportComponentSupplier;
import io.camunda.connector.http.graphql.model.GraphQLRequest;
import io.camunda.connector.http.graphql.model.GraphQLRequestWrapper;
import io.camunda.connector.http.graphql.model.GraphQLResult;
import io.camunda.connector.http.graphql.utils.GraphQLRequestMapper;
import io.camunda.connector.http.graphql.utils.JsonSerializeHelper;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "GraphQL",
    inputVariables = {"graphql", "authentication"},
    type = "io.camunda:connector-graphql:1")
public class GraphQLFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLFunction.class);

  private final ObjectMapper objectMapper;
  private final GsonFactory gsonFactory;
  private final HttpRequestFactory requestFactory;

  private final String proxyFunctionUrl;

  public GraphQLFunction() {
    this(System.getenv(Constants.PROXY_FUNCTION_URL_ENV_NAME));
  }

  public GraphQLFunction(String proxyFunctionUrl) {
    this(
        ConnectorsObjectMapperSupplier.getCopy(),
        HttpTransportComponentSupplier.httpRequestFactoryInstance(),
        new GsonFactory(),
        proxyFunctionUrl);
  }

  public GraphQLFunction(
      final ObjectMapper objectMapper,
      final HttpRequestFactory requestFactory,
      final GsonFactory gsonFactory,
      final String proxyFunctionUrl) {
    this.objectMapper = objectMapper;
    this.requestFactory = requestFactory;
    this.gsonFactory = gsonFactory;
    this.proxyFunctionUrl = proxyFunctionUrl;
  }

  @Override
  public Object execute(OutboundConnectorContext context)
      throws IOException, InstantiationException, IllegalAccessException {
    var connectorRequest = context.bindVariables(GraphQLRequestWrapper.class);
    GraphQLRequest graphQLRequest = connectorRequest.getGraphql();
    graphQLRequest.setAuthentication(connectorRequest.getAuthentication());
    return StringUtils.isBlank(proxyFunctionUrl)
        ? executeGraphQLConnector(graphQLRequest)
        : executeGraphQLConnectorViaProxy(graphQLRequest);
  }

  private GraphQLResult executeGraphQLConnector(final GraphQLRequest connectorRequest)
      throws IOException, InstantiationException, IllegalAccessException {
    // connector logic
    LOGGER.debug("Executing graphql connector with request {}", connectorRequest);
    HttpInteractionService httpInteractionService = new HttpInteractionService(objectMapper);
    String bearerToken = null;
    if (connectorRequest.getAuthentication() != null
        && connectorRequest.getAuthentication() instanceof OAuthAuthentication) {
      AuthenticationService authService = new AuthenticationService(objectMapper, requestFactory);
      final com.google.api.client.http.HttpRequest oauthRequest =
          authService.createOAuthRequest(connectorRequest);
      final HttpResponse oauthResponse = httpInteractionService.executeHttpRequest(oauthRequest);
      bearerToken = authService.extractOAuthAccessToken(oauthResponse);
    }

    final com.google.api.client.http.HttpRequest httpRequest =
        createRequest(connectorRequest, bearerToken);
    HttpResponse httpResponse = httpInteractionService.executeHttpRequest(httpRequest);
    return httpInteractionService.toHttpResponse(httpResponse, GraphQLResult.class);
  }

  private HttpCommonResult executeGraphQLConnectorViaProxy(GraphQLRequest request)
      throws IOException {
    HttpCommonRequest commonRequest = GraphQLRequestMapper.toHttpCommonRequest(request);
    HttpInteractionService httpInteractionService = new HttpInteractionService(objectMapper);

    com.google.api.client.http.HttpRequest httpRequest =
        HttpProxyService.toRequestViaProxy(requestFactory, commonRequest, proxyFunctionUrl);

    HttpResponse httpResponse = httpInteractionService.executeHttpRequest(httpRequest, true);

    try (InputStream responseContentStream = httpResponse.getContent();
        Reader reader = new InputStreamReader(responseContentStream)) {
      final HttpCommonResult jsonResult = objectMapper.readValue(reader, HttpCommonResult.class);
      LOGGER.debug("Proxy returned result: " + jsonResult);
      return jsonResult;
    } catch (final Exception e) {
      LOGGER.debug("Failed to parse external response: {}", httpResponse, e);
      throw new ConnectorException("Failed to parse result: " + e.getMessage(), e);
    }
  }

  public com.google.api.client.http.HttpRequest createRequest(
      final GraphQLRequest request, String bearerToken) throws IOException {
    final GenericUrl genericUrl = new GenericUrl(request.getUrl());
    HttpContent content = null;
    final HttpHeaders headers = HttpRequestMapper.createHeaders(request, bearerToken);
    final Map<String, Object> queryAndVariablesMap =
        JsonSerializeHelper.queryAndVariablesToMap(request);
    if (HttpMethod.POST.equals(request.getMethod())) {
      content = new JsonHttpContent(gsonFactory, queryAndVariablesMap);
    } else {
      genericUrl.putAll(queryAndVariablesMap);
    }

    final var httpRequest =
        requestFactory.buildRequest(request.getMethod().name(), genericUrl, content);
    httpRequest.setFollowRedirects(false);
    setTimeout(request, httpRequest);
    httpRequest.setHeaders(headers);

    return httpRequest;
  }
}
