/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.connector.aws.bedrock.model.BedrockRequest;
import io.camunda.connector.aws.model.impl.AwsAuthentication.AwsStaticCredentialsAuthentication;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import org.junit.jupiter.api.Test;

class BedrockExecutorTest {

  private static BedrockRequest requestWith(final String endpoint) {
    var request = new BedrockRequest();
    request.setAuthentication(new AwsStaticCredentialsAuthentication("key", "secret"));
    request.setConfiguration(new AwsBaseConfiguration("eu-central-1", endpoint));
    return request;
  }

  @Test
  void buildsExecutorWithRegionOnly() {
    BedrockExecutor executor = BedrockExecutor.create(requestWith(null));
    assertThat(executor).isNotNull();
  }

  @Test
  void buildsExecutorWithEndpointOverrideWhenEndpointIsSet() {
    BedrockExecutor executor = BedrockExecutor.create(requestWith("http://localhost:4566"));
    assertThat(executor).isNotNull();
  }

  /**
   * Regression guard for the blank-endpoint bug: a blank (non-null) endpoint must be treated as "no
   * endpoint", not fed to {@code URI.create("")} / {@code endpointOverride}.
   */
  @Test
  void buildsExecutorWithoutEndpointOverrideWhenEndpointIsBlank() {
    assertThatCode(() -> BedrockExecutor.create(requestWith(""))).doesNotThrowAnyException();
    assertThatCode(() -> BedrockExecutor.create(requestWith("   "))).doesNotThrowAnyException();
  }
}
