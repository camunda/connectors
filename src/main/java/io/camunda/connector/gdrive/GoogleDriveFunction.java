/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive;

import com.google.api.client.json.JsonParser;
import com.google.api.client.json.gson.GsonFactory;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.gdrive.model.GoogleDriveResult;
import io.camunda.connector.gdrive.model.request.GoogleDriveRequest;
import io.camunda.connector.gdrive.supliers.GoogleServicesSupplier;
import io.camunda.connector.gdrive.supliers.GsonComponentSupplier;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "GOOGLEDRIVE",
    inputVariables = {"authentication", "resource"},
    type = "io.camunda:google-drive:1")
public class GoogleDriveFunction implements OutboundConnectorFunction {
  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleDriveFunction.class);

  private final GoogleDriveService service;
  private final GsonFactory gsonFactory;

  public GoogleDriveFunction() {
    this(new GoogleDriveService(), GsonComponentSupplier.gsonFactoryInstance());
  }

  public GoogleDriveFunction(final GoogleDriveService service, final GsonFactory gsonFactory) {
    this.service = service;
    this.gsonFactory = gsonFactory;
  }

  @Override
  public Object execute(final OutboundConnectorContext context) {
    final GoogleDriveRequest request = parseVariablesToRequest(context.getVariables());
    context.validate(request);
    context.replaceSecrets(request);
    LOGGER.debug("Request verified successfully and all required secrets replaced");
    return executeConnector(request);
  }

  private GoogleDriveRequest parseVariablesToRequest(final String requestAsJson) {
    try {
      JsonParser jsonParser = gsonFactory.createJsonParser(requestAsJson);
      return jsonParser.parseAndClose(GoogleDriveRequest.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private GoogleDriveResult executeConnector(final GoogleDriveRequest request) {
    LOGGER.debug("Executing my connector with request {}", request);
    GoogleDriveClient drive =
        new GoogleDriveClient(
            GoogleServicesSupplier.createDriveClientInstance(request.getAuthentication()),
            GoogleServicesSupplier.createDocsClientInstance(request.getAuthentication()));
    return service.execute(drive, request.getResource());
  }
}
