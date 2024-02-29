/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.model.MSTeamsRequest;
import io.camunda.connector.operation.OperationFactory;
import io.camunda.connector.suppliers.GraphServiceClientSupplier;
import okhttp3.Request;

@OutboundConnector(
    name = "MS Teams",
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
  public Object execute(OutboundConnectorContext context) {
    var msTeamsRequest = context.bindVariables(MSTeamsRequest.class);

    GraphServiceClient<Request> graphServiceClient =
        graphSupplier.buildAndGetGraphServiceClient(msTeamsRequest.authentication());

    OperationFactory operationFactory = new OperationFactory();

    return operationFactory.getService(msTeamsRequest.data()).invoke(graphServiceClient);
  }
}
