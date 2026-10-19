/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.managedcode;

import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the optional managed-script connector.
 *
 * @param deploymentNamePrefix prefix of every provider resource name, one per cluster; the identity
 *     of the runtime may only manage resources whose name starts with it
 */
@ConfigurationProperties(prefix = "camunda.connector.managed-code")
public record ManagedCodeProperties(
    boolean enabled,
    String provider,
    String deploymentNamePrefix,
    Duration executionTimeout,
    Duration provisioningTimeout,
    Duration provisioningRetryBackoff,
    int provisioningConcurrency,
    Local local) {

  private static final Duration DEFAULT_EXECUTION_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration DEFAULT_PROVISIONING_TIMEOUT = Duration.ofMinutes(2);
  private static final Duration DEFAULT_PROVISIONING_RETRY_BACKOFF = Duration.ofSeconds(10);

  /** Lambda function names have at most 64 characters, 40 of which are the name hash. */
  private static final Pattern DEPLOYMENT_NAME_PREFIX = Pattern.compile("[a-z][a-z0-9-]{0,23}");

  private static final int DEFAULT_PROVISIONING_CONCURRENCY = 2;
  private static final Duration DEFAULT_LOCAL_INSTALL_TIMEOUT = Duration.ofMinutes(5);

  public ManagedCodeProperties {
    provider = provider == null ? "" : provider.strip();
    deploymentNamePrefix =
        deploymentNamePrefix == null || deploymentNamePrefix.isBlank()
            ? ArtifactSpec.DEFAULT_DEPLOYMENT_NAME_PREFIX
            : deploymentNamePrefix.strip();
    if (!DEPLOYMENT_NAME_PREFIX.matcher(deploymentNamePrefix).matches()) {
      throw new IllegalArgumentException(
          ("camunda.connector.managed-code.deployment-name-prefix '%s' must start with a lowercase"
                  + " letter and contain at most 24 lowercase letters, digits and hyphens")
              .formatted(deploymentNamePrefix));
    }
    executionTimeout = positive(executionTimeout, DEFAULT_EXECUTION_TIMEOUT);
    provisioningTimeout = positive(provisioningTimeout, DEFAULT_PROVISIONING_TIMEOUT);
    provisioningRetryBackoff =
        positive(provisioningRetryBackoff, DEFAULT_PROVISIONING_RETRY_BACKOFF);
    provisioningConcurrency =
        provisioningConcurrency > 0 ? provisioningConcurrency : DEFAULT_PROVISIONING_CONCURRENCY;
    local = local == null ? new Local(null, null) : local;
  }

  /**
   * Settings of the local provider.
   *
   * @param directory where deployments are kept; survives restarts so that deployments are adopted
   * @param installTimeout bound of one dependency installation
   */
  public record Local(Path directory, Duration installTimeout) {
    public Local {
      directory =
          directory == null
              ? Path.of(System.getProperty("java.io.tmpdir"), "camunda-managed-code")
              : directory;
      installTimeout = positive(installTimeout, DEFAULT_LOCAL_INSTALL_TIMEOUT);
    }
  }

  private static Duration positive(Duration value, Duration fallback) {
    return value != null && !value.isNegative() && !value.isZero() ? value : fallback;
  }
}
