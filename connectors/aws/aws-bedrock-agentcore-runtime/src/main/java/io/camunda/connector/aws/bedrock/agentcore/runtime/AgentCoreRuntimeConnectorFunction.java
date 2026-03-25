/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.agentcore.runtime;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.CredentialsProviderSupportV2;
import io.camunda.connector.aws.bedrock.agentcore.runtime.model.request.AgentCoreRuntimeRequest;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;

@OutboundConnector(
    name = "AWS Bedrock AgentCore Runtime",
    inputVariables = {"authentication", "configuration", "agentRuntimeArn", "prompt", "sessionId"},
    type = "io.camunda:aws-bedrock-agentcore-runtime:1")
@ElementTemplate(
    id = "io.camunda.connectors.aws.bedrock.agentcore.runtime.v1",
    name = "AWS Bedrock AgentCore Runtime",
    version = 1,
    inputDataClass = AgentCoreRuntimeRequest.class,
    description = "Invoke an external agent running in AWS Bedrock AgentCore Runtime",
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "configuration", label = "Configuration"),
      @PropertyGroup(id = "agentConfig", label = "Agent Configuration")
    },
    documentationRef =
        "https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/runtime-invoke-agent.html",
    icon = "icon.png")
public class AgentCoreRuntimeConnectorFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    var request = context.bindVariables(AgentCoreRuntimeRequest.class);
    var region = Region.of(request.getConfiguration().region());
    var credentialsProvider = CredentialsProviderSupportV2.credentialsProvider(request);

    try (var client =
        BedrockAgentCoreClient.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)
            .build()) {
      return new AgentCoreRuntimeExecutor(client).invoke(request);
    }
  }
}
