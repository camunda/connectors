package io.camunda.connector.http;

import java.util.Objects;

public class ConnectorBridgeResponse {

  private HttpJsonResult result;
  private String error;

  public HttpJsonResult getResult() {
    return result;
  }

  public void setResult(final HttpJsonResult result) {
    this.result = result;
  }

  public String getError() {
    return error;
  }

  public void setError(final String error) {
    this.error = error;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ConnectorBridgeResponse that = (ConnectorBridgeResponse) o;
    return Objects.equals(result, that.result) && Objects.equals(error, that.error);
  }

  @Override
  public int hashCode() {
    return Objects.hash(result, error);
  }

  @Override
  public String toString() {
    return "ConnectorBridgeResponse{" + "result=" + result + ", error='" + error + '\'' + '}';
  }
}
