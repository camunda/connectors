/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.agentcore.memory;

import static io.camunda.connector.aws.AwsClientSupport.createClient;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.bedrock.agentcore.memory.model.request.AgentCoreMemoryRequest;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;

@OutboundConnector(
    name = "AWS Bedrock AgentCore Long-Term Memory",
    inputVariables = {"authentication", "configuration", "memoryId", "namespace", "operation"},
    type = "io.camunda:aws-bedrock-agentcore-lt-memory:1")
@ElementTemplate(
    engineVersion = "^8.10",
    id = "io.camunda.connectors.aws.bedrock.agentcore.memory.longterm.v1",
    name = "AWS Bedrock AgentCore Long-Term Memory Connector",
    description =
        "Retrieve persistent knowledge — facts, preferences, and summaries — from AWS Bedrock AgentCore Long-Term Memory",
    inputDataClass = AgentCoreMemoryRequest.class,
    version = 1,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "configuration", label = "Configuration"),
      @PropertyGroup(id = "operation", label = "Operation"),
      @PropertyGroup(id = "retrieve", label = "Retrieve Memory Records"),
      @PropertyGroup(id = "list", label = "List Memory Records"),
    },
    icon = "icon.svg")
public class AgentCoreMemoryConnectorFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) {
    var request = context.bindVariables(AgentCoreMemoryRequest.class);
    try (var client = createClient(BedrockAgentCoreClient.builder(), request)) {
      return new AgentCoreMemoryExecutor(client).execute(request);
    }
  }
}
