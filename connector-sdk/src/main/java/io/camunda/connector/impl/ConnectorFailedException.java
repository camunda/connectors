package io.camunda.connector.impl;

public class ConnectorFailedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ConnectorFailedException(String errorMessage) {
    super(errorMessage);
  }

  public ConnectorFailedException(String errorMessage, Throwable rootCause) {
    super(errorMessage, rootCause);
  }

  public ConnectorFailedException(Throwable exception) {
    super(exception);
  }
}
