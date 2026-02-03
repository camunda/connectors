/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.document.DynamoDb;

public final class AwsDynamoDbClientSupplier {

  private AwsDynamoDbClientSupplier() {}

  public static DynamoDb getDynamoDdClient(
      final AwsCredentialsProvider credentialsProvider, final String region) {
    DynamoDbClient client =
        DynamoDbClient.builder()
            .credentialsProvider(credentialsProvider)
            .region(Region.of(region))
            .build();
    return new DynamoDb(client);
  }
}
