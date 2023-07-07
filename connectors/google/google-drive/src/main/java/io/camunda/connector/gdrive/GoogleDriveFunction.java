/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.gdrive.model.GoogleDriveResult;
import io.camunda.connector.gdrive.model.request.GoogleDriveRequest;
import io.camunda.connector.gdrive.supliers.GoogleDocsServiceSupplier;
import io.camunda.google.supplier.GoogleDriveServiceSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "GOOGLEDRIVE",
    inputVariables = {"authentication", "resource"},
    type = "io.camunda:google-drive:1")
public class GoogleDriveFunction implements OutboundConnectorFunction {
  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleDriveFunction.class);

  private final GoogleDriveService service;

  public GoogleDriveFunction() {
    this(new GoogleDriveService());
  }

  public GoogleDriveFunction(final GoogleDriveService service) {
    this.service = service;
  }

  @Override
  public Object execute(final OutboundConnectorContext context) {
    var request = context.bindVariables(GoogleDriveRequest.class);
    return executeConnector(request);
  }

  private GoogleDriveResult executeConnector(final GoogleDriveRequest request) {
    LOGGER.debug("Executing my connector with request {}", request);
    GoogleDriveClient drive =
        new GoogleDriveClient(
            GoogleDriveServiceSupplier.createDriveClientInstance(request.getAuthentication()),
            GoogleDocsServiceSupplier.createDocsClientInstance(request.getAuthentication()));
    return service.execute(drive, request.getResource());
  }
}
