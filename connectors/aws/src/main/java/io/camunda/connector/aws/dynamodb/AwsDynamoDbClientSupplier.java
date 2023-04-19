/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;

public final class AwsDynamoDbClientSupplier {

  private AwsDynamoDbClientSupplier() {}

  public static DynamoDB getDynamoDdClient(
      final AWSStaticCredentialsProvider credentialsProvider,
      final AwsBaseConfiguration configuration) {
    AmazonDynamoDB client =
        AmazonDynamoDBClientBuilder.standard()
            .withCredentials(credentialsProvider)
            .withRegion(configuration.getRegion())
            .build();
    return new DynamoDB(client);
  }
}
