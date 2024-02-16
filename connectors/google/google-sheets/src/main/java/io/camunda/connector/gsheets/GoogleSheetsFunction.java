/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.gsheets.model.request.GoogleSheetsRequest;
import io.camunda.connector.gsheets.operation.GoogleSheetOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "Google Spreadsheets",
    inputVariables = {"authentication", "operation", "operationDetails"},
    type = "io.camunda:google-sheets:1")
@ElementTemplate(
    id = "io.camunda.connectors.GoogleSheets.v1",
    name = "Google Sheets Outbound Connector",
    description = "Work with spreadsheets",
    inputDataClass = GoogleSheetsRequest.class,
    version = 2,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "operation", label = "Select operation"),
      @ElementTemplate.PropertyGroup(id = "operationDetails", label = "Operation details"),
    },
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/google-sheets/",
    icon = "icon.svg")
public class GoogleSheetsFunction implements OutboundConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleSheetsFunction.class);

  private final GoogleSheetsOperationFactory operationFactory;

  public GoogleSheetsFunction() {
    operationFactory = GoogleSheetsOperationFactory.getInstance();
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    var request = context.bindVariables(GoogleSheetsRequest.class);
    LOGGER.debug("Request verified successfully and all required secrets replaced");
    GoogleSheetOperation operation = operationFactory.createOperation(request.getOperation());
    return operation.execute(request.getAuthentication());
  }
}
