/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.knowledgebase;

import static io.camunda.connector.aws.AwsClientSupport.createClient;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.bedrock.knowledgebase.model.request.BedrockKnowledgeBaseRequest;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;

@OutboundConnector(
    name = "AWS Bedrock Knowledge Base",
    inputVariables = {"authentication", "configuration", "knowledgeBaseId", "operation"},
    type = "io.camunda:aws-bedrock-knowledgebase:1")
@ElementTemplate(
    engineVersion = "^8.10",
    id = "io.camunda.connectors.aws.bedrock.knowledgebase.v1",
    name = "AWS Bedrock Knowledge Base Outbound Connector",
    description = "Retrieve relevant documents from an AWS Bedrock Knowledge Base",
    inputDataClass = BedrockKnowledgeBaseRequest.class,
    version = 1,
    propertyGroups = {
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "configuration", label = "Configuration"),
      @PropertyGroup(id = "operation", label = "Operation"),
      @PropertyGroup(id = "retrieve", label = "Retrieve from Knowledge Base"),
    },
    icon = "icon.svg")
public class BedrockKnowledgeBaseConnectorFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) {
    var request = context.bindVariables(BedrockKnowledgeBaseRequest.class);
    try (var client = createClient(BedrockAgentRuntimeClient.builder(), request)) {
      return new BedrockKnowledgeBaseExecutor(client).execute(request, context);
    }
  }
}
