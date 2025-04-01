/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agents.schema;

import io.camunda.connector.agents.core.AgentsApplicationContextHolder;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

@OutboundConnector(
    name = "Ad-hoc subprocess tools schema",
    inputVariables = {"adHocSubprocessId"},
    type = "io.camunda.agents:adhoctoolsschema:1")
@ElementTemplate(
    id = "io.camunda.connectors.agents.adhoctoolsschema.v1",
    name = "Ad-hoc subprocess tools schema",
    description = "Connector to fetch tools schema information from an ad-hoc subprocess",
    inputDataClass = AdHocToolsSchemaFunction.AdHocToolsSchemaRequest.class,
    version = 1,
    propertyGroups = {@PropertyGroup(id = "tools", label = "Available Tools")},
    documentationRef = "https://example.com",
    icon = "adhoctoolsschema.svg")
public class AdHocToolsSchemaFunction implements OutboundConnectorFunction {

  private final AdHocToolsSchemaResolver schemaResolver;

  public AdHocToolsSchemaFunction() {
    this(schemaResolverFromStaticContext());
  }

  public AdHocToolsSchemaFunction(AdHocToolsSchemaResolver schemaResolver) {
    this.schemaResolver = schemaResolver;
  }

  private static AdHocToolsSchemaResolver schemaResolverFromStaticContext() {
    final var camundaClient = AgentsApplicationContextHolder.currentContext().camundaClient();
    return new AdHocToolsSchemaResolver(camundaClient);
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    AdHocToolsSchemaRequest request = context.bindVariables(AdHocToolsSchemaRequest.class);

    // TODO support multiple output formats (e.g. OpenAI, ...)?
    return schemaResolver.resolveSchema(
        context.getJobContext().getProcessDefinitionKey(), request.adHocSubprocessId());
  }

  public record AdHocToolsSchemaRequest(
      @NotBlank @TemplateProperty(group = "tools", label = "Ad-hoc subprocess ID")
          String adHocSubprocessId) {}
}
