package io.camunda.connector.http;

import java.util.Map;
import java.util.Objects;

public class HttpJsonResponse {
  private int status;
  private Map<String, Object> headers;
  private Object body;

  public int getStatus() {
    return status;
  }

  public void setStatus(final int status) {
    this.status = status;
  }

  public Map<String, Object> getHeaders() {
    return headers;
  }

  public void setHeaders(final Map<String, Object> headers) {
    this.headers = headers;
  }

  public Object getBody() {
    return body;
  }

  public void setBody(final Object body) {
    this.body = body;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final HttpJsonResponse that = (HttpJsonResponse) o;
    return status == that.status
        && Objects.equals(headers, that.headers)
        && Objects.equals(body, that.body);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, headers, body);
  }

  @Override
  public String toString() {
    return "HttpJsonResponse{"
        + "status="
        + status
        + ", headers="
        + headers
        + ", body="
        + body
        + '}';
  }
}
