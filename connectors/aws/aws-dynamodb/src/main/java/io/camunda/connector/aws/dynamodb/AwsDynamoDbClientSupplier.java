/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;

public final class AwsDynamoDbClientSupplier {

  private AwsDynamoDbClientSupplier() {}

  public static DynamoDB getDynamoDdClient(
      final AWSCredentialsProvider credentialsProvider, final String region) {
    AmazonDynamoDB client =
        AmazonDynamoDBClientBuilder.standard()
            .withCredentials(credentialsProvider)
            .withRegion(region)
            .build();
    return new DynamoDB(client);
  }
}
