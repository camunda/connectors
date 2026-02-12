/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import io.camunda.connector.aws.AwsUtils;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import java.net.URI;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public final class AwsDynamoDbClientSupplier {

  private AwsDynamoDbClientSupplier() {}

  public static DynamoDbClient getDynamoDbClient(
      final AwsCredentialsProvider credentialsProvider, final AwsBaseConfiguration configuration) {
    var region = AwsUtils.extractRegionOrDefault(configuration, null);
    var builder =
        DynamoDbClient.builder().credentialsProvider(credentialsProvider).region(Region.of(region));
    Optional.ofNullable(configuration)
        .map(AwsBaseConfiguration::endpoint)
        .filter(endpoint -> !endpoint.isBlank())
        .ifPresent(endpoint -> builder.endpointOverride(URI.create(endpoint)));
    return builder.build();
  }
}
