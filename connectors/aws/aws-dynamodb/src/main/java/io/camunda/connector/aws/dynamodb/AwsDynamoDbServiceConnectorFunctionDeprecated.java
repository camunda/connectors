/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;

@OutboundConnector(
    name = "AWS",
    inputVariables = {"authentication", "configuration", "input"},
    type = "io.camunda:aws:1")
@Deprecated
/**
 * Deprecated implementation as the connector type identifier was changed to the new format that
 * delegates functionality to the actual implementation.
 */
public class AwsDynamoDbServiceConnectorFunctionDeprecated implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    return new AwsDynamoDbServiceConnectorFunction().execute(context);
  }
}
