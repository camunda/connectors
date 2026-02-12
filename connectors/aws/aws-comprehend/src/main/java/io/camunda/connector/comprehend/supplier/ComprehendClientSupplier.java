/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.supplier;

import io.camunda.connector.aws.AwsUtils;
import io.camunda.connector.aws.CredentialsProviderSupportV2;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.comprehend.model.ComprehendRequest;
import java.net.URI;
import java.util.Optional;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.comprehend.ComprehendAsyncClient;
import software.amazon.awssdk.services.comprehend.ComprehendClient;

public class ComprehendClientSupplier {

  public ComprehendClient getSyncClient(ComprehendRequest comprehendRequest) {
    return configureBuilder(ComprehendClient.builder(), comprehendRequest).build();
  }

  public ComprehendAsyncClient getAsyncClient(ComprehendRequest comprehendRequest) {
    return configureBuilder(ComprehendAsyncClient.builder(), comprehendRequest).build();
  }

  private <B extends AwsClientBuilder<B, ?>> B configureBuilder(
      B builder, ComprehendRequest comprehendRequest) {
    AwsBaseConfiguration configuration = comprehendRequest.getConfiguration();
    builder
        .credentialsProvider(CredentialsProviderSupportV2.credentialsProvider(comprehendRequest))
        .region(Region.of(AwsUtils.extractRegionOrDefault(configuration, null)));
    Optional.ofNullable(configuration)
        .map(AwsBaseConfiguration::endpoint)
        .filter(endpoint -> !endpoint.isBlank())
        .ifPresent(endpoint -> builder.endpointOverride(URI.create(endpoint)));
    return builder;
  }
}
