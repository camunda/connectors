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
import java.net.URI;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;

@OutboundConnector(
    name = "AWS Bedrock AgentCore Runtime",
    inputVariables = {"authentication", "configuration", "input"},
    type = "io.camunda:aws-bedrock-agentcore-runtime:1")
@ElementTemplate(
    engineVersion = "^8.9", // TODO: update to ^8.10 when available
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
    icon = "icon.svg")
public class AgentCoreRuntimeConnectorFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) {
    var request = context.bindVariables(AgentCoreRuntimeRequest.class);
    try (var client = createClient(request, BedrockAgentCoreClient.builder())) {
      return new AgentCoreRuntimeExecutor(client).invoke(request.getInput());
    }
  }

  private <B extends AwsClientBuilder<B, C>, C extends AutoCloseable> C createClient(
      AgentCoreRuntimeRequest request, B builder) {
    builder.credentialsProvider(CredentialsProviderSupportV2.credentialsProvider(request));
    var config = request.getConfiguration();
    if (config != null && config.region() != null) {
      builder.region(Region.of(config.region()));
    }
    if (config != null && config.endpoint() != null && !config.endpoint().isBlank()) {
      builder.endpointOverride(URI.create(config.endpoint()));
    }
    return builder.build();
  }
}
