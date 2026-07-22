/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class DefaultDynamoDbClientSupplierTest {

  private static final AwsCredentialsProvider CREDENTIALS_PROVIDER =
      StaticCredentialsProvider.create(AwsBasicCredentials.create("key", "secret"));

  private final DefaultDynamoDbClientSupplier supplier = new DefaultDynamoDbClientSupplier();

  @Test
  void buildsClientWithRegionOnly() {
    try (DynamoDbClient client = supplier.dynamoDbClient(CREDENTIALS_PROVIDER, "eu-central-1")) {
      assertThat(client).isNotNull();
    }
  }

  @Test
  void buildsClientWithEndpointOverrideWhenEndpointIsSet() {
    try (DynamoDbClient client =
        supplier.dynamoDbClient(CREDENTIALS_PROVIDER, "eu-central-1", "http://localhost:4566")) {
      assertThat(client).isNotNull();
    }
  }
}
