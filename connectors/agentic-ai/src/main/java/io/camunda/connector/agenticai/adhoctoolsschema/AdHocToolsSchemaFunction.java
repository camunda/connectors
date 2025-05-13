/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaRequest;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.AdHocToolsSchemaResolver;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

@OutboundConnector(
    name = "Ad-hoc tools schema (alpha)",
    inputVariables = {"data"},
    type = "io.camunda.agenticai:adhoctoolsschema:0")
@ElementTemplate(
    id = "io.camunda.connectors.agenticai.adhoctoolsschema.v0",
    name = "Ad-hoc tools schema (alpha)",
    description = "Connector to fetch tools schema information from an ad-hoc sub-process",
    engineVersion = "^8.8",
    version = 0,
    inputDataClass = AdHocToolsSchemaRequest.class,
    propertyGroups = {@PropertyGroup(id = "tools", label = "Available Tools")},
    icon = "adhoctoolsschema.svg")
public class AdHocToolsSchemaFunction implements OutboundConnectorFunction {

  private final AdHocToolsSchemaResolver schemaResolver;

  public AdHocToolsSchemaFunction(AdHocToolsSchemaResolver schemaResolver) {
    this.schemaResolver = schemaResolver;
  }

  @Override
  public AdHocToolsSchemaResponse execute(OutboundConnectorContext context) {
    AdHocToolsSchemaRequest request = context.bindVariables(AdHocToolsSchemaRequest.class);

    return schemaResolver.resolveSchema(
        context.getJobContext().getProcessDefinitionKey(), request.data().containerElementId());
  }
}
