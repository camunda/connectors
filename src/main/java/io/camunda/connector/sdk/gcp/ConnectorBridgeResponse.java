package io.camunda.connector.sdk.gcp;

import java.util.Objects;

public class ConnectorBridgeResponse {

  private String error;

  private Object result;

  public Object getResult() {
    return result;
  }

  public void setResult(Object result) {
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
    return Objects.equals(error, that.error);
  }

  @Override
  public int hashCode() {
    return Objects.hash(error);
  }

  @Override
  public String toString() {
    return "ConnectorBridgeResponse{" + "error='" + error + '\'' + '}';
  }

}
