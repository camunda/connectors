/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DefaultDynamoDbClientSupplier implements DynamoDbClientSupplier {

  @Override
  public DynamoDbClient dynamoDbClient(
      final AwsCredentialsProvider credentialsProvider, final String region) {
    return DynamoDbClient.builder()
        .credentialsProvider(credentialsProvider)
        .region(Region.of(region))
        .build();
  }

  @Override
  public DynamoDbClient dynamoDbClient(
      final AwsCredentialsProvider credentialsProvider,
      final String region,
      final String endpoint) {
    return DynamoDbClient.builder()
        .credentialsProvider(credentialsProvider)
        .region(Region.of(region))
        .endpointOverride(URI.create(endpoint))
        .build();
  }
}
