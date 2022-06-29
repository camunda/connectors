package io.camunda.connector.sdk;

public class ConnectorFailedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ConnectorFailedException(String error) {
    super(error);
  }

  public ConnectorFailedException(Throwable exception) {
    super(exception);
  }
}
