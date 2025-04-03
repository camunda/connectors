/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.textract.caller.AsyncTextractCaller;
import io.camunda.connector.textract.caller.PollingTextractCalller;
import io.camunda.connector.textract.caller.SyncTextractCaller;
import io.camunda.connector.textract.model.TextractRequest;
import io.camunda.connector.textract.suppliers.AmazonTextractClientSupplier;

@OutboundConnector(
    name = "AWS Textract",
    inputVariables = {"authentication", "configuration", "input"},
    type = "io.camunda:aws-textract:1")
@ElementTemplate(
    engineVersion = "^8.7",
    id = "io.camunda.connectors.AWSTEXTRACT.v1",
    name = "AWS Textract Outbound Connector",
    description = "Extract text and data using AWS Textract.",
    metadata =
        @ElementTemplate.Metadata(
            keywords = {
              "extract text",
              "extract data",
              "extract text from image",
              "extract data from image",
              "ocr"
            }),
    inputDataClass = TextractRequest.class,
    version = 2,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Configuration"),
      @ElementTemplate.PropertyGroup(id = "input", label = "Configure input")
    },
    documentationRef =
        "https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/amazon-textract/",
    icon = "icon.svg")
public class TextractConnectorFunction implements OutboundConnectorFunction {

  private final AmazonTextractClientSupplier clientSupplier;

  private final SyncTextractCaller syncTextractCaller;

  private final PollingTextractCalller pollingTextractCaller;

  private final AsyncTextractCaller asyncTextractCaller;

  public TextractConnectorFunction() {
    this.clientSupplier = new AmazonTextractClientSupplier();
    this.syncTextractCaller = new SyncTextractCaller();
    this.pollingTextractCaller = new PollingTextractCalller();
    this.asyncTextractCaller = new AsyncTextractCaller();
  }

  public TextractConnectorFunction(
      AmazonTextractClientSupplier clientSupplier,
      SyncTextractCaller syncTextractCaller,
      PollingTextractCalller pollingTextractCaller,
      AsyncTextractCaller asyncTextractCaller) {
    this.clientSupplier = clientSupplier;
    this.syncTextractCaller = syncTextractCaller;
    this.pollingTextractCaller = pollingTextractCaller;
    this.asyncTextractCaller = asyncTextractCaller;
  }

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    TextractRequest request = context.bindVariables(TextractRequest.class);
    return switch (request.getInput().executionType()) {
      case SYNC ->
          syncTextractCaller.call(
              request.getInput(), clientSupplier.getSyncTextractClient(request));
      case POLLING ->
          pollingTextractCaller.call(
              request.getInput(), clientSupplier.getAsyncTextractClient(request));
      case ASYNC ->
          asyncTextractCaller.call(
              request.getInput(), clientSupplier.getAsyncTextractClient(request));
    };
  }
}
