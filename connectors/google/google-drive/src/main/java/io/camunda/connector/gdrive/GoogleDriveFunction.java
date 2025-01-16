/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive;

import com.google.api.services.drive.model.File;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.gdrive.model.request.GoogleDriveRequest;
import io.camunda.connector.gdrive.supliers.GoogleDocsServiceSupplier;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.document.Document;
import io.camunda.document.store.DocumentCreationRequest;
import io.camunda.google.supplier.GoogleDriveServiceSupplier;
import java.io.ByteArrayOutputStream;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ElementTemplate(
    id = "io.camunda.connectors.GoogleDrive.v1",
    name = "Google Drive Outbound Connector",
    description = "Manage Google Drive files and folders",
    metadata =
        @ElementTemplate.Metadata(
            keywords = {"create file", "create file from template", "create folder"}),
    inputDataClass = GoogleDriveRequest.class,
    version = 3,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "operation", label = "Select operation"),
      @ElementTemplate.PropertyGroup(id = "operationDetails", label = "Operation details"),
    },
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/googledrive/",
    icon = "icon.svg")
@OutboundConnector(
    name = "Google Docs",
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

    var result = executeConnector(request);

    if (result instanceof Pair) {
      return prepareDocument(context, (Pair<File, ByteArrayOutputStream>) result);
    }

    return result;
  }

  private Object executeConnector(final GoogleDriveRequest request) {
    LOGGER.debug("Executing my connector with request {}", request);

    GoogleDriveClient drive =
        new GoogleDriveClient(
            GoogleDriveServiceSupplier.createDriveClientInstance(request.getAuthentication()),
            GoogleDocsServiceSupplier.createDocsClientInstance(request.getAuthentication()));

    return service.execute(drive, request.getResource());
  }

  private Document prepareDocument(
      final OutboundConnectorContext context, final Pair<File, ByteArrayOutputStream> pair) {
    byte[] bytes = pair.getRight().toByteArray();
    var metaData = pair.getLeft();
    return context.createDocument(
        DocumentCreationRequest.from(bytes)
            .contentType(metaData.getMimeType())
            .fileName(metaData.getName())
            .build());
  }
}
