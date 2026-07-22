/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Client-supplier seam for the AWS SDK v2 {@link DynamoDbClient}, mirroring {@code
 * io.camunda.connector.common.suppliers.AmazonSQSClientSupplier} (see aws-sqs). Replaces the former
 * static-method-plus-mockStatic seam ({@code AwsDynamoDbClientSupplier}).
 */
public interface DynamoDbClientSupplier {

  DynamoDbClient dynamoDbClient(AwsCredentialsProvider credentialsProvider, String region);

  DynamoDbClient dynamoDbClient(
      AwsCredentialsProvider credentialsProvider, String region, String endpoint);
}
