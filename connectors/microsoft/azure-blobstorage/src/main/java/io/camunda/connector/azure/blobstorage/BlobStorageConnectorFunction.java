/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.blobstorage;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.azure.blobstorage.model.core.BlobStorageExecutor;
import io.camunda.connector.azure.blobstorage.model.request.BlobStorageRequest;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.document.Document;
import io.camunda.document.store.DocumentCreationRequest;
import java.util.function.Function;

@OutboundConnector(
    name = "Azure Blob Storage",
    inputVariables = {"authentication", "actionDiscriminator", "action"},
    type = "io.camunda:azure-blobstorage:1")
@ElementTemplate(
    engineVersion = "^8.8",
    id = "io.camunda.connectors.azure.blobstorage.v1",
    name = "Azure Blob Storage Outbound Connector",
    description = "Execute azure blob storage requests",
    inputDataClass = BlobStorageRequest.class,
    version = 1,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "action", label = "Action"),
      @ElementTemplate.PropertyGroup(id = "uploadObject", label = "Upload an object"),
      @ElementTemplate.PropertyGroup(id = "downloadObject", label = "Download an object"),
    },
    documentationRef =
        "https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/azure-blobstorage/",
    icon = "icon.svg")
public class BlobStorageConnectorFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) {
    Function<DocumentCreationRequest, Document> createDocument = context::create;
    BlobStorageRequest blobStorageRequest = context.bindVariables(BlobStorageRequest.class);
    return BlobStorageExecutor.create(blobStorageRequest, createDocument)
        .execute(blobStorageRequest.getAction());
  }
}
