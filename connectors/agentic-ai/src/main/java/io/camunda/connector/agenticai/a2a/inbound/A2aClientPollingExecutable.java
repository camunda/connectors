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
import io.camunda.connector.agenticai.a2a.inbound.service.SharedExecutorService;
import io.camunda.connector.agenticai.a2a.inbound.task.ProcessInstancesFetcherTask;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.generator.dsl.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  private static final Logger LOG = LoggerFactory.getLogger(A2aClientPollingExecutable.class);

  private final A2aSdkClientFactory clientFactory;
  private final A2aSdkObjectConverter objectConverter;
  private ProcessInstancesFetcherTask processInstancesFetcherTask;

  public A2aClientPollingExecutable(
      final A2aSdkClientFactory clientFactory, final A2aSdkObjectConverter objectConverter) {
    this.clientFactory = clientFactory;
    this.objectConverter = objectConverter;
  }

  @Override
  public void activate(final InboundIntermediateConnectorContext context) {
    LOG.info("Activating A2A polling client");
    processInstancesFetcherTask =
        new ProcessInstancesFetcherTask(
            context, SharedExecutorService.getInstance(), clientFactory, objectConverter);
    processInstancesFetcherTask.start();
  }

  @Override
  public void deactivate() {
    LOG.info("Deactivating A2A polling client");
    processInstancesFetcherTask.stop();
  }
}
