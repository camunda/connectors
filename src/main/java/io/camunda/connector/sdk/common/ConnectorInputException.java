package io.camunda.connector.sdk.common;

import java.io.IOException;

public class ConnectorInputException extends RuntimeException {
  public ConnectorInputException(Throwable exception) {
    super(exception);
  }
}
