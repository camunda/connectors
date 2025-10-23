/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.a2a.client.api.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.api.A2aClientFactory;
import io.camunda.connector.agenticai.a2a.client.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.inbound.model.A2aPollingRequest;
import io.camunda.connector.agenticai.a2a.inbound.service.A2aPollingExecutorService;
import io.camunda.connector.agenticai.a2a.inbound.task.A2aPollingProcessInstancesFetcherTask;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.generator.dsl.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;

@ElementTemplate(
    id = "io.camunda.connectors.agenticai.a2a.polling.v0",
    version = 0,
    name = "A2A Client Polling Connector (experimental)",
    description =
        "Agent-to-Agent (A2A) polling inbound connector. Supports polling asynchronous tasks, but can also directly correlate messages and synchronously completed tasks.",
    icon = "a2a-client.svg",
    engineVersion = "^8.9",
    inputDataClass = A2aPollingRequest.class,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "connection", label = "Connection"),
      @ElementTemplate.PropertyGroup(id = "clientResponse", label = "Client Response"),
      @ElementTemplate.PropertyGroup(id = "options", label = "Options", openByDefault = false),
      @ElementTemplate.PropertyGroup(id = "polling", label = "Polling", openByDefault = false),
    },
    elementTypes = {
      @ElementTemplate.ConnectorElementType(
          appliesTo = {BpmnType.INTERMEDIATE_THROW_EVENT, BpmnType.INTERMEDIATE_CATCH_EVENT},
          elementType = BpmnType.INTERMEDIATE_CATCH_EVENT,
          templateIdOverride = "io.camunda.connectors.agenticai.a2a.polling.intermediate.v0",
          templateNameOverride =
              "A2A Client Polling Intermediate Catch Event Connector (experimental)"),
      @ElementTemplate.ConnectorElementType(
          appliesTo = {BpmnType.RECEIVE_TASK},
          elementType = BpmnType.RECEIVE_TASK,
          templateIdOverride = "io.camunda.connectors.agenticai.a2a.polling.receive.v0",
          templateNameOverride = "A2A Client Polling Receive Task Connector (experimental)")
    })
@InboundConnector(name = "A2A Polling Connector", type = "io.camunda.agenticai:a2aclient:polling:0")
public class A2aPollingExecutable
    implements InboundConnectorExecutable<InboundIntermediateConnectorContext> {

  private final A2aPollingExecutorService executorService;
  private final A2aAgentCardFetcher agentCardFetcher;
  private final A2aClientFactory clientFactory;
  private final A2aSdkObjectConverter objectConverter;
  private final ObjectMapper objectMapper;

  private A2aPollingProcessInstancesFetcherTask processInstancesFetcherTask;

  public A2aPollingExecutable(
      final A2aPollingExecutorService executorService,
      final A2aAgentCardFetcher agentCardFetcher,
      final A2aClientFactory clientFactory,
      final A2aSdkObjectConverter objectConverter,
      final ObjectMapper objectMapper) {
    this.executorService = executorService;
    this.agentCardFetcher = agentCardFetcher;
    this.clientFactory = clientFactory;
    this.objectConverter = objectConverter;
    this.objectMapper = objectMapper;
  }

  @Override
  public void activate(final InboundIntermediateConnectorContext context) {
    processInstancesFetcherTask =
        new A2aPollingProcessInstancesFetcherTask(
            context,
            executorService,
            agentCardFetcher,
            clientFactory,
            objectConverter,
            objectMapper);
    processInstancesFetcherTask.start();
  }

  @Override
  public void deactivate() {
    processInstancesFetcherTask.stop();
  }
}
