/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.bedrock.core.BedrockExecutor;
import io.camunda.connector.aws.bedrock.model.BedrockRequest;
import io.camunda.connector.generator.java.annotation.ElementTemplate;

@OutboundConnector(
    name = "AWS BedRock",
    inputVariables = {"authentication", "configuration", "action", "data"},
    type = "io.camunda:aws-bedrock:1")
@ElementTemplate(
    engineVersion = "^8.7",
    id = "io.camunda.connectors.aws.bedrock.v1",
    name = "AWS Bedrock Outbound Connector",
    description = "Invoke models and converse using AWS Bedrock.",
    metadata =
        @ElementTemplate.Metadata(
            keywords = {"invoke model", "run inference", "invokemodel API", "converse API"}),
    inputDataClass = BedrockRequest.class,
    version = 2,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Configuration"),
      @ElementTemplate.PropertyGroup(id = "action", label = "Action"),
      @ElementTemplate.PropertyGroup(id = "invokeModel", label = "Invoke Model"),
      @ElementTemplate.PropertyGroup(id = "converse", label = "Converse"),
    },
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-bedrock/",
    icon = "icon.svg")
public class BedrockConnectorFunction implements OutboundConnectorFunction {

  public BedrockConnectorFunction() {}

  @Override
  public Object execute(OutboundConnectorContext context) {
    BedrockRequest bedrockRequest = context.bindVariables(BedrockRequest.class);
    return BedrockExecutor.create(bedrockRequest).execute();
  }
}
