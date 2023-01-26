/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql.services;

import static io.camunda.connector.graphql.utils.Timeout.setTimeout;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.Gson;
import io.camunda.connector.api.error.ConnectorException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HTTPService {

  private static final Logger LOGGER = LoggerFactory.getLogger(HTTPService.class);

  private final Gson gson;
  private final GsonFactory gsonFactory;
  private final HttpRequestFactory requestFactory;

  private static HTTPService instance;

  private HTTPService(
      final Gson gson, final HttpRequestFactory requestFactory, final GsonFactory gsonFactory) {
    this.gson = gson;
    this.requestFactory = requestFactory;
    this.gsonFactory = gsonFactory;
  }

  public static HTTPService getInstance(
      final Gson gson, final HttpRequestFactory requestFactory, final GsonFactory gsonFactory) {
    if (instance == null) {
      instance = new HTTPService(gson, requestFactory, gsonFactory);
    }
    return instance;
  }

  public HttpRequest createRequest(final GraphQLRequest request, String bearerToken)
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

  public HttpHeaders createHeaders(final GraphQLRequest request, String bearerToken) {
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

  public HttpResponse executeHttpRequest(HttpRequest externalRequest) throws IOException {
    try {
      return externalRequest.execute();
    } catch (HttpResponseException httpResponseException) {
      var errorCode = String.valueOf(httpResponseException.getStatusCode());
      var errorMessage = httpResponseException.getMessage();
      throw new ConnectorException(errorCode, errorMessage, httpResponseException);
    }
  }

  public GraphQLResult toHttpJsonResponse(final HttpResponse externalResponse) {
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
