package io.camunda.connector.common;

import java.io.IOException;

public class ConnectorResponse {

  public static Object empty() {
    return new Object();
  }

  public static ConnectorFailedException failed(String error) {
    return new ConnectorFailedException(error);
  }

  public static ConnectorFailedException failed(Throwable exception) {
    return new ConnectorFailedException(exception);
  }
}
