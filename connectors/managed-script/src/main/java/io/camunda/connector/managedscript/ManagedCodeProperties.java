/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import io.camunda.connector.managedscript.api.ManagedCodeSettings;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the optional managed-script connector.
 *
 * @param enabled whether the managed-script connector serves its job type; disabled by default
 * @param provider name of the managed-code provider that runs the scripts
 * @param deploymentNamePrefix prefix of every executor name, one per cluster; the identity of the
 *     runtime may only manage executors whose name starts with it (default {@code camunda-ms-})
 * @param executionTimeout how long a script may run before the executor stops it (default 30s)
 * @param provisioningTimeout how long a job waits for the executor of its language before it fails
 *     with {@code PROVISIONING_PENDING}, its retries unchanged (default 2m)
 * @param grantPropagationWindow how long after the first attempt of the runtime a permission denial
 *     is taken for grants that still propagate, and tried again (default 2m)
 * @param provisioningRetryBackoff backoff of a job that failed because of the platform and keeps
 *     its retries, whatever the task's {@code retryBackoff} header says (default 10s)
 */
@ConfigurationProperties(prefix = "camunda.connector.managed-code")
public record ManagedCodeProperties(
    boolean enabled,
    String provider,
    String deploymentNamePrefix,
    Duration executionTimeout,
    Duration provisioningTimeout,
    Duration grantPropagationWindow,
    Duration provisioningRetryBackoff) {

  private static final Duration DEFAULT_PROVISIONING_TIMEOUT = Duration.ofMinutes(2);
  // IAM grants took 66 to 98 seconds to apply, measured once.
  private static final Duration DEFAULT_GRANT_PROPAGATION_WINDOW = Duration.ofMinutes(2);
  private static final Duration DEFAULT_PROVISIONING_RETRY_BACKOFF = Duration.ofSeconds(10);

  public ManagedCodeProperties {
    provider = provider == null ? "" : provider;
    // Read by the providers too: their defaults and validation are those of the API.
    final var settings = new ManagedCodeSettings(deploymentNamePrefix, executionTimeout);
    deploymentNamePrefix = settings.deploymentNamePrefix();
    executionTimeout = settings.executionTimeout();
    provisioningTimeout = positive(provisioningTimeout, DEFAULT_PROVISIONING_TIMEOUT);
    grantPropagationWindow =
        grantPropagationWindow == null || grantPropagationWindow.isNegative()
            ? DEFAULT_GRANT_PROPAGATION_WINDOW
            : grantPropagationWindow;
    provisioningRetryBackoff =
        positive(provisioningRetryBackoff, DEFAULT_PROVISIONING_RETRY_BACKOFF);
  }

  private static Duration positive(Duration value, Duration fallback) {
    return value != null && !value.isNegative() && !value.isZero() ? value : fallback;
  }
}
