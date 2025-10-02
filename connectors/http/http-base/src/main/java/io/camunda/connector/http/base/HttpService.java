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
import io.camunda.connector.http.client.client.HttpClient;
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import io.camunda.connector.http.client.model.HttpClientRequest;

public class HttpService {

  private final static HttpClient HTTP_CLIENT = new CustomApacheHttpClient();

  public HttpCommonResult executeConnectorRequest(HttpCommonRequest request) {
    return executeConnectorRequest(request, null);
  }

  public HttpCommonResult executeConnectorRequest(
      final HttpCommonRequest request, final OutboundConnectorContext context) {
    HttpClientRequest httpClientRequest = mapToHttpClientRequest(request);
    HttpCommonResultMapper responseHandler = new HttpCommonResultMapper(context, request.isStoreResponse());
    return HTTP_CLIENT.execute(httpClientRequest, responseHandler);
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
