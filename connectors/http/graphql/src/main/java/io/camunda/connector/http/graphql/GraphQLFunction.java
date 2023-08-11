/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.graphql;

import static io.camunda.connector.http.base.utils.Timeout.setTimeout;

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
import io.camunda.connector.api.config.ConnectorConfigurationUtil;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.http.base.auth.OAuthAuthentication;
import io.camunda.connector.http.base.constants.Constants;
import io.camunda.connector.http.base.model.CommonRequest;
import io.camunda.connector.http.base.model.CommonResult;
import io.camunda.connector.http.base.services.AuthenticationService;
import io.camunda.connector.http.base.services.HTTPProxyService;
import io.camunda.connector.http.base.services.HTTPService;
import io.camunda.connector.http.graphql.components.GsonComponentSupplier;
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
    name = "GRAPHQL",
    inputVariables = {"graphql", "authentication"},
    type = "io.camunda:connector-graphql:1")
public class GraphQLFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(GraphQLFunction.class);

  private final Gson gson;
  private final GsonFactory gsonFactory;
  private final HttpRequestFactory requestFactory;

  private final String proxyFunctionUrl;

  public GraphQLFunction() {
    this(ConnectorConfigurationUtil.getProperty(Constants.PROXY_FUNCTION_URL_ENV_NAME));
  }

  public GraphQLFunction(String proxyFunctionUrl) {
    this(
        GsonComponentSupplier.gsonInstance(),
        HttpTransportComponentSupplier.httpRequestFactoryInstance(),
        GsonComponentSupplier.gsonFactoryInstance(),
        proxyFunctionUrl);
  }

  public GraphQLFunction(
      final Gson gson,
      final HttpRequestFactory requestFactory,
      final GsonFactory gsonFactory,
      final String proxyFunctionUrl) {
    this.gson = gson;
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
    return httpService.toHttpResponse(httpResponse, GraphQLResult.class);
  }

  private CommonResult executeGraphQLConnectorViaProxy(GraphQLRequest request) throws IOException {
    CommonRequest commonRequest = GraphQLRequestMapper.toCommonRequest(request);
    HTTPService httpService = new HTTPService(gson);

    HttpRequest httpRequest =
        HTTPProxyService.toRequestViaProxy(requestFactory, commonRequest, proxyFunctionUrl);

    HttpResponse httpResponse = httpService.executeHttpRequest(httpRequest, true);

    try (InputStream responseContentStream = httpResponse.getContent();
        Reader reader = new InputStreamReader(responseContentStream)) {
      final CommonResult jsonResult = gson.fromJson(reader, CommonResult.class);
      LOGGER.debug("Proxy returned result: " + jsonResult);
      return jsonResult;
    } catch (final Exception e) {
      LOGGER.debug("Failed to parse external response: {}", httpResponse, e);
      throw new ConnectorException("Failed to parse result: " + e.getMessage(), e);
    }
  }

  public HttpRequest createRequest(
      final HTTPService httpService, final GraphQLRequest request, String bearerToken)
      throws IOException {
    final String method = request.getMethod().toUpperCase();
    final GenericUrl genericUrl = new GenericUrl(request.getUrl());
    HttpContent content = null;
    final HttpHeaders headers = httpService.createHeaders(request, bearerToken);
    final Map<String, Object> queryAndVariablesMap =
        JsonSerializeHelper.queryAndVariablesToMap(request);
    if (Constants.POST.equalsIgnoreCase(method)) {
      content = new JsonHttpContent(gsonFactory, queryAndVariablesMap);
    } else {
      genericUrl.putAll(queryAndVariablesMap);
    }

    final var httpRequest = requestFactory.buildRequest(method, genericUrl, content);
    httpRequest.setFollowRedirects(false);
    setTimeout(request, httpRequest);
    httpRequest.setHeaders(headers);

    return httpRequest;
  }
}
