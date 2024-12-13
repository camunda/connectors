/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.automationanywhere.auth.AuthenticationFactory;
import io.camunda.connector.automationanywhere.model.request.AutomationAnywhereRequest;
import io.camunda.connector.automationanywhere.operations.OperationFactory;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.http.base.HttpService;
import java.util.Map;

@OutboundConnector(
    name = "Automation Anywhere Outbound Connector",
    inputVariables = {"authentication", "operation", "configuration"},
    type = "io.camunda:connector-automationanywhere:1")
@ElementTemplate(
    id = "io.camunda.connectors.AutomationAnywhere",
    name = "Automation Anywhere Outbound Connector",
    description = "Manage work items in Automation Anywhere queues.",
    metadata =
        @ElementTemplate.Metadata(
            keywords = {
              "add work item to queue",
              "get work item from queue",
              "get work item result from queue by ID"
            }),
    inputDataClass = AutomationAnywhereRequest.class,
    version = 2,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Configuration"),
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "input", label = "Input"),
      @ElementTemplate.PropertyGroup(id = "timeout", label = "Timeout")
    },
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/automation-anywhere/",
    icon = "icon.svg")
public class AutomationAnywhereConnector implements OutboundConnectorFunction {
  protected static final String AUTHORIZATION_KEY = "X-Authorization";

  private final HttpService httpService;
  private final ObjectMapper objectMapper;

  public AutomationAnywhereConnector() {
    this(ConnectorsObjectMapperSupplier.getCopy());
  }

  public AutomationAnywhereConnector(final ObjectMapper objectMapper) {
    this(new HttpService(), objectMapper);
  }

  public AutomationAnywhereConnector(
      final HttpService httpService, final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.httpService = httpService;
  }

  @Override
  public Object execute(final OutboundConnectorContext context) throws Exception {
    final var aaRequest = context.bindVariables(AutomationAnywhereRequest.class);

    final var provider =
        AuthenticationFactory.createProvider(aaRequest.authentication(), aaRequest.configuration());
    final var token = provider.obtainToken(httpService, objectMapper);
    final var authenticationHeader = Map.of(AUTHORIZATION_KEY, token);

    final var operation =
        OperationFactory.createOperation(aaRequest.operation(), aaRequest.configuration());
    return operation.execute(httpService, authenticationHeader);
  }
}
