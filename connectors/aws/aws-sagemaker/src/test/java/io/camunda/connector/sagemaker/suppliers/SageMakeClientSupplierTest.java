/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker.suppliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.connector.aws.model.impl.AwsAuthentication.AwsStaticCredentialsAuthentication;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.sagemaker.model.SageMakerRequest;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeAsyncClient;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;

class SageMakeClientSupplierTest {

  private final SageMakeClientSupplier supplier = new SageMakeClientSupplier();

  private static SageMakerRequest requestWith(final String endpoint) {
    var request = new SageMakerRequest();
    request.setAuthentication(new AwsStaticCredentialsAuthentication("key", "secret"));
    request.setConfiguration(new AwsBaseConfiguration("eu-central-1", endpoint));
    return request;
  }

  @Test
  void buildsSyncClientWithRegionOnly() {
    try (SageMakerRuntimeClient client = supplier.getSyncClient(requestWith(null))) {
      assertThat(client).isNotNull();
    }
  }

  @Test
  void buildsAsyncClientWithRegionOnly() {
    try (SageMakerRuntimeAsyncClient client = supplier.getAsyncClient(requestWith(null))) {
      assertThat(client).isNotNull();
    }
  }

  @Test
  void buildsSyncClientWithEndpointOverrideWhenEndpointIsSet() {
    try (SageMakerRuntimeClient client =
        supplier.getSyncClient(requestWith("http://localhost:4566"))) {
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
              try (SageMakerRuntimeClient client = supplier.getSyncClient(requestWith(""))) {
                assertThat(client).isNotNull();
              }
              try (SageMakerRuntimeClient client = supplier.getSyncClient(requestWith("   "))) {
                assertThat(client).isNotNull();
              }
            })
        .doesNotThrowAnyException();
  }
}
