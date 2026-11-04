/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;

class AwsManagedCodeAutoConfigurationTest {

  private static final String ROLE = "arn:aws:iam::000000000000:role/executor";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(AwsManagedCodeAutoConfiguration.class));

  private final ApplicationContextRunner aws =
      contextRunner.withPropertyValues(
          "camunda.connector.managed-code.enabled=true",
          "camunda.connector.managed-code.provider=aws",
          "camunda.connector.managed-code.aws.region=eu-central-1",
          "camunda.connector.managed-code.aws.execution-role-arn=" + ROLE);

  @Test
  void registersTheProviderOnlyWhenManagedCodeIsEnabledWithIt() {
    aws.run(context -> {
      assertThat(context).hasNotFailed();
      assertThat(context).hasSingleBean(AwsLambdaManagedCodeProvider.class);
      assertThat(context.getBean(AwsLambdaManagedCodeProvider.class).name()).isEqualTo("aws");
    });
    aws.withPropertyValues("camunda.connector.managed-code.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(AwsLambdaManagedCodeProvider.class));
    aws.withPropertyValues("camunda.connector.managed-code.provider=local")
        .run(context -> assertThat(context).doesNotHaveBean(AwsLambdaManagedCodeProvider.class));
  }

  @Test
  void hasDefaultsForTheMemoryAndTheLogRetention() {
    aws.run(context -> {
      final var properties = context.getBean(AwsManagedCodeProperties.class);
      assertThat(properties.memoryMb()).isEqualTo(512);
      assertThat(properties.logRetentionDays()).isEqualTo(30);
      assertThat(properties.region()).isEqualTo("eu-central-1");
    });
  }

  @Test
  void failsStartupWithoutAnExecutionRole() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.managed-code.enabled=true",
            "camunda.connector.managed-code.provider=aws",
            "camunda.connector.managed-code.aws.region=eu-central-1")
        .run(context ->
            assertThat(context)
                .getFailure()
                .rootCause()
                .hasMessageContaining("execution-role-arn must be set"));
  }

  @Test
  void failsStartupForAnExecutionTimeoutLongerThanALambdaFunctionRuns() {
    aws.withPropertyValues("camunda.connector.managed-code.execution-timeout=15m")
        .run(context ->
            assertThat(context)
                .getFailure()
                .rootCause()
                .hasMessageContaining("at most 895 seconds"));
  }

  @Test
  void failsStartupForARetentionThatCloudWatchRejects() {
    aws.withPropertyValues("camunda.connector.managed-code.aws.log-retention-days=10")
        .run(context ->
            assertThat(context)
                .getFailure()
                .rootCause()
                .hasMessageContaining("log-retention-days 10"));
  }

  @Test
  void rejectsMemoryOutsideTheLambdaRange() {
    assertThatThrownBy(() -> new AwsManagedCodeProperties(null, ROLE, 64, null))
        .hasMessageContaining("between 128 and 10240");
  }

  @Test
  void neverRetriesAnInvocationAndReadsForLongerThanTheFunctionRuns() {
    try (var invoker = AwsClients.invoker("eu-central-1", 35, AwsClients.credentials())) {
      final var configuration = invoker.serviceClientConfiguration();
      assertThat(configuration.overrideConfiguration().retryStrategy())
          .hasValueSatisfying(strategy -> assertThat(strategy.maxAttempts()).isEqualTo(1));
      assertThat(configuration.overrideConfiguration().apiCallAttemptTimeout())
          .hasValueSatisfying(
              timeout -> assertThat(timeout).isGreaterThan(java.time.Duration.ofSeconds(35)));
      assertThat(configuration.region()).isEqualTo(Region.EU_CENTRAL_1);
    }
  }

  @Test
  void keepsTheRetriesOfTheSdkForTheControlPlane() {
    try (var lambda = AwsClients.lambda("eu-central-1", AwsClients.credentials())) {
      assertThat(lambda.serviceClientConfiguration().overrideConfiguration().retryStrategy())
          .hasValueSatisfying(strategy -> assertThat(strategy.maxAttempts()).isGreaterThan(1));
    }
  }

  @Test
  void sharesOneDefaultChainAcrossTheClients() {
    final var credentials = AwsClients.credentials();
    try (var lambda = AwsClients.lambda("eu-central-1", credentials);
        var invoker = AwsClients.invoker("eu-central-1", 35, credentials);
        var logs = AwsClients.logs("eu-central-1", credentials)) {
      assertThat(credentials).isInstanceOf(DefaultCredentialsProvider.class);
      assertThat(lambda.serviceClientConfiguration().credentialsProvider()).isSameAs(credentials);
      assertThat(invoker.serviceClientConfiguration().credentialsProvider()).isSameAs(credentials);
      assertThat(logs.serviceClientConfiguration().credentialsProvider()).isSameAs(credentials);
    }
  }

  @Test
  void canReadAWebIdentityTokenFileWithoutTheMetadataServer() throws ClassNotFoundException {
    // The default chain only tries a web-identity token file when STS is on the classpath.
    assertThat(
            Class.forName(
                "software.amazon.awssdk.services.sts.internal.StsWebIdentityCredentialsProviderFactory"))
        .isNotNull();
  }
}
