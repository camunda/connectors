/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.comprehend.model.ComprehendRequest;
import io.camunda.connector.generator.java.annotation.ElementTemplate;

@OutboundConnector(
    name = "AWS Comprehend",
    inputVariables = {"authentication", "configuration", "input"},
    type = "io.camunda:aws-comprehend:1")
@ElementTemplate(
    id = "io.camunda.connectors.AWSCOMPREHEND.v1",
    name = "AWS Comprehend Outbound Connector",
    description = "Execute Comprehend models",
    inputDataClass = ComprehendRequest.class,
    version = 1,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Configuration"),
      @ElementTemplate.PropertyGroup(id = "input", label = "Configure input")
    },
    documentationRef =
        "https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/amazon-comprehend/",
    icon = "icon.svg")
public class ComprehendConnectorFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    return null;
  }
}
