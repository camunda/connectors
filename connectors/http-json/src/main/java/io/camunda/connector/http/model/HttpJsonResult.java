package io.camunda.connector.http.model;

import com.google.common.base.Objects;
import java.util.Map;

public class HttpJsonResult {
  private int status;
  private Map<String, Object> headers;
  private Object body;

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public Map<String, Object> getHeaders() {
    return headers;
  }

  public void setHeaders(Map<String, Object> headers) {
    this.headers = headers;
  }

  public Object getBody() {
    return body;
  }

  public void setBody(Object body) {
    this.body = body;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HttpJsonResult that = (HttpJsonResult) o;
    return status == that.status
        && Objects.equal(headers, that.headers)
        && Objects.equal(body, that.body);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(status, headers, body);
  }

  @Override
  public String toString() {
    return "HttpJsonResult{" + "status=" + status + ", headers=" + headers + ", body=" + body + '}';
  }
}
