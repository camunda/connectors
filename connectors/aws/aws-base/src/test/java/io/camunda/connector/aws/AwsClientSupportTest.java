/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.connector.aws.model.impl.AwsAuthentication.AwsStaticCredentialsAuthentication;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;

/**
 * Exercises {@link AwsClientSupport#configureClient} against a mocked {@link AwsClientBuilder}
 * seam, shared by every AWS SDK v2 connector migrated onto this helper (issue #7083).
 *
 * <p>This is deliberately a mock-based test, not a "build a real client and assert
 * isNotNull()"-style test: a real client build succeeds whether or not the credentials provider was
 * ever wired onto the builder (the SDK falls back to its own default resolution), so an isNotNull
 * assertion cannot detect a regression that silently stops wiring credentials from the request.
 * Verifying the exact calls made on the builder can.
 */
class AwsClientSupportTest {

  private interface TestClient extends AutoCloseable {}

  private interface TestBuilder extends AwsClientBuilder<TestBuilder, TestClient> {}

  private static AwsBaseRequest requestWith(final String region, final String endpoint) {
    var request = new AwsBaseRequest();
    request.setAuthentication(new AwsStaticCredentialsAuthentication("key", "secret"));
    request.setConfiguration(new AwsBaseConfiguration(region, endpoint));
    return request;
  }

  @Test
  void configureClientWiresCredentialsProviderDerivedFromRequest() {
    TestBuilder builder = mock(TestBuilder.class);
    ArgumentCaptor<AwsCredentialsProvider> captor =
        ArgumentCaptor.forClass(AwsCredentialsProvider.class);

    AwsClientSupport.configureClient(builder, requestWith("eu-central-1", null));

    verify(builder).credentialsProvider(captor.capture());
    var resolved = captor.getValue().resolveCredentials();
    assertThat(resolved.accessKeyId()).isEqualTo("key");
    assertThat(resolved.secretAccessKey()).isEqualTo("secret");
  }

  @Test
  void configureClientAppliesRegionWhenPresent() {
    TestBuilder builder = mock(TestBuilder.class);

    AwsClientSupport.configureClient(builder, requestWith("eu-central-1", null));

    verify(builder).region(Region.of("eu-central-1"));
  }

  @Test
  void configureClientOmitsRegionWhenAbsent() {
    TestBuilder builder = mock(TestBuilder.class);

    AwsClientSupport.configureClient(builder, requestWith(null, null));

    verify(builder, never()).region(any());
  }

  @Test
  void configureClientAppliesEndpointOverrideWhenSet() {
    TestBuilder builder = mock(TestBuilder.class);

    AwsClientSupport.configureClient(builder, requestWith("eu-central-1", "http://localhost:4566"));

    verify(builder).endpointOverride(URI.create("http://localhost:4566"));
  }

  @Test
  void configureClientSkipsEndpointOverrideWhenNull() {
    TestBuilder builder = mock(TestBuilder.class);

    AwsClientSupport.configureClient(builder, requestWith("eu-central-1", null));

    verify(builder, never()).endpointOverride(any());
  }

  /**
   * Regression guard for the blank-endpoint bug documented across the migrated connectors: a blank
   * (non-null) endpoint must not reach {@code endpointOverride(URI.create(""))}, which throws at
   * construction time.
   */
  @Test
  void configureClientSkipsEndpointOverrideWhenBlank() {
    TestBuilder builder = mock(TestBuilder.class);

    AwsClientSupport.configureClient(builder, requestWith("eu-central-1", "   "));

    verify(builder, never()).endpointOverride(any());
  }
}
