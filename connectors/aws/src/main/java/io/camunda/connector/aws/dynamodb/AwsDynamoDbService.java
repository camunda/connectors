/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.google.gson.Gson;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.model.AwsService;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import java.util.Objects;

public class AwsDynamoDbService implements AwsService {
  private String type;

  public AwsDynamoDbService() {}

  @Override
  public Object invoke(
      final AWSStaticCredentialsProvider credentialsProvider,
      final AwsBaseConfiguration configuration,
      final OutboundConnectorContext context) {

    final Gson gson = GsonDynamoDbComponentSupplier.gsonInstance();
    final AwsDynamoDbOperationFactory operationFactory = AwsDynamoDbOperationFactory.getInstance();

    final AwsDynamoDbRequest dynamoDbRequest =
        gson.fromJson(context.getVariables(), AwsDynamoDbRequest.class);
    context.validate(dynamoDbRequest);
    context.replaceSecrets(dynamoDbRequest);

    return operationFactory
        .createOperation(dynamoDbRequest.getInput())
        .invoke(AwsDynamoDbClientSupplier.getDynamoDdClient(credentialsProvider, configuration));
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AwsDynamoDbService that = (AwsDynamoDbService) o;
    return Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type);
  }

  @Override
  public String toString() {
    return "AwsDynamoDbService{" + "type='" + type + "'" + "}";
  }
}
