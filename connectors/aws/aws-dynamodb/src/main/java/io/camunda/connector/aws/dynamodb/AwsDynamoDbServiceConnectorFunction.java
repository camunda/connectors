/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import com.google.gson.Gson;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.aws.CredentialsProviderSupport;

@OutboundConnector(
    name = "AWS",
    inputVariables = {"authentication", "configuration", "input"},
    type = "io.camunda:aws-dynamodb:1")
public class AwsDynamoDbServiceConnectorFunction implements OutboundConnectorFunction {

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {
    final Gson gson = GsonDynamoDbComponentSupplier.gsonInstance();
    final AwsDynamoDbOperationFactory operationFactory = AwsDynamoDbOperationFactory.getInstance();
    final AwsDynamoDbRequest dynamoDbRequest =
        gson.fromJson(context.getVariables(), AwsDynamoDbRequest.class);
    context.validate(dynamoDbRequest);
    context.replaceSecrets(dynamoDbRequest);
    return operationFactory
        .createOperation(dynamoDbRequest.getInput())
        .invoke(
            AwsDynamoDbClientSupplier.getDynamoDdClient(
                CredentialsProviderSupport.credentialsProvider(dynamoDbRequest),
                dynamoDbRequest.getConfiguration().getRegion()));
  }
}
