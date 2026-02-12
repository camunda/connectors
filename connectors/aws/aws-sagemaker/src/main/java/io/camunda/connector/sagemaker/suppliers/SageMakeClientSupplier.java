/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker.suppliers;

import io.camunda.connector.aws.AwsUtils;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeAsyncClient;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;

public class SageMakeClientSupplier {

  public SageMakerRuntimeClient getSyncClient(
      final AwsCredentialsProvider credentialsProvider, final AwsBaseConfiguration configuration) {
    var builder =
        SageMakerRuntimeClient.builder()
            .credentialsProvider(credentialsProvider)
            .region(Region.of(AwsUtils.extractRegionOrDefault(configuration, null)));
    if (configuration != null
        && configuration.endpoint() != null
        && !configuration.endpoint().isBlank()) {
      builder = builder.endpointOverride(java.net.URI.create(configuration.endpoint()));
    }
    return builder.build();
  }

  public SageMakerRuntimeAsyncClient getAsyncClient(
      final AwsCredentialsProvider credentialsProvider, final AwsBaseConfiguration configuration) {
    var builder =
        SageMakerRuntimeAsyncClient.builder()
            .credentialsProvider(credentialsProvider)
            .region(Region.of(AwsUtils.extractRegionOrDefault(configuration, null)));
    if (configuration != null
        && configuration.endpoint() != null
        && !configuration.endpoint().isBlank()) {
      builder = builder.endpointOverride(java.net.URI.create(configuration.endpoint()));
    }
    return builder.build();
  }
}
