/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.suppliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.connector.aws.model.impl.AwsAuthentication.AwsStaticCredentialsAuthentication;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.sns.outbound.model.SnsConnectorRequest;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.SnsClient;

class SnsClientSupplierTest {

  private final SnsClientSupplier supplier = new SnsClientSupplier();

  private static SnsConnectorRequest requestWith(final String endpoint) {
    var request = new SnsConnectorRequest();
    request.setAuthentication(new AwsStaticCredentialsAuthentication("key", "secret"));
    request.setConfiguration(new AwsBaseConfiguration("eu-central-1", endpoint));
    return request;
  }

  @Test
  void buildsClientWithRegionOnly() {
    try (SnsClient client = supplier.getSnsClient(requestWith(null), "eu-central-1")) {
      assertThat(client).isNotNull();
    }
  }

  @Test
  void buildsClientWithEndpointOverrideWhenEndpointIsSet() {
    try (SnsClient client =
        supplier.getSnsClient(requestWith("http://localhost:4566"), "eu-central-1")) {
      assertThat(client).isNotNull();
    }
  }

  /**
   * Regression guard for the blank-endpoint bug: a blank (non-null) endpoint must be treated as "no
   * endpoint", not fed to {@code URI.create("")} / {@code endpointOverride}.
   */
  @Test
  void buildsClientWithoutEndpointOverrideWhenEndpointIsBlank() {
    assertThatCode(
            () -> {
              try (SnsClient client = supplier.getSnsClient(requestWith(""), "eu-central-1")) {
                assertThat(client).isNotNull();
              }
              try (SnsClient client = supplier.getSnsClient(requestWith("   "), "eu-central-1")) {
                assertThat(client).isNotNull();
              }
            })
        .doesNotThrowAnyException();
  }
}
