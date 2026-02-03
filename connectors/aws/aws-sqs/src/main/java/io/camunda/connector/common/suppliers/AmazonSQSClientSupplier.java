/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.common.suppliers;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.sqs.SqsClient;

public interface AmazonSQSClientSupplier {
  SqsClient sqsClient(AwsCredentialsProvider credentialsProvider, String region);

  SqsClient sqsClient(AwsCredentialsProvider credentialsProvider, String region, String endpoint);
}
