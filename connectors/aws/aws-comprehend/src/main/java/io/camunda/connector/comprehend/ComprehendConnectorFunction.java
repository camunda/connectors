/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.comprehend.caller.AsyncComprehendCaller;
import io.camunda.connector.comprehend.caller.SyncComprehendCaller;
import io.camunda.connector.comprehend.model.ComprehendAsyncRequestData;
import io.camunda.connector.comprehend.model.ComprehendRequest;
import io.camunda.connector.comprehend.model.ComprehendRequestData;
import io.camunda.connector.comprehend.model.ComprehendSyncRequestData;
import io.camunda.connector.comprehend.supplier.ComprehendClientSupplier;
import io.camunda.connector.generator.java.annotation.ElementTemplate;

@OutboundConnector(
    name = "AWS Comprehend",
    inputVariables = {"authentication", "configuration", "input"},
    type = "io.camunda:aws-comprehend:1")
@ElementTemplate(
    engineVersion = "^8.7",
    id = "io.camunda.connectors.AWSCOMPREHEND.v1",
    name = "AWS Comprehend Outbound Connector",
    description = "Execute Comprehend models",
    inputDataClass = ComprehendRequest.class,
    version = 2,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Configuration"),
      @ElementTemplate.PropertyGroup(id = "input", label = "Data Configuration and Processing")
    },
    documentationRef =
        "https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/amazon-comprehend/",
    icon = "icon.svg")
public class ComprehendConnectorFunction implements OutboundConnectorFunction {

  private final ComprehendClientSupplier clientSupplier;

  private final SyncComprehendCaller syncComprehendCaller;

  private final AsyncComprehendCaller asyncComprehendCaller;

  public ComprehendConnectorFunction(
      ComprehendClientSupplier clientSupplier,
      SyncComprehendCaller syncComprehendCaller,
      AsyncComprehendCaller asyncComprehendCaller) {
    this.clientSupplier = clientSupplier;
    this.syncComprehendCaller = syncComprehendCaller;
    this.asyncComprehendCaller = asyncComprehendCaller;
  }

  public ComprehendConnectorFunction() {
    this.clientSupplier = new ComprehendClientSupplier();
    this.syncComprehendCaller = new SyncComprehendCaller();
    this.asyncComprehendCaller = new AsyncComprehendCaller();
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    var request = context.bindVariables(ComprehendRequest.class);
    ComprehendRequestData requestData = request.getInput();
    if (requestData instanceof ComprehendSyncRequestData syncRequestData) {
      return syncComprehendCaller.call(clientSupplier.getSyncClient(request), syncRequestData);
    }
    return asyncComprehendCaller.call(
        clientSupplier.getAsyncClient(request), (ComprehendAsyncRequestData) requestData);
  }
}
