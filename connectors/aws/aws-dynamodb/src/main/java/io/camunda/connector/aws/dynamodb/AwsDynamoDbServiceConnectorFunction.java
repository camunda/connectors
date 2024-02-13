/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.CredentialsProviderSupport;
import io.camunda.connector.generator.java.annotation.ElementTemplate;

@OutboundConnector(
    name = "AWS DynamoDB",
    inputVariables = {"authentication", "configuration", "input"},
    type = "io.camunda:aws-dynamodb:1")
@ElementTemplate(
    id = "io.camunda.connectors.AWSDynamoDB.v1",
    name = "AWS DynamoDB Outbound Connector",
    version = 8,
    description = "Work with tables and items using AWS DynamoDB service",
    documentationRef =
        "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/aws-dynamodb/",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation"),
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Queue properties"),
      @ElementTemplate.PropertyGroup(id = "input", label = "Input")
    },
    inputDataClass = AwsDynamoDbRequest.class,
    icon = "icon.svg")
public class AwsDynamoDbServiceConnectorFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    final AwsDynamoDbOperationFactory operationFactory = AwsDynamoDbOperationFactory.getInstance();
    final AwsDynamoDbRequest dynamoDbRequest = context.bindVariables(AwsDynamoDbRequest.class);
    return operationFactory
        .createOperation(dynamoDbRequest.getInput())
        .invoke(
            AwsDynamoDbClientSupplier.getDynamoDdClient(
                CredentialsProviderSupport.credentialsProvider(dynamoDbRequest),
                dynamoDbRequest.getConfiguration().region()));
  }
}
