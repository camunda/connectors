package io.camunda.connector.sdk.common;

public class ConnectorFailedException extends RuntimeException {

  public ConnectorFailedException(String error) {
    super(error);
  }

  public ConnectorFailedException(Throwable exception) {
    super(exception);
  }
}
