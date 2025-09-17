/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.auth.AuthenticationMapper;
import io.camunda.connector.http.client.HttpClientService;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.HttpClientResult;
import java.util.Optional;

public class HttpService {

  private final HttpClientService httpClientService;
  private final FileResponseHandler fileResponseHandler;

  public HttpService() {
    httpClientService = new HttpClientService();
  }

  public HttpCommonResult executeConnectorRequest(HttpCommonRequest request) {
    return executeConnectorRequest(request, null);
  }

  public HttpCommonResult executeConnectorRequest(
      final HttpCommonRequest request, final OutboundConnectorContext context) {
    HttpClientRequest httpClientRequest = mapToHttpClientRequest(request);
    HttpClientResult result = httpClientService.executeConnectorRequest(httpClientRequest, context);
    if (request.isStoreResponse() && result.body() != null) {
      return fileResponseHandler.handle(result.headers(), result.body());
    }
    return mapToHttpCommonResult(result);
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
    httpClientRequest.setStoreResponse(request.isStoreResponse());
    httpClientRequest.setConnectionTimeoutInSeconds(request.getConnectionTimeoutInSeconds());
    httpClientRequest.setReadTimeoutInSeconds(request.getReadTimeoutInSeconds());
    httpClientRequest.setSkipEncoding(request.getSkipEncoding());
    httpClientRequest.setIgnoreNullValues(request.isIgnoreNullValues());
    return httpClientRequest;
  }

  public HttpCommonResult mapToHttpCommonResult(HttpClientResult result) {

    return new HttpCommonResult(
        result.status(), result.headers(), result.body(), result.reason(), result.document());
  }
}
