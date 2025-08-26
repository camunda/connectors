/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.box.model.BoxRequest;
import io.camunda.connector.generator.java.annotation.ElementTemplate;

@OutboundConnector(
    name = "Box",
    inputVariables = {"authentication", "operation"},
    type = "io.camunda:box:1")
@ElementTemplate(
    engineVersion = "^8.7",
    id = "io.camunda.connectors.box",
    name = "Box Outbound Connector",
    description = "Interact with the Box Document API",
    inputDataClass = BoxRequest.class,
    version = 2,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation"),
    },
    documentationRef =
        "https://docs.camunda.io/docs/8.7/components/connectors/out-of-the-box-connectors/box/",
    icon = "icon.svg")
public class BoxFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) {
    var request = context.bindVariables(BoxRequest.class);
    return BoxOperations.execute(request, context);
  }
}
