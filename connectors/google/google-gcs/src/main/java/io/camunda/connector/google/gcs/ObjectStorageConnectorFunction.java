/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.google.gcs;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.google.gcs.model.core.ObjectStorageExecutor;
import io.camunda.connector.google.gcs.model.request.ObjectStorageRequest;
import io.camunda.document.Document;
import io.camunda.document.store.DocumentCreationRequest;
import java.util.function.Function;

@OutboundConnector(
    name = "Google Cloud Storage",
    inputVariables = {
      "authentication",
      "operationDiscriminator",
      "operation",
      "additionalProperties"
    },
    type = "io.camunda:google-gcs:1")
@ElementTemplate(
    engineVersion = "^8.8",
    id = "io.camunda.connectors.google.gcp.v1",
    name = "Google Cloud Storage Outbound Connector",
    description = "Upload and download files from Google Cloud Storage.",
    inputDataClass = ObjectStorageRequest.class,
    version = 1,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation"),
      @ElementTemplate.PropertyGroup(id = "additionalProperties", label = "Additional properties")
    },
    metadata =
        @ElementTemplate.Metadata(
            keywords = {
              "download file from google cloud storage",
              "upload file to google cloud storage",
              "download file from gcs",
              "upload file to gcs",
              "gcs"
            }),
    documentationRef =
        "https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/google-cloud-storage",
    icon = "icon.svg")
public class ObjectStorageConnectorFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) {
    Function<DocumentCreationRequest, Document> createDocument = context::create;
    ObjectStorageRequest objectStorageRequest = context.bindVariables(ObjectStorageRequest.class);
    return ObjectStorageExecutor.create(objectStorageRequest, createDocument)
        .execute(objectStorageRequest.getOperation());
  }
}
