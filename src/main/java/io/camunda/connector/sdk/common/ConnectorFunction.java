package io.camunda.connector.sdk.common;

public interface ConnectorFunction {

  Object service(ConnectorContext input);
}
