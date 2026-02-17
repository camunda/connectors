/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.generator.java.annotation.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.http.base.HttpService;
import io.camunda.connector.http.polling.model.PollingRequest;
import io.camunda.connector.http.polling.service.SharedExecutorService;
import io.camunda.connector.http.polling.task.ProcessInstancesFetcherTask;

@ElementTemplate(
    engineVersion = "^8.8",
    id = "io.camunda:http-polling:1",
    name = "Polling Connector",
    icon = "icon.svg",
    version = 4,
    inputDataClass = PollingRequest.class,
    description = "Polls endpoint at regular intervals",
    documentationRef = "https://docs.camunda.io/docs/components/connectors/protocol/polling/",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "endpoint", label = "HTTP Polling configuration"),
      @ElementTemplate.PropertyGroup(id = "payload", label = "Payload"),
      @ElementTemplate.PropertyGroup(id = "interval", label = "HTTP Polling Interval"),
      @ElementTemplate.PropertyGroup(id = "timeout", label = "Connection timeout"),
    },
    elementTypes = {
      @ElementTemplate.ConnectorElementType(
          appliesTo = {BpmnType.INTERMEDIATE_THROW_EVENT, BpmnType.INTERMEDIATE_CATCH_EVENT},
          elementType = BpmnType.INTERMEDIATE_CATCH_EVENT,
          templateIdOverride = "io.camunda.connectors.http.Polling",
          templateNameOverride = "HTTP Polling Intermediate Catch Event Connector"),
      @ElementTemplate.ConnectorElementType(
          appliesTo = BpmnType.BOUNDARY_EVENT,
          elementType = BpmnType.BOUNDARY_EVENT,
          templateIdOverride = "io.camunda.connectors.http.Polling.Boundary",
          templateNameOverride = "HTTP Polling Boundary Catch Event Connector")
    })
@InboundConnector(name = "HTTP Polling Connector", type = "io.camunda:http-polling:1")
public class HttpPollingConnector
    implements InboundConnectorExecutable<InboundIntermediateConnectorContext> {

  private final HttpService httpService;
  private final SharedExecutorService executorService;

  private ProcessInstancesFetcherTask processInstancesFetcherTask;

  public HttpPollingConnector() {
    this(new HttpService(), SharedExecutorService.getInstance());
  }

  public HttpPollingConnector(
      final HttpService httpService, final SharedExecutorService executorService) {
    this.httpService = httpService;
    this.executorService = executorService;
  }

  @Override
  public void activate(final InboundIntermediateConnectorContext context) {
    processInstancesFetcherTask =
        new ProcessInstancesFetcherTask(context, httpService, executorService);
    processInstancesFetcherTask.start();
  }

  @Override
  public void deactivate() {
    processInstancesFetcherTask.stop();
  }
}
