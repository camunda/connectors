/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.awslambda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.connector.aws.model.impl.AwsAuthentication.AwsStaticCredentialsAuthentication;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.awslambda.model.AwsLambdaRequest;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.lambda.LambdaClient;

class AwsLambdaSupplierTest {

  private final AwsLambdaSupplier supplier = new AwsLambdaSupplier();

  private static AwsLambdaRequest requestWith(final String endpoint) {
    var request = new AwsLambdaRequest();
    request.setAuthentication(new AwsStaticCredentialsAuthentication("key", "secret"));
    request.setConfiguration(new AwsBaseConfiguration("eu-central-1", endpoint));
    return request;
  }

  @Test
  void buildsClientWithRegionOnly() {
    try (LambdaClient client = supplier.awsLambdaService(requestWith(null), "eu-central-1")) {
      assertThat(client).isNotNull();
    }
  }

  @Test
  void buildsClientWithEndpointOverrideWhenEndpointIsSet() {
    try (LambdaClient client =
        supplier.awsLambdaService(requestWith("http://localhost:4566"), "eu-central-1")) {
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
              try (LambdaClient client =
                  supplier.awsLambdaService(requestWith(""), "eu-central-1")) {
                assertThat(client).isNotNull();
              }
              try (LambdaClient client =
                  supplier.awsLambdaService(requestWith("   "), "eu-central-1")) {
                assertThat(client).isNotNull();
              }
            })
        .doesNotThrowAnyException();
  }
}
