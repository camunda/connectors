/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.http.model;

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
