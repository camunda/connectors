/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.eventbridge;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "AWSEventBridge",
    inputVariables = {"authentication", "configuration", "input"},
    type = "io.camunda:aws:eventbridge:1")
public class EventBridgeFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(EventBridgeFunction.class);

  public EventBridgeFunction() {}

  @Override
  public Object execute(OutboundConnectorContext context) {
    // todo
    throw new UnsupportedOperationException("EventBridge connector not implemented yet");
  }
}
