package io.camunda.connector.http;

import io.camunda.connector.sdk.common.SecretStore;

import java.util.Map;
import java.util.Objects;

public class HttpJsonRequest {

  private String method;
  private String url;
  private Authentication authentication;
  private Map<String, String> queryParameters;
  private Map<String, String> headers;
  private Object body;

  public void validate(final Validator validator) {
    validator.require(method, "HTTP Endpoint - Method");
    validator.require(url, "HTTP Endpoint - URL");
    if (hasAuthentication()) {
      authentication.validate(validator);
    }
  }

  public void replaceSecrets(final SecretStore secretStore) {
    method = secretStore.replaceSecret(method);
    url = secretStore.replaceSecret(url);
    if (hasAuthentication()) {
      authentication.replaceSecrets(secretStore);
    }
    if (hasQueryParameters()) {
      queryParameters.replaceAll((k, v) -> secretStore.replaceSecret(v));
    }
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

  public boolean hasAuthentication() {
    return authentication != null;
  }

  public Map<String, String> getQueryParameters() {
    return queryParameters;
  }

  public void setQueryParameters(final Map<String, String> queryParameters) {
    this.queryParameters = queryParameters;
  }

  public boolean hasQueryParameters() {
    return queryParameters != null;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public void setHeaders(final Map<String, String> headers) {
    this.headers = headers;
  }

  public boolean hasHeaders() {
    return headers != null;
  }

  public Object getBody() {
    return body;
  }

  public void setBody(final Object body) {
    this.body = body;
  }

  public boolean hasBody() {
    return body != null;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final HttpJsonRequest that = (HttpJsonRequest) o;
    return Objects.equals(method, that.method)
        && Objects.equals(url, that.url)
        && Objects.equals(authentication, that.authentication)
        && Objects.equals(queryParameters, that.queryParameters)
        && Objects.equals(headers, that.headers)
        && Objects.equals(body, that.body);
  }

  @Override
  public int hashCode() {
    return Objects.hash(method, url, authentication, queryParameters, headers, body);
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
