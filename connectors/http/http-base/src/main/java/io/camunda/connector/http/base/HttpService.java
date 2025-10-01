/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.auth.AuthenticationMapper;
import io.camunda.connector.http.client.HttpClientService;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.HttpClientResult;

public class HttpService {

  private final static HttpClientService HTTP_CLIENT = new HttpClientService();

  public HttpCommonResult executeConnectorRequest(HttpCommonRequest request) {
    return executeConnectorRequest(request, null);
  }

  public HttpCommonResult executeConnectorRequest(
      final HttpCommonRequest request, final OutboundConnectorContext context) {
    HttpClientRequest httpClientRequest = mapToHttpClientRequest(request);
    ResponseHandler responseHandler = new ResponseHandler(context, request.isStoreResponse());

    try (HttpClientResult result = HTTP_CLIENT.executeConnectorRequest(httpClientRequest)) {
      return responseHandler.handle(result);
    } catch (Exception e) {
      throw new RuntimeException("Failed to execute HTTP request", e);
    }
  }

  public HttpClientRequest mapToHttpClientRequest(HttpCommonRequest request) {
    HttpClientRequest httpClientRequest = new HttpClientRequest();
    httpClientRequest.setMethod(
        io.camunda.connector.http.client.model.HttpMethod.valueOf(request.getMethod().name()));
    httpClientRequest.setUrl(request.getUrl());
    httpClientRequest.setHeaders(request.getHeaders().orElse(null));
    httpClientRequest.setQueryParameters(request.getQueryParameters());
    httpClientRequest.setBody(request.getBody());
    httpClientRequest.setAuthentication(AuthenticationMapper.map(request.getAuthentication()));
    httpClientRequest.setConnectionTimeoutInSeconds(request.getConnectionTimeoutInSeconds());
    httpClientRequest.setReadTimeoutInSeconds(request.getReadTimeoutInSeconds());
    httpClientRequest.setSkipEncoding(request.getSkipEncoding());
    httpClientRequest.setIgnoreNullValues(request.isIgnoreNullValues());
    return httpClientRequest;
  }
}
