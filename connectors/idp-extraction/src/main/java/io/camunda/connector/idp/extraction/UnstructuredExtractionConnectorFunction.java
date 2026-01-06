/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction;

import static io.camunda.connector.idp.extraction.utils.ProviderUtil.getAiClient;
import static io.camunda.connector.idp.extraction.utils.ProviderUtil.getTextExtractor;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.idp.extraction.client.ai.base.AiClient;
import io.camunda.connector.idp.extraction.client.extraction.base.TextExtractor;
import io.camunda.connector.idp.extraction.request.unstructured.UnstructuredExtractionRequest;
import io.camunda.connector.idp.extraction.service.UnstructuredService;

@OutboundConnector(
    name = "IDP Unstructured Extraction Connector",
    inputVariables = {"extractor", "ai", "input"},
    type = "io.camunda:idp-unstructured-connector-template:1")
@ElementTemplate(
    engineVersion = "^8.9",
    id = "io.camunda.connector.IdpUnstructuredExtractionOutBoundTemplate.v1",
    name = "IDP Unstructured Extraction Outbound Connector",
    version = 2,
    description = "Execute IDP Unstructured Extraction requests",
    icon = "unstructured-icon.svg",
    documentationRef = "https://docs.camunda.io/docs/guides/",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "input", label = "Input message data"),
      @ElementTemplate.PropertyGroup(id = "extractor", label = "Extractor selection"),
      @ElementTemplate.PropertyGroup(id = "ai", label = "Ai provider selection")
    },
    inputDataClass = UnstructuredExtractionRequest.class)
public class UnstructuredExtractionConnectorFunction implements OutboundConnectorFunction {

  private final UnstructuredService unstructuredService = new UnstructuredService();

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    UnstructuredExtractionRequest request =
        context.bindVariables(UnstructuredExtractionRequest.class);
    TextExtractor textExtractor = getTextExtractor(request.extractor());
    AiClient aiClient = getAiClient(request.ai(), request.input().getConverseData());

    return unstructuredService.extract(
        textExtractor, aiClient, request.input().getTaxonomyItems(), request.input().getDocument());
  }
}
