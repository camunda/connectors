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

import com.google.common.base.Objects;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.http.auth.Authentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

public class HttpJsonRequest {

  @NotBlank @Secret private String method;

  @NotBlank
  @Pattern(regexp = "^(http://|https://|secrets).*$")
  @Secret
  private String url;

  @Valid @Secret private Authentication authentication;
  @Secret private Map<String, String> queryParameters;
  @Secret private Map<String, String> headers;
  private Object body;

  public boolean hasAuthentication() {
    return authentication != null;
  }

  public boolean hasQueryParameters() {
    return queryParameters != null;
  }

  public boolean hasHeaders() {
    return headers != null;
  }

  public boolean hasBody() {
    return body != null;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(final String method) {
    this.method = method;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(final String url) {
    this.url = url;
  }

  public Authentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(final Authentication authentication) {
    this.authentication = authentication;
  }

  public Map<String, String> getQueryParameters() {
    return queryParameters;
  }

  public void setQueryParameters(final Map<String, String> queryParameters) {
    this.queryParameters = queryParameters;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public void setHeaders(final Map<String, String> headers) {
    this.headers = headers;
  }

  public Object getBody() {
    return body;
  }

  public void setBody(final Object body) {
    this.body = body;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    HttpJsonRequest that = (HttpJsonRequest) o;
    return Objects.equal(method, that.method)
        && Objects.equal(url, that.url)
        && Objects.equal(authentication, that.authentication)
        && Objects.equal(queryParameters, that.queryParameters)
        && Objects.equal(headers, that.headers)
        && Objects.equal(body, that.body);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(method, url, authentication, queryParameters, headers, body);
  }

  @Override
  public String toString() {
    return "HttpJsonRequest{"
        + "method='"
        + method
        + '\''
        + ", url='"
        + url
        + '\''
        + ", authentication="
        + authentication
        + ", queryParameters="
        + queryParameters
        + ", headers="
        + headers
        + ", body="
        + body
        + '}';
  }
}
