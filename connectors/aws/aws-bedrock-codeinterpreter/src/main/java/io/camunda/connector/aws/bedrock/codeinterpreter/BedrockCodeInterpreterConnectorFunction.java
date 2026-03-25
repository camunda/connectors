/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.codeinterpreter;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.CredentialsProviderSupportV2;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.CodeInterpreterRequest;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import java.net.URI;
import java.util.function.Function;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreAsyncClient;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;

@OutboundConnector(
    name = "AWS Bedrock Code Interpreter",
    inputVariables = {"authentication", "configuration", "input"},
    type = "io.camunda:aws-bedrock-codeinterpreter:1")
@ElementTemplate(
    engineVersion = "^8.8",
    id = "io.camunda.connectors.aws.bedrock.codeinterpreter.v1",
    name = "AWS Bedrock Code Interpreter Outbound Connector",
    description = "Execute Python code in a secure AWS Bedrock AgentCore Code Interpreter sandbox",
    inputDataClass = CodeInterpreterRequest.class,
    version = 1,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "configuration", label = "Configuration"),
      @PropertyGroup(id = "codeExecution", label = "Code Execution"),
    },
    icon = "icon.png")
public class BedrockCodeInterpreterConnectorFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) {
    var request = context.bindVariables(CodeInterpreterRequest.class);
    var credentialsProvider = CredentialsProviderSupportV2.credentialsProvider(request);
    var config = request.getConfiguration();
    Function<DocumentCreationRequest, Document> createDocument = context::create;

    var syncBuilder = BedrockAgentCoreClient.builder().credentialsProvider(credentialsProvider);
    var asyncBuilder =
        BedrockAgentCoreAsyncClient.builder().credentialsProvider(credentialsProvider);

    if (config != null && config.region() != null) {
      var region = Region.of(config.region());
      syncBuilder.region(region);
      asyncBuilder.region(region);
    }
    if (config != null && config.endpoint() != null && !config.endpoint().isBlank()) {
      var endpoint = URI.create(config.endpoint());
      syncBuilder.endpointOverride(endpoint);
      asyncBuilder.endpointOverride(endpoint);
    }

    try (var syncClient = syncBuilder.build();
        var asyncClient = asyncBuilder.build()) {
      return new CodeInterpreterExecutor(syncClient, asyncClient, createDocument).execute(request);
    }
  }
}
