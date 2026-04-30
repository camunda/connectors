/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.codeinterpreter;

import static io.camunda.connector.aws.AwsClientSupport.createClient;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.CodeInterpreterRequest;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreAsyncClient;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;

@OutboundConnector(
    name = "AWS Bedrock Code Interpreter",
    inputVariables = {"authentication", "configuration", "input"},
    type = "io.camunda:aws-bedrock-codeinterpreter:1")
@ElementTemplate(
    engineVersion = "^8.10",
    id = "io.camunda.connectors.aws.bedrock.codeinterpreter.v1",
    name = "AWS Bedrock Code Interpreter Outbound Connector",
    description = "Execute Python code in a secure AWS Bedrock AgentCore Code Interpreter sandbox",
    inputDataClass = CodeInterpreterRequest.class,
    version = 1,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "configuration", label = "Configuration"),
      @PropertyGroup(id = "codeExecution", label = "Code Execution"),
      @PropertyGroup(id = "session", label = "Session Settings"),
    },
    icon = "icon.svg")
public class BedrockCodeInterpreterConnectorFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) {
    var request = context.bindVariables(CodeInterpreterRequest.class);
    try (var syncClient = createClient(BedrockAgentCoreClient.builder(), request);
        var asyncClient = createClient(BedrockAgentCoreAsyncClient.builder(), request)) {
      return new CodeInterpreterExecutor(syncClient, asyncClient, context::create)
          .execute(request, context.getJobContext().getElementInstanceKey());
    }
  }
}
