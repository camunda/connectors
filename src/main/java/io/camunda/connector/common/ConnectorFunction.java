package io.camunda.connector.common;

public interface ConnectorFunction {

  Object service(ConnectorContext input);
}
