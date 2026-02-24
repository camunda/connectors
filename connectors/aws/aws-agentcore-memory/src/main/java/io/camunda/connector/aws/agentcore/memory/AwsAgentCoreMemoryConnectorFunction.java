/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.agentcore.memory;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.CredentialsProviderSupportV2;
import io.camunda.connector.aws.agentcore.memory.model.request.AwsAgentCoreMemoryRequest;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.net.URI;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;

@OutboundConnector(
    name = "AWS AgentCore Memory",
    inputVariables = {
      "authentication",
      "configuration",
      "memoryId",
      "maxResults",
      "operation",
      "operationDiscriminator"
    },
    type = "io.camunda:aws-agentcore-memory:1")
@ElementTemplate(
    engineVersion = "^8.7",
    id = "io.camunda.connectors.aws.agentcore.memory.v1",
    name = "AWS AgentCore Memory Outbound Connector",
    description = "Retrieve long-term memory records from AWS Bedrock AgentCore Memory",
    inputDataClass = AwsAgentCoreMemoryRequest.class,
    version = 1,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "configuration", label = "Configuration"),
      @PropertyGroup(id = "operation", label = "Operation"),
      @PropertyGroup(id = "retrieve", label = "Retrieve memory records"),
    },
    icon = "icon.png")
public class AwsAgentCoreMemoryConnectorFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) {
    var request = context.bindVariables(AwsAgentCoreMemoryRequest.class);
    try (var client = buildClient(request)) {
      return new AwsAgentCoreMemoryExecutor(client, ConnectorsObjectMapperSupplier.getCopy())
          .execute(request, context::create);
    }
  }

  private BedrockAgentCoreClient buildClient(AwsAgentCoreMemoryRequest request) {
    var builder =
        BedrockAgentCoreClient.builder()
            .credentialsProvider(CredentialsProviderSupportV2.credentialsProvider(request));
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
