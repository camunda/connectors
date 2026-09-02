/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.common.suppliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.connector.aws.model.impl.AwsAuthentication.AwsStaticCredentialsAuthentication;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;

class DefaultAmazonSQSClientSupplierTest {

  private final DefaultAmazonSQSClientSupplier supplier = new DefaultAmazonSQSClientSupplier();

  private static AwsBaseRequest requestWith(final String endpoint) {
    AwsBaseRequest request = new AwsBaseRequest();
    request.setAuthentication(new AwsStaticCredentialsAuthentication("key", "secret"));
    request.setConfiguration(new AwsBaseConfiguration("eu-central-1", endpoint));
    return request;
  }

  @Test
  void buildsClientWithRegionOnly() {
    try (SqsClient client = supplier.sqsClient(requestWith(null), "eu-central-1")) {
      assertThat(client).isNotNull();
    }
  }

  @Test
  void buildsClientWithEndpointOverrideWhenEndpointIsSet() {
    try (SqsClient client =
        supplier.sqsClient(requestWith("http://localhost:4566"), "eu-central-1")) {
      assertThat(client).isNotNull();
    }
  }

  /**
   * Regression guard for the blank-endpoint bug: an endpoint that arrives as an empty or blank
   * string must be treated as "no endpoint" (the region-only client path), not fed to {@code
   * URI.create("")} / {@code endpointOverride}.
   */
  @Test
  void buildsClientWithoutEndpointOverrideWhenEndpointIsBlank() {
    assertThatCode(
            () -> {
              try (SqsClient client = supplier.sqsClient(requestWith(""), "eu-central-1")) {
                assertThat(client).isNotNull();
              }
              try (SqsClient client = supplier.sqsClient(requestWith("   "), "eu-central-1")) {
                assertThat(client).isNotNull();
              }
            })
        .doesNotThrowAnyException();
  }
}
