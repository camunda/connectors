/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.knowledgebase;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.CredentialsProviderSupportV2;
import io.camunda.connector.aws.bedrock.knowledgebase.model.request.BedrockKnowledgeBaseRequest;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.net.URI;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;

@OutboundConnector(
    name = "AWS Bedrock Knowledge Base",
    inputVariables = {
      "authentication",
      "configuration",
      "knowledgeBaseId",
      "operation",
      "operationDiscriminator"
    },
    type = "io.camunda:aws-bedrock-knowledgebase:1")
@ElementTemplate(
    engineVersion = "^8.9", // TODO: update to ^8.10 when available
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
    try (var client = createClient(request, BedrockAgentRuntimeClient.builder())) {
      return new BedrockKnowledgeBaseExecutor(client, ConnectorsObjectMapperSupplier.getCopy())
          .execute(request, context::create);
    }
  }

  private <B extends AwsClientBuilder<B, C>, C extends AutoCloseable> C createClient(
      BedrockKnowledgeBaseRequest request, B builder) {
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
