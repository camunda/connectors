/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.model.MSTeamsRequest;
import io.camunda.connector.suppliers.GraphServiceClientSupplier;

@OutboundConnector(
    name = "MSTEAMS",
    inputVariables = {"authentication", "data"},
    type = "io.camunda:connector-microsoft-teams:1")
public class MSTeamsFunction implements OutboundConnectorFunction {

  private final GraphServiceClientSupplier graphSupplier;

  public MSTeamsFunction() {
    this(new GraphServiceClientSupplier());
  }

  public MSTeamsFunction(final GraphServiceClientSupplier graphSupplier) {
    this.graphSupplier = graphSupplier;
  }

  @Override
  public Object execute(OutboundConnectorContext context) throws JsonProcessingException {
    var msTeamsRequest = context.bindVariables(MSTeamsRequest.class);
    return msTeamsRequest.invoke(graphSupplier);
  }
}
