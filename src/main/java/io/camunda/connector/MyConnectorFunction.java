package io.camunda.connector;

import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.ConnectorFunction;
import io.camunda.connector.api.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyConnectorFunction implements ConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(MyConnectorFunction.class);

  @Override
  public Object execute(ConnectorContext context) throws Exception {
    var connectorRequest = context.getVariablesAsType(MyConnectorRequest.class);

    var validator = new Validator();
    connectorRequest.validate(validator);
    validator.validate();

    connectorRequest.replaceSecrets(context.getSecretStore());

    return executeConnector(connectorRequest);
  }

  private MyConnectorResult executeConnector(final MyConnectorRequest connectorRequest) {
    // TODO: implement connector logic
    LOGGER.info("Executing my connector with request {}", connectorRequest);
    var result = new MyConnectorResult();
    result.setMyProperty(connectorRequest.getMyProperty());
    return result;
  }
}
