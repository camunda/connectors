package io.camunda.connector.api;

public interface ConnectorFunction {

  Object execute(ConnectorContext context) throws Exception;
}
