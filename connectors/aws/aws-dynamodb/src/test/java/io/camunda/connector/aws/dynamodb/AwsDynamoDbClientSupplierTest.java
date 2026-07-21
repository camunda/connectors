/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import org.junit.jupiter.api.Test;

class AwsDynamoDbClientSupplierTest {

  private static final AWSCredentialsProvider CREDENTIALS_PROVIDER =
      new AWSStaticCredentialsProvider(new BasicAWSCredentials("key", "secret"));

  @Test
  void buildsClientWithRegionOnlyWhenNoEndpointConfigured() {
    DynamoDB client =
        AwsDynamoDbClientSupplier.getDynamoDdClient(CREDENTIALS_PROVIDER, "eu-central-1");

    assertThat(client).isNotNull();
  }

  @Test
  void buildsClientWithRegionOnlyWhenEndpointIsNull() {
    DynamoDB client =
        AwsDynamoDbClientSupplier.getDynamoDdClient(CREDENTIALS_PROVIDER, "eu-central-1", null);

    assertThat(client).isNotNull();
  }

  @Test
  void buildsClientWithRegionOnlyWhenEndpointIsBlank() {
    DynamoDB client =
        AwsDynamoDbClientSupplier.getDynamoDdClient(CREDENTIALS_PROVIDER, "eu-central-1", "  ");

    assertThat(client).isNotNull();
  }

  @Test
  void buildsClientWithEndpointConfigurationWhenEndpointIsSet() {
    DynamoDB client =
        AwsDynamoDbClientSupplier.getDynamoDdClient(
            CREDENTIALS_PROVIDER, "eu-central-1", "http://localhost:4566");

    assertThat(client).isNotNull();
  }
}
