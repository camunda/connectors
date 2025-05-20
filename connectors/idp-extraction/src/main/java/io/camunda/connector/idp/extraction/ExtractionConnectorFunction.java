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
import io.camunda.connector.idp.extraction.model.*;
import io.camunda.connector.idp.extraction.service.StructuredService;
import io.camunda.connector.idp.extraction.service.UnstructuredService;

@OutboundConnector(
    name = "IDP extraction outbound Connector",
    inputVariables = {"baseRequest", "input"},
    type = "io.camunda:idp-extraction-connector-template:1")
@ElementTemplate(
    engineVersion = "^8.7",
    id = "io.camunda.connector.IdpExtractionOutBoundTemplate.v1",
    name = "IDP extraction outbound Connector",
    version = 2,
    description = "Execute IDP extraction requests",
    icon = "icon.svg",
    documentationRef = "https://docs.camunda.io/docs/guides/",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "input", label = "Input message data"),
      @ElementTemplate.PropertyGroup(id = "provider", label = "Provider selection"),
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Provider authentication"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Provider configuration"),
    },
    inputDataClass = ExtractionRequest.class)
public class ExtractionConnectorFunction implements OutboundConnectorFunction {

  private final UnstructuredService unstructuredService;
  private final StructuredService structuredService;

  public ExtractionConnectorFunction() {
    this.unstructuredService = new UnstructuredService();
    this.structuredService = new StructuredService();
  }

  public ExtractionConnectorFunction(
      UnstructuredService unstructuredService, StructuredService structuredService) {
    this.unstructuredService = unstructuredService;
    this.structuredService = structuredService;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    final var extractionRequest = context.bindVariables(ExtractionRequest.class);
    return switch (extractionRequest.input().extractionType()) {
      case STRUCTURED -> structuredService.extract(extractionRequest);
      case UNSTRUCTURED -> unstructuredService.extract(extractionRequest);
    };
  }
}
