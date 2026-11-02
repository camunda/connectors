/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.aws;

import java.time.Duration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.lambda.LambdaClient;

/**
 * The AWS clients of the provider. Credentials come from the SDK's default chain only: environment,
 * system properties, a web-identity token file, the shared profile files, then the container and
 * instance metadata. The runtime therefore needs no key in its configuration, and works without a
 * metadata server.
 */
final class AwsClients {

  /** What a synchronous invocation may take beyond the function timeout: start and network. */
  static final Duration INVOCATION_MARGIN = Duration.ofSeconds(30);

  private AwsClients() {}

  /** Lambda control plane, with the SDK's default retries. */
  static LambdaClient lambda(String region) {
    return configure(LambdaClient.builder(), region).build();
  }

  static CloudWatchLogsClient logs(String region) {
    return configure(CloudWatchLogsClient.builder(), region).build();
  }

  /**
   * Lambda invocations: never retried by the SDK, so that a script runs at most once per job
   * attempt, and read for longer than the function may run, so that the client does not give up on
   * a script that is still running.
   */
  static LambdaClient invoker(String region, int functionTimeoutSeconds) {
    final var timeout = Duration.ofSeconds(functionTimeoutSeconds).plus(INVOCATION_MARGIN);
    return configure(LambdaClient.builder(), region)
        .httpClientBuilder(Apache5HttpClient.builder().socketTimeout(timeout))
        .overrideConfiguration(o ->
            o.retryStrategy(AwsRetryStrategy.doNotRetry())
                .apiCallAttemptTimeout(timeout)
                .apiCallTimeout(timeout))
        .build();
  }

  private static <B extends AwsClientBuilder<B, ?>> B configure(B builder, String region) {
    builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
    if (region != null) {
      builder.region(Region.of(region));
    }
    return builder;
  }
}
