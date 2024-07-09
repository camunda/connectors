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
import io.camunda.connector.aws.CredentialsProviderSupportV2;
import io.camunda.connector.aws.bedrock.model.BedrockRequest;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@OutboundConnector(
    name = "AWS BedRock",
    inputVariables = {"authentication", "configuration", "action", "payload"},
    type = "io.camunda:aws-bedrock:1")
@ElementTemplate(
    id = "io.camunda.connectors.AWSBEDROCK.v1",
    name = "AWS BedRock Outbound Connector",
    description = "Execute bedrock requests",
    inputDataClass = BedrockRequest.class,
    version = 1,
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Configuration"),
      @ElementTemplate.PropertyGroup(id = "action", label = "Action"),
      @ElementTemplate.PropertyGroup(id = "invokeModel", label = "Invoke"),
      @ElementTemplate.PropertyGroup(id = "converse", label = "Converse"),
    },
    documentationRef = "https://docs.camunda.io/docs/",
    icon = "icon.svg")
public class BedrockConnectorFunction implements OutboundConnectorFunction {

  public BedrockConnectorFunction() {}

  @Override
  public Object execute(OutboundConnectorContext context) {
    BedrockRequest bedrockRequest = context.bindVariables(BedrockRequest.class);
    BedrockRuntimeClient bedrockRuntimeClient =
        BedrockRuntimeClient.builder()
            .credentialsProvider(CredentialsProviderSupportV2.credentialsProvider(bedrockRequest))
            .region(Region.of(bedrockRequest.getConfiguration().region()))
            .build();

    return bedrockRequest;
  }
}
