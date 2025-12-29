/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.idp.extraction.request.classification.ClassificationRequest;
import io.camunda.connector.idp.extraction.service.ClassificationService;

@OutboundConnector(
    name = "IDP Classification Connector",
    inputVariables = {"extractor", "ai", "input"},
    type = "io.camunda:idp-classification-connector-template:1")
@ElementTemplate(
    engineVersion = "^8.9",
    id = "io.camunda.connector.IdpClassificationOutBoundTemplate.v1",
    name = "IDP classification outbound Connector",
    version = 2,
    description = "Execute IDP classification requests",
    icon = "classification-icon.svg",
    documentationRef = "https://docs.camunda.io/docs/guides/",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "input", label = "Input message data"),
      @ElementTemplate.PropertyGroup(id = "extractor", label = "Extractor selection"),
      @ElementTemplate.PropertyGroup(id = "ai", label = "Ai provider selection")
    },
    inputDataClass = ClassificationRequest.class)
public class ClassificationConnectorFunction implements OutboundConnectorFunction {

  private final ClassificationService classificationService = new ClassificationService();

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    ClassificationRequest request = context.bindVariables(ClassificationRequest.class);
    return classificationService.execute(request);
  }
}
