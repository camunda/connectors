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
package io.camunda.connector.http.client.model;

import io.camunda.connector.http.client.model.auth.HttpAuthentication;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class HttpClientRequest {

  private static final int DEFAULT_TIMEOUT = 20;

  private HttpMethod method;

  private String url;

  private HttpAuthentication authentication;

  private Integer connectionTimeoutInSeconds;

  private Integer readTimeoutInSeconds;

  private Map<String, String> headers;

  private Object body;

  private Map<String, String> queryParameters;

  private boolean storeResponse;

  private String skipEncoding;

  private boolean ignoreNullValues;

  private boolean shouldReturnRawBody;

  public Object getBody() {
    return body;
  }

  public void setBody(final Object body) {
    this.body = body;
  }

  public boolean hasHeaders() {
    return headers != null;
  }

  public boolean hasBody() {
    return body != null;
  }

  public boolean hasQueryParameters() {
    return queryParameters != null;
  }

  public Map<String, String> getQueryParameters() {
    return queryParameters;
  }

  public void setQueryParameters(Map<String, String> queryParameters) {
    this.queryParameters = queryParameters;
  }

  public boolean getSkipEncoding() {
    return Objects.equals(skipEncoding, "true");
  }

  public void setSkipEncoding(final String skipEncoding) {
    this.skipEncoding = skipEncoding;
  }

  public boolean hasAuthentication() {
    return authentication != null;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public HttpAuthentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(HttpAuthentication authentication) {
    this.authentication = authentication;
  }

  public HttpMethod getMethod() {
    return method;
  }

  public void setMethod(HttpMethod method) {
    this.method = method;
  }

  public Integer getConnectionTimeoutInSeconds() {
    return Optional.ofNullable(connectionTimeoutInSeconds).orElse(DEFAULT_TIMEOUT);
  }

  public void setConnectionTimeoutInSeconds(Integer connectionTimeoutInSeconds) {
    this.connectionTimeoutInSeconds = connectionTimeoutInSeconds;
  }

  public Integer getReadTimeoutInSeconds() {
    return Optional.ofNullable(readTimeoutInSeconds)
        .orElse(connectionTimeoutInSeconds != null ? connectionTimeoutInSeconds : DEFAULT_TIMEOUT);
  }

  public void setReadTimeoutInSeconds(final Integer readTimeoutInSeconds) {
    this.readTimeoutInSeconds = readTimeoutInSeconds;
  }

  public Optional<String> getHeader(final String key) {
    if (Objects.nonNull(headers)) {
      return headers.keySet().stream().filter(key::equalsIgnoreCase).findFirst().map(headers::get);
    }
    return Optional.empty();
  }

  public Optional<Map<String, String>> getHeaders() {
    return Optional.ofNullable(headers);
  }

  public void setHeaders(final Map<String, String> headers) {
    this.headers = headers;
  }

  public boolean isStoreResponse() {
    return storeResponse;
  }

  public void setStoreResponse(boolean storeResponse) {
    this.storeResponse = storeResponse;
  }

  public boolean shouldReturnRawBody() {
    return shouldReturnRawBody;
  }

  public void setShouldReturnRawBody(boolean shouldReturnRawBody) {
    this.shouldReturnRawBody = shouldReturnRawBody;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HttpClientRequest that = (HttpClientRequest) o;
    return url.equals(that.url)
        && method.equals(that.method)
        && Objects.equals(authentication, that.authentication)
        && Objects.equals(connectionTimeoutInSeconds, that.connectionTimeoutInSeconds)
        && Objects.equals(readTimeoutInSeconds, that.readTimeoutInSeconds)
        && Objects.equals(headers, that.headers)
        && Objects.equals(body, that.body)
        && Objects.equals(queryParameters, that.queryParameters)
        && storeResponse == that.storeResponse;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        url,
        method,
        authentication,
        connectionTimeoutInSeconds,
        readTimeoutInSeconds,
        headers,
        body,
        queryParameters,
        storeResponse);
  }

  @Override
  public String toString() {
    return "HttpRequest{"
        + "url='"
        + url
        + '\''
        + ", method='"
        + method
        + '\''
        + ", authentication="
        + authentication
        + ", connectionTimeoutInSeconds='"
        + connectionTimeoutInSeconds
        + '\''
        + ", headers="
        + headers
        + '\''
        + ", readTimeoutInSeconds='"
        + readTimeoutInSeconds
        + ", body="
        + body
        + ", queryParameters="
        + queryParameters
        + ", storeResponse="
        + storeResponse
        + '}';
  }

  public boolean isIgnoreNullValues() {
    return ignoreNullValues;
  }

  public void setIgnoreNullValues(boolean ignoreNullValues) {
    this.ignoreNullValues = ignoreNullValues;
  }
}
