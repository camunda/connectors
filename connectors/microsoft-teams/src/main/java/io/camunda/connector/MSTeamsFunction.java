/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

import com.google.gson.Gson;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.model.MSTeamsRequest;
import io.camunda.connector.suppliers.GraphServiceClientSupplier;
import io.camunda.connector.suppliers.GsonSupplier;

@OutboundConnector(
    name = "MSTEAMS",
    inputVariables = {"authentication", "data"},
    type = "io.camunda:connector-microsoft-teams:1")
public class MSTeamsFunction implements OutboundConnectorFunction {

  private final Gson gson;
  private final GraphServiceClientSupplier graphSupplier;

  public MSTeamsFunction() {
    this(new GraphServiceClientSupplier(), GsonSupplier.getGson());
  }

  public MSTeamsFunction(final GraphServiceClientSupplier graphSupplier, final Gson gson) {
    this.graphSupplier = graphSupplier;
    this.gson = gson;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {

    final var variables = context.getVariables();
    final var msTeamsRequest = gson.fromJson(variables, MSTeamsRequest.class);

    context.validate(msTeamsRequest);
    context.replaceSecrets(msTeamsRequest);

    return msTeamsRequest.invoke(graphSupplier);
  }
}
