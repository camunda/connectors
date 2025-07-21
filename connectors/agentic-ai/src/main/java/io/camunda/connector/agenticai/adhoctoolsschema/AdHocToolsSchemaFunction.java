/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema;

import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaRequest;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;

@OutboundConnector(
    name = "Ad-hoc tools schema",
    inputVariables = {"data"},
    type = "io.camunda.agenticai:adhoctoolsschema:1")
@ElementTemplate(
    id = "io.camunda.connectors.agenticai.adhoctoolsschema.v1",
    name = "Ad-hoc tools schema",
    description =
        "Connector to fetch tools schema information from an ad-hoc sub-process. Compatible with 8.8.0-alpha6 or later.",
    documentationRef =
        "https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/agentic-ai-ad-hoc-tools-schema-resolver/",
    engineVersion = "^8.8",
    version = 1,
    inputDataClass = AdHocToolsSchemaRequest.class,
    propertyGroups = {@PropertyGroup(id = "tools", label = "Available tools")},
    icon = "adhoctoolsschema.svg")
public class AdHocToolsSchemaFunction implements OutboundConnectorFunction {

  private final ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
  private final AdHocToolsSchemaResolver toolsSchemaResolver;

  public AdHocToolsSchemaFunction(
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver,
      AdHocToolsSchemaResolver toolsSchemaResolver) {
    this.toolElementsResolver = toolElementsResolver;
    this.toolsSchemaResolver = toolsSchemaResolver;
  }

  @Override
  public AdHocToolsSchemaResponse execute(OutboundConnectorContext context) {
    final var request = context.bindVariables(AdHocToolsSchemaRequest.class);
    final var elements =
        toolElementsResolver.resolveToolElements(
            context.getJobContext().getProcessDefinitionKey(), request.data().containerElementId());

    return toolsSchemaResolver.resolveAdHocToolsSchema(elements);
  }
}
