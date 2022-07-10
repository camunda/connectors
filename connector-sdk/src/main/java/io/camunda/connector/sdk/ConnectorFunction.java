package io.camunda.connector.sdk;

public interface ConnectorFunction {

  Object execute(ConnectorContext context) throws Exception;
}
