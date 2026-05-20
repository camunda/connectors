/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.connector.aws.model.impl.AwsAuthentication;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

class CredentialsProviderSupportV2Test {

  @Test
  void staticCredentials_shouldReturnStaticCredentialsProvider() {
    var request =
        requestWith(new AwsAuthentication.AwsStaticCredentialsAuthentication("key", "secret"));
    var provider = CredentialsProviderSupportV2.credentialsProvider(request);
    assertThat(provider.resolveCredentials().accessKeyId()).isEqualTo("key");
  }

  @Test
  void defaultCredentials_shouldReturnDefaultCredentialsProvider() {
    var request = requestWith(new AwsAuthentication.AwsDefaultCredentialsChainAuthentication());
    var provider = CredentialsProviderSupportV2.credentialsProvider(request);
    assertThat(provider).isInstanceOf(DefaultCredentialsProvider.class);
  }

  @Test
  void stsShouldBeOnClasspathForIrsaSupport() {
    // DefaultCredentialsProvider includes WebIdentityTokenFileCredentialsProvider (IRSA) only
    // when software.amazon.awssdk:sts is on the classpath. Without it the provider is silently
    // skipped. This test guards against accidental removal of that dependency.
    assertThatCode(() -> Class.forName("software.amazon.awssdk.services.sts.StsClient"))
        .as("software.amazon.awssdk:sts must be on the classpath for IRSA support")
        .doesNotThrowAnyException();
  }

  private static AwsBaseRequest requestWith(AwsAuthentication auth) {
    var request = new AwsBaseRequest();
    request.setAuthentication(auth);
    request.setConfiguration(new AwsBaseConfiguration("eu-central-1", null));
    return request;
  }
}
