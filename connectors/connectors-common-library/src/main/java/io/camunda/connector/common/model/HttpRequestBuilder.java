/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.common.model;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class HttpRequestBuilder {

  private String method;
  private GenericUrl genericUrl;
  private HttpHeaders headers;
  private HttpContent content;
  private String connectionTimeoutInSeconds;
  private boolean followRedirects;

  public HttpRequestBuilder method(String method) {
    this.method = method;
    return this;
  }

  public HttpRequestBuilder genericUrl(GenericUrl genericUrl) {
    this.genericUrl = genericUrl;
    return this;
  }

  public HttpRequestBuilder headers(HttpHeaders headers) {
    this.headers = headers;
    return this;
  }

  public HttpRequestBuilder content(HttpContent content) {
    this.content = content;
    return this;
  }

  public HttpRequestBuilder connectionTimeoutInSeconds(String connectionTimeoutInSeconds) {
    this.connectionTimeoutInSeconds = connectionTimeoutInSeconds;
    return this;
  }

  public HttpRequestBuilder followRedirects(boolean followRedirects) {
    this.followRedirects = followRedirects;
    return this;
  }

  public HttpRequest build(final HttpRequestFactory requestFactory) throws IOException {

    final var httpRequest = requestFactory.buildRequest(method, genericUrl, content);
    httpRequest.setFollowRedirects(this.followRedirects);
    if (headers != null) {
      httpRequest.setHeaders(headers);
    }
    if (connectionTimeoutInSeconds != null) {
      long connectionTimeout =
          TimeUnit.SECONDS.toMillis(Long.parseLong(connectionTimeoutInSeconds));
      int intConnectionTimeout = Math.toIntExact(connectionTimeout);
      httpRequest.setConnectTimeout(intConnectionTimeout);
      httpRequest.setReadTimeout(intConnectionTimeout);
      httpRequest.setWriteTimeout(intConnectionTimeout);
    }
    return httpRequest;
  }
}
