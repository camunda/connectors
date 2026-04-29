/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.agentcore.runtime;

import static io.camunda.connector.aws.AwsClientSupport.createClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.aws.bedrock.agentcore.runtime.model.request.AgentCoreRuntimeRequest;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
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
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-bedrock-agentcore-runtime/",
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "configuration", label = "Configuration"),
      @PropertyGroup(id = "agentConfig", label = "Agent Configuration")
    },
    icon = "icon.svg")
public class AgentCoreRuntimeConnectorFunction implements OutboundConnectorFunction {

  private static final ObjectMapper OBJECT_MAPPER = ObjectMapperSupplier.getMapperInstance();

  @Override
  public Object execute(OutboundConnectorContext context) {
    var request = context.bindVariables(AgentCoreRuntimeRequest.class);
    try (var client = createClient(BedrockAgentCoreClient.builder(), request)) {
      return new AgentCoreRuntimeExecutor(client, OBJECT_MAPPER).invoke(request.getInput());
    }
  }
}
