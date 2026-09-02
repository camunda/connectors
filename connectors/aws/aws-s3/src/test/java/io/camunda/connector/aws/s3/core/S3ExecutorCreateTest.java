/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.s3.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.connector.aws.model.impl.AwsAuthentication.AwsStaticCredentialsAuthentication;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.aws.s3.model.request.S3Request;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link S3Executor#create} (the real {@code AwsClientSupport}-backed client construction
 * path), as opposed to {@code S3ExecutorTest}, which only ever constructs {@link S3Executor}
 * directly with a pre-built (mocked) {@code S3Client}.
 */
class S3ExecutorCreateTest {

  private static S3Request requestWith(final String endpoint) {
    var request = new S3Request();
    request.setAuthentication(new AwsStaticCredentialsAuthentication("key", "secret"));
    request.setConfiguration(new AwsBaseConfiguration("eu-central-1", endpoint));
    return request;
  }

  @Test
  void buildsExecutorWithRegionOnly() {
    S3Executor executor = S3Executor.create(requestWith(null), req -> null);
    assertThat(executor).isNotNull();
  }

  @Test
  void buildsExecutorWithEndpointOverrideWhenEndpointIsSet() {
    S3Executor executor = S3Executor.create(requestWith("http://localhost:4566"), req -> null);
    assertThat(executor).isNotNull();
  }

  /**
   * Regression guard for the blank-endpoint bug: a blank (non-null) endpoint must be treated as "no
   * endpoint", not fed to {@code URI.create("")} / {@code endpointOverride}.
   */
  @Test
  void buildsExecutorWithoutEndpointOverrideWhenEndpointIsBlank() {
    assertThatCode(() -> S3Executor.create(requestWith(""), req -> null))
        .doesNotThrowAnyException();
    assertThatCode(() -> S3Executor.create(requestWith("   "), req -> null))
        .doesNotThrowAnyException();
  }
}
