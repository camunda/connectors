package io.camunda.connector.http;

import java.util.Map;
import java.util.Objects;

public class ResponseWrapper {

  private HttpJsonResult response;

  // deprecated but kept for backwards compatibility with first pre-releases
  private int status;
  private Map<String, Object> headers;
  private Object body;

  public ResponseWrapper(final HttpJsonResult result) {
    this.response = result;

    this.status = result.getStatus();
    this.headers = result.getHeaders();
    this.body = result.getBody();
  }

  public HttpJsonResult getResponse() {
    return response;
  }

  public void setResponse(final HttpJsonResult response) {
    this.response = response;
  }

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
    final ResponseWrapper that = (ResponseWrapper) o;
    return status == that.status
        && Objects.equals(response, that.response)
        && Objects.equals(headers, that.headers)
        && Objects.equals(body, that.body);
  }

  @Override
  public int hashCode() {
    return Objects.hash(response, status, headers, body);
  }

  @Override
  public String toString() {
    return "ResponseWrapper{"
        + "response="
        + response
        + ", status="
        + status
        + ", headers="
        + headers
        + ", body="
        + body
        + '}';
  }
}
