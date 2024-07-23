/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.textract;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.textract.model.TextractRequest;

@OutboundConnector(
    name = "AWS Textract",
    inputVariables = {"authentication", "configuration", "input"},
    type = "io.camunda:aws-textract:1")
@ElementTemplate(
    id = "io.camunda.connectors.AWSTEXTRACT.v1",
    name = "AWS Textract Outbound Connector",
    description =
        "Automatically extract printed text, handwriting, layout elements, and data from any document",
    inputDataClass = TextractRequest.class,
    version = 1,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Configuration"),
      @ElementTemplate.PropertyGroup(id = "input", label = "Configure input")
    },
    documentationRef =
        "https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/amazon-textract/",
    icon = "icon.svg")
public class TextractConnectorFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    return null;
  }
}
