/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.awslambda;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsSyncClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;

public class AwsLambdaSupplier {

  public LambdaClient awsLambdaService(
      final AwsCredentialsProvider credentialsProvider, final String region) {
    return LambdaClient.builder()
        .credentialsProvider(credentialsProvider)
        .region(Region.of(region))
        .build();
  }

  public LambdaClient awsLambdaService(
      final AwsCredentialsProvider credentialsProvider,
      final String region,
      final String endpoint) {
    return LambdaClient.builder()
        .credentialsProvider(credentialsProvider)
        .endpointOverride(new AwsSyncClientBuilder.EndpointConfiguration(endpoint, region))
        .build();
  }
}
