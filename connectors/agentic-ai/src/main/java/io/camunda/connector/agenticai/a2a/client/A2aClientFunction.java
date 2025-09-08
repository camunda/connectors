/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client;

import io.camunda.connector.agenticai.a2a.client.model.A2aClientRequest;
import io.camunda.connector.agenticai.a2a.client.model.result.A2aClientResult;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;

@OutboundConnector(
    name = "A2A Client",
    inputVariables = {"data"},
    type = "io.camunda.agenticai:a2aclient:0")
@ElementTemplate(
    id = "io.camunda.connectors.agenticai.a2a.client.v0",
    name = "A2A Client (experimental)",
    description =
        "Agent-to-Agent (A2A) client, enabling discovering remote agents' Agent Cards as well as invoking remove agents.",
    engineVersion = "^8.9",
    version = 0,
    inputDataClass = A2aClientRequest.class,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(
          id = "connection",
          label = "HTTP Connection",
          tooltip =
              "Configure the HTTP connection to the remote A2A server for retrieving the Agent Card. Setting authentication headers is not supported yet."),
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation")
    },
    icon = "a2a-client.svg")
public class A2aClientFunction implements OutboundConnectorFunction {

  private final A2aClientRequestHandler handler;

  public A2aClientFunction(A2aClientRequestHandler handler) {
    this.handler = handler;
  }

  @Override
  public A2aClientResult execute(OutboundConnectorContext context) throws Exception {
    final A2aClientRequest request = context.bindVariables(A2aClientRequest.class);
    return handler.handle(request);
  }
}
