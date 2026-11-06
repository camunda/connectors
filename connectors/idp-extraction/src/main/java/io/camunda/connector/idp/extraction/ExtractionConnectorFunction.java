/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction;

import static io.camunda.connector.idp.extraction.utils.ProviderUtil.*;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.idp.extraction.client.ai.base.AiClient;
import io.camunda.connector.idp.extraction.client.extraction.base.MlExtractor;
import io.camunda.connector.idp.extraction.client.extraction.base.TextExtractor;
import io.camunda.connector.idp.extraction.model.*;
import io.camunda.connector.idp.extraction.service.StructuredService;
import io.camunda.connector.idp.extraction.service.UnstructuredService;

/**
 * @deprecated Legacy IDP extraction entry point. Use {@link
 *     UnstructuredExtractionConnectorFunction}, {@link StructuredExtractionConnectorFunction}, or
 *     {@link ClassificationConnectorFunction} instead, which expose the full feature set. Retained
 *     for backwards compatibility; no removal currently planned.
 *     <p>Its element template is frozen and no longer generated from this class. The only valid
 *     versions are the snapshots in {@code element-templates/versioned/}: v1 for 8.7 and v2 for 8.8
 *     clusters. From 8.9 on, the specialized templates replace it.
 */
@Deprecated(since = "8.9")
@OutboundConnector(
    name = "IDP extraction outbound Connector",
    inputVariables = {"baseRequest", "input"},
    type = "io.camunda:idp-extraction-connector-template:1")
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
      case STRUCTURED -> {
        MlExtractor mlExtractor = getMlExtractor(extractionRequest.baseRequest());
        yield structuredService.extract(
            mlExtractor,
            extractionRequest.input().includedFields(),
            extractionRequest.input().renameMappings(),
            extractionRequest.input().delimiter(),
            extractionRequest.input().document());
      }
      case UNSTRUCTURED -> {
        TextExtractor textExtractor = getTextExtractor(extractionRequest.baseRequest());
        AiClient aiClient = getAiClient(extractionRequest);
        yield unstructuredService.extract(
            textExtractor,
            aiClient,
            extractionRequest.input().taxonomyItems(),
            extractionRequest.input().document());
      }
    };
  }
}
