/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction;

import static io.camunda.connector.idp.extraction.utils.ProviderUtil.getMlExtractor;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.idp.extraction.client.extraction.base.MlExtractor;
import io.camunda.connector.idp.extraction.request.structured.StructuredExtractionRequest;
import io.camunda.connector.idp.extraction.service.StructuredService;

@OutboundConnector(
    name = "IDP Structured Extraction Connector",
    inputVariables = {"extractor", "input"},
    type = "io.camunda:idp-structured-connector-template:1")
@ElementTemplate(
    engineVersion = "^8.9",
    id = "io.camunda.connector.IdpStructuredExtractionOutBoundTemplate.v1",
    name = "IDP Structured Extraction outbound Connector",
    version = 2,
    description = "Execute IDP Structured Extraction requests",
    icon = "structured-icon.svg",
    documentationRef = "https://docs.camunda.io/docs/guides/",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "input", label = "Input message data"),
      @ElementTemplate.PropertyGroup(id = "extractor", label = "Extractor selection")
    },
    inputDataClass = StructuredExtractionRequest.class)
public class StructuredExtractionConnectorFunction implements OutboundConnectorFunction {

  private final StructuredService structuredService = new StructuredService();

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    StructuredExtractionRequest request = context.bindVariables(StructuredExtractionRequest.class);
    MlExtractor mlExtractor = getMlExtractor(request.extractor());

    return structuredService.extract(
        mlExtractor,
        request.input().getIncludedFields(),
        request.input().getRenameMappings(),
        request.input().getDelimiter(),
        request.input().getDocument());
  }
}
