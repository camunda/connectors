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
package io.camunda.connector.common.model;

import io.camunda.connector.common.auth.Authentication;
import java.util.Map;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class CommonRequest {

  @NotBlank
  @Pattern(regexp = "^(http://|https://|secrets|\\{\\{).*$")
  private String url;

  @NotBlank private String method;

  @Valid private Authentication authentication;

  @Pattern(regexp = "^([0-9]+|secrets\\..+)$")
  private String connectionTimeoutInSeconds;

  private Map<String, String> headers;

  private Object body;

  private Map<String, String> queryParameters;

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

  public Map<String, String> getHeaders() {
    return headers;
  }

  public void setHeaders(final Map<String, String> headers) {
    this.headers = headers;
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

  public boolean hasAuthentication() {
    return authentication != null;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public Authentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(Authentication authentication) {
    this.authentication = authentication;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public String getConnectionTimeoutInSeconds() {
    return connectionTimeoutInSeconds;
  }

  public void setConnectionTimeoutInSeconds(String connectionTimeoutInSeconds) {
    this.connectionTimeoutInSeconds = connectionTimeoutInSeconds;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CommonRequest that = (CommonRequest) o;
    return url.equals(that.url)
        && method.equals(that.method)
        && Objects.equals(authentication, that.authentication)
        && Objects.equals(connectionTimeoutInSeconds, that.connectionTimeoutInSeconds)
        && Objects.equals(headers, that.headers)
        && Objects.equals(body, that.body)
        && Objects.equals(queryParameters, that.queryParameters);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        url, method, authentication, connectionTimeoutInSeconds, headers, body, queryParameters);
  }

  @Override
  public String toString() {
    return "CommonRequest{"
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
        + ", body="
        + body
        + ", queryParameters="
        + queryParameters
        + '}';
  }
}
