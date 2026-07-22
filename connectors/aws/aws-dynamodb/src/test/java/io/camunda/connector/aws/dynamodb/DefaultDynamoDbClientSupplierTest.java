/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.connector.aws.model.impl.AwsAuthentication.AwsStaticCredentialsAuthentication;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

class DefaultDynamoDbClientSupplierTest {

  private final DefaultDynamoDbClientSupplier supplier = new DefaultDynamoDbClientSupplier();

  private static AwsDynamoDbRequest requestWith(final String region, final String endpoint) {
    AwsDynamoDbRequest request = new AwsDynamoDbRequest();
    request.setAuthentication(new AwsStaticCredentialsAuthentication("key", "secret"));
    request.setConfiguration(new AwsBaseConfiguration(region, endpoint));
    return request;
  }

  @Test
  void buildsClientWithRegionOnly() {
    try (DynamoDbClient client = supplier.dynamoDbClient(requestWith("eu-central-1", null))) {
      assertThat(client).isNotNull();
    }
  }

  @Test
  void buildsClientWithEndpointOverrideWhenEndpointIsSet() {
    try (DynamoDbClient client =
        supplier.dynamoDbClient(requestWith("eu-central-1", "http://localhost:4566"))) {
      assertThat(client).isNotNull();
    }
  }

  /**
   * Regression guard for the blank-endpoint bug: an optional endpoint that arrives as an empty or
   * blank string must be treated as "no endpoint" (the region-only client path), not fed to {@code
   * URI.create("")} / {@code endpointOverride}. Delegating to {@code AwsClientSupport} guarantees
   * this because it only applies a non-blank endpoint; a blank one must build a client without
   * throwing.
   */
  @Test
  void buildsClientWithoutEndpointOverrideWhenEndpointIsBlank() {
    assertThatCode(
            () -> {
              try (DynamoDbClient client =
                  supplier.dynamoDbClient(requestWith("eu-central-1", ""))) {
                assertThat(client).isNotNull();
              }
              try (DynamoDbClient client =
                  supplier.dynamoDbClient(requestWith("eu-central-1", "   "))) {
                assertThat(client).isNotNull();
              }
            })
        .doesNotThrowAnyException();
  }
}
