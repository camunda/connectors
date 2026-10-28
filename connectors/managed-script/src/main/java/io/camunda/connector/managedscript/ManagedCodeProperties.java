/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the optional managed-script connector.
 *
 * @param enabled whether the managed-script connector serves its job type; disabled by default
 * @param provider name of the managed-code provider that runs the scripts
 * @param deploymentNamePrefix prefix of every executor name, one per cluster; the identity of the
 *     runtime may only manage executors whose name starts with it (default {@code camunda-ms-})
 * @param executionTimeout how long a script may run before the executor stops it (default 30s)
 * @param provisioningTimeout how long a job may wait for the executors of the cluster; the job
 *     timeout of a managed-script task must cover it plus the execution timeout (default 2m)
 * @param provisioningRetryBackoff backoff of a job that failed because of the platform (default
 *     10s)
 */
@ConfigurationProperties(prefix = "camunda.connector.managed-code")
public record ManagedCodeProperties(
    boolean enabled,
    String provider,
    String deploymentNamePrefix,
    Duration executionTimeout,
    Duration provisioningTimeout,
    Duration provisioningRetryBackoff) {

  static final String DEFAULT_DEPLOYMENT_NAME_PREFIX = "camunda-ms-";

  /** {@code <py|js>-} and the 12 hexadecimal characters of the wrapper digest. */
  static final int EXECUTOR_NAME_SUFFIX_LENGTH = 15;

  /** Cloud Run function ids have at most 63 characters, Lambda function names 64. */
  private static final int MAX_DEPLOYMENT_NAME_PREFIX_LENGTH = 63 - EXECUTOR_NAME_SUFFIX_LENGTH;

  private static final Pattern DEPLOYMENT_NAME_PREFIX =
      Pattern.compile("[a-z][a-z0-9-]{0,%d}".formatted(MAX_DEPLOYMENT_NAME_PREFIX_LENGTH - 1));

  private static final Duration DEFAULT_EXECUTION_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration DEFAULT_PROVISIONING_TIMEOUT = Duration.ofMinutes(2);
  private static final Duration DEFAULT_PROVISIONING_RETRY_BACKOFF = Duration.ofSeconds(10);

  public ManagedCodeProperties {
    provider = provider == null ? "" : provider.strip();
    deploymentNamePrefix =
        deploymentNamePrefix == null || deploymentNamePrefix.isBlank()
            ? DEFAULT_DEPLOYMENT_NAME_PREFIX
            : deploymentNamePrefix.strip();
    if (!DEPLOYMENT_NAME_PREFIX.matcher(deploymentNamePrefix).matches()) {
      throw new IllegalArgumentException(
          ("camunda.connector.managed-code.deployment-name-prefix '%s' must start with a lowercase"
                  + " letter and contain at most %d lowercase letters, digits and hyphens")
              .formatted(deploymentNamePrefix, MAX_DEPLOYMENT_NAME_PREFIX_LENGTH));
    }
    executionTimeout = positive(executionTimeout, DEFAULT_EXECUTION_TIMEOUT);
    provisioningTimeout = positive(provisioningTimeout, DEFAULT_PROVISIONING_TIMEOUT);
    provisioningRetryBackoff =
        positive(provisioningRetryBackoff, DEFAULT_PROVISIONING_RETRY_BACKOFF);
  }

  private static Duration positive(Duration value, Duration fallback) {
    return value != null && !value.isNegative() && !value.isZero() ? value : fallback;
  }
}
