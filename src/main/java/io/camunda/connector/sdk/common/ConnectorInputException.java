package io.camunda.connector.sdk.common;

public class ConnectorInputException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ConnectorInputException(Throwable exception) {
    super(exception);
  }
}
