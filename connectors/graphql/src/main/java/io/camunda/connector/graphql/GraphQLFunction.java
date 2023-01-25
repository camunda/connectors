/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql;

import static org.apache.http.entity.ContentType.APPLICATION_JSON;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.UrlEncodedContent;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.graphql.auth.OAuthAuthentication;
import io.camunda.connector.graphql.components.GsonComponentSupplier;
import io.camunda.connector.graphql.components.HttpTransportComponentSupplier;
import io.camunda.connector.graphql.constants.Constants;
import io.camunda.connector.graphql.model.GraphQLRequest;
import io.camunda.connector.graphql.model.GraphQLResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "GRAPHQL",
    inputVariables = {
      "url",
      "method",
      "authentication",
      "query",
      "variables",
      "connectionTimeoutInSeconds"
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
  public Object execute(OutboundConnectorContext context) throws Exception {
    final var json = context.getVariables();
    final var connectorRequest = gson.fromJson(json, GraphQLRequest.class);
    context.validate(connectorRequest);
    context.replaceSecrets(connectorRequest);
    return executeGraphQLConnector(connectorRequest);
  }

  /////////////////////////////////////////////////////////////////////////////////////
  private GraphQLResult executeGraphQLConnector(final GraphQLRequest connectorRequest)
      throws IOException {
    // connector logic
    LOGGER.info("Executing graphql connector with request {}", connectorRequest);
    String bearerToken = null;
    if (connectorRequest.getAuthentication() != null
        && connectorRequest.getAuthentication() instanceof OAuthAuthentication) {
      final HttpRequest oauthRequest = createOAuthRequest(connectorRequest);
      final HttpResponse oauthResponse = executeHttpRequest(oauthRequest);
      bearerToken = extractAccessToken(oauthResponse);
    }

    final HttpRequest httpRequest = createRequest(connectorRequest, bearerToken);
    HttpResponse httpResponse = executeHttpRequest(httpRequest);
    return toHttpJsonResponse(httpResponse);
  }

  protected HttpRequest createRequest(final GraphQLRequest request, String bearerToken)
      throws IOException {
    final String method = request.getMethod().toUpperCase();
    final GenericUrl genericUrl = new GenericUrl(request.getUrl());
    HttpContent content = null;
    final HttpHeaders headers = createHeaders(request, bearerToken);
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

  protected HttpHeaders createHeaders(final GraphQLRequest request, String bearerToken) {
    final HttpHeaders httpHeaders = new HttpHeaders();
    if (Constants.POST.equalsIgnoreCase(request.getMethod())) {
      httpHeaders.setContentType(APPLICATION_JSON.getMimeType());
    }
    if (request.hasAuthentication()) {
      if (bearerToken != null && !bearerToken.isEmpty()) {
        httpHeaders.setAuthorization("Bearer " + bearerToken);
      }
      request.getAuthentication().setHeaders(httpHeaders);
    }
    return httpHeaders;
  }

  protected String extractAccessToken(HttpResponse oauthResponse) throws IOException {
    String oauthResponseStr = oauthResponse.parseAsString();
    if (oauthResponseStr != null && !oauthResponseStr.isEmpty()) {
      JsonObject jsonObject = gson.fromJson(oauthResponseStr, JsonObject.class);
      if (jsonObject.get(Constants.ACCESS_TOKEN) != null) {
        return jsonObject.get(Constants.ACCESS_TOKEN).getAsString();
      }
    }
    return null;
  }

  protected HttpResponse executeHttpRequest(HttpRequest externalRequest) throws IOException {
    try {
      return externalRequest.execute();
    } catch (HttpResponseException httpResponseException) {
      var errorCode = String.valueOf(httpResponseException.getStatusCode());
      var errorMessage = httpResponseException.getMessage();
      throw new ConnectorException(errorCode, errorMessage, httpResponseException);
    }
  }

  protected HttpRequest createOAuthRequest(GraphQLRequest request) throws IOException {
    OAuthAuthentication authentication = (OAuthAuthentication) request.getAuthentication();

    final GenericUrl genericUrl = new GenericUrl(authentication.getOauthTokenEndpoint());
    Map<String, String> data = getDataForAuthRequestBody(authentication);
    HttpContent content = new UrlEncodedContent(data);
    final String method = Constants.POST;
    final var httpRequest = requestFactory.buildRequest(method, genericUrl, content);
    httpRequest.setFollowRedirects(false);
    setTimeout(request, httpRequest);
    HttpHeaders headers = new HttpHeaders();

    if (authentication.getClientAuthentication().equals(Constants.BASIC_AUTH_HEADER)) {
      headers.setBasicAuthentication(
          authentication.getClientId(), authentication.getClientSecret());
    }
    headers.setContentType(Constants.APPLICATION_X_WWW_FORM_URLENCODED);
    httpRequest.setHeaders(headers);
    return httpRequest;
  }

  protected void setTimeout(GraphQLRequest request, HttpRequest httpRequest) {
    if (request.getConnectionTimeoutInSeconds() != null) {
      long connectionTimeout =
          TimeUnit.SECONDS.toMillis(Long.parseLong(request.getConnectionTimeoutInSeconds()));
      int intConnectionTimeout = Math.toIntExact(connectionTimeout);
      httpRequest.setConnectTimeout(intConnectionTimeout);
      httpRequest.setReadTimeout(intConnectionTimeout);
      httpRequest.setWriteTimeout(intConnectionTimeout);
    }
  }

  private static Map<String, String> getDataForAuthRequestBody(OAuthAuthentication authentication) {
    Map<String, String> data = new HashMap<>();
    data.put(Constants.GRANT_TYPE, authentication.getGrantType());
    data.put(Constants.AUDIENCE, authentication.getAudience());
    data.put(Constants.SCOPE, authentication.getScopes());

    if (authentication.getClientAuthentication().equals(Constants.CREDENTIALS_BODY)) {
      data.put(Constants.CLIENT_ID, authentication.getClientId());
      data.put(Constants.CLIENT_SECRET, authentication.getClientSecret());
    }
    return data;
  }

  protected GraphQLResult toHttpJsonResponse(final HttpResponse externalResponse) {
    final GraphQLResult graphQLResult = new GraphQLResult();
    graphQLResult.setStatus(externalResponse.getStatusCode());
    final Map<String, Object> headers = new HashMap<>();
    externalResponse
        .getHeaders()
        .forEach(
            (k, v) -> {
              if (v instanceof List && ((List<?>) v).size() == 1) {
                headers.put(k, ((List<?>) v).get(0));
              } else {
                headers.put(k, v);
              }
            });
    graphQLResult.setHeaders(headers);
    try (InputStream content = externalResponse.getContent();
        Reader reader = new InputStreamReader(content)) {
      final Object body = gson.fromJson(reader, Object.class);
      if (body != null) {
        graphQLResult.setBody(body);
      }
    } catch (final Exception e) {
      LOGGER.error("Failed to parse external response: {}", externalResponse, e);
    }
    return graphQLResult;
  }
}
