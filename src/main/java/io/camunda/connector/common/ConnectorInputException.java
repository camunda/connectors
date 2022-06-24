package io.camunda.connector.common;

import java.io.IOException;

public class ConnectorInputException extends RuntimeException {
  public ConnectorInputException(Throwable exception) {
    super(exception);
  }
}
