/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.api.inbound.PollingConnectorExecutable;

@InboundConnector(name = "HTTP_POLLING", type = "io.camunda:http:polling:1")
public class PollingConnector implements PollingConnectorExecutable {

  @Override
  public void activate(final InboundConnectorContext context) {
    InboundIntermediateConnectorContext pollingContext =
        (InboundIntermediateConnectorContext) context;
    throw new UnsupportedOperationException("Connector not implemented yet");
  }

  @Override
  public void deactivate() {}
}
