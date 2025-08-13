/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.s3;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.s3.core.S3Executor;
import io.camunda.connector.aws.s3.model.request.S3Request;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import java.util.function.Function;

@OutboundConnector(
    name = "AWS S3",
    inputVariables = {"authentication", "configuration", "actionDiscriminator", "action"},
    type = "io.camunda:aws-s3:1")
@ElementTemplate(
    engineVersion = "^8.7",
    id = "io.camunda.connectors.aws.s3.v1",
    name = "AWS S3 Outbound Connector",
    description = "Execute S3 requests",
    inputDataClass = S3Request.class,
    version = 2,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Configuration"),
      @ElementTemplate.PropertyGroup(id = "action", label = "Action"),
      @ElementTemplate.PropertyGroup(id = "deleteObject", label = "Delete an object"),
      @ElementTemplate.PropertyGroup(id = "uploadObject", label = "Upload an object"),
      @ElementTemplate.PropertyGroup(id = "downloadObject", label = "Download an object"),
    },
    documentationRef =
        "https://docs.camunda.io/docs/8.7/components/connectors/out-of-the-box-connectors/amazon-s3/",
    icon = "icon.svg")
public class S3ConnectorFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) {
    Function<DocumentCreationRequest, Document> createDocument = context::create;
    S3Request s3Request = context.bindVariables(S3Request.class);
    return S3Executor.create(s3Request, createDocument).execute(s3Request.getAction());
  }
}
