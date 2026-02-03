/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.common.suppliers;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsSyncClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

public class DefaultAmazonSQSClientSupplier implements AmazonSQSClientSupplier {

  public SqsClient sqsClient(
      final AwsCredentialsProvider credentialsProvider, final String region) {
    return SqsClient.builder()
        .credentialsProvider(credentialsProvider)
        .region(Region.of(region))
        .build();
  }

  public SqsClient sqsClient(
      final AwsCredentialsProvider credentialsProvider,
      final String region,
      final String endpoint) {
    return SqsClient.builder()
        .credentialsProvider(credentialsProvider)
        .endpointOverride(new AwsSyncClientBuilder.EndpointConfiguration(endpoint, region))
        .build();
  }
}
