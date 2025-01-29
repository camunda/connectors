/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *             under one or more contributor license agreements. Licensed under a proprietary license.
 *             See the License.txt file for more information. You may not use this file
 *             except in compliance with the proprietary license.
 */
package io.camunda.connector.javascript;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.javascript.model.JavascriptInputRequest;

@OutboundConnector(
    name = "Javascript Connector",
    inputVariables = {"script", "parameters"},
    type = "io.camunda:javascript:1")
@ElementTemplate(
    id = "io.camunda.connectors.javascript.v1",
    name = "Javascript Connector",
    description = "Execute custom Javascript connectors",
    inputDataClass = JavascriptInputRequest.class,
    version = 1,
    propertyGroups = {@ElementTemplate.PropertyGroup(id = "javascript", label = "JavaScript")},
    documentationRef = "",
    icon = "icon.svg")
public class JavascriptConnectorFunction implements OutboundConnectorFunction {
  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    System.out.println("Executing Javascript connector");
    return null;
  }
}
