/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.inbound;

import io.camunda.connector.agenticai.a2a.client.api.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.inbound.model.A2aPollingRequest;
import io.camunda.connector.agenticai.a2a.inbound.service.A2aTaskPollingExecutorService;
import io.camunda.connector.agenticai.a2a.inbound.task.A2aProcessInstancesFetcherTask;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.generator.dsl.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;

@ElementTemplate(
    id = "io.camunda.connectors.agenticai.a2a.polling.v0",
    version = 0,
    name = "A2A Client Polling Connector (experimental)",
    description = "Agent-to-Agent (A2A) polling client.",
    icon = "a2a-client.svg",
    engineVersion = "^8.9",
    inputDataClass = A2aPollingRequest.class,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "connection", label = "Connection"),
      @ElementTemplate.PropertyGroup(id = "task", label = "Task"),
      @ElementTemplate.PropertyGroup(id = "polling", label = "Polling"),
    },
    elementTypes = {
      @ElementTemplate.ConnectorElementType(
          appliesTo = {BpmnType.INTERMEDIATE_THROW_EVENT, BpmnType.INTERMEDIATE_CATCH_EVENT},
          elementType = BpmnType.INTERMEDIATE_CATCH_EVENT,
          templateNameOverride = "A2A Client Polling Intermediate Catch Event Connector")
    })
@InboundConnector(name = "A2A Polling Connector", type = "io.camunda.agenticai:a2aclient:polling:0")
public class A2aClientPollingExecutable
    implements InboundConnectorExecutable<InboundIntermediateConnectorContext> {

  private final A2aTaskPollingExecutorService executorService;
  private final A2aSdkClientFactory clientFactory;
  private final A2aSdkObjectConverter objectConverter;
  private A2aProcessInstancesFetcherTask processInstancesFetcherTask;

  public A2aClientPollingExecutable(
      final A2aTaskPollingExecutorService executorService,
      final A2aSdkClientFactory clientFactory,
      final A2aSdkObjectConverter objectConverter) {
    this.executorService = executorService;
    this.clientFactory = clientFactory;
    this.objectConverter = objectConverter;
  }

  @Override
  public void activate(final InboundIntermediateConnectorContext context) {
    processInstancesFetcherTask =
        new A2aProcessInstancesFetcherTask(
            context, executorService, clientFactory, objectConverter);
    processInstancesFetcherTask.start();
  }

  @Override
  public void deactivate() {
    processInstancesFetcherTask.stop();
  }
}
