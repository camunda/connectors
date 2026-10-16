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
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration of the optional managed-script worker. */
@ConfigurationProperties(prefix = "camunda.connector.managed-code")
public record ManagedCodeProperties(
    boolean enabled,
    String provider,
    String workerName,
    Duration executionTimeout,
    Duration provisioningTimeout,
    Duration provisioningRetryBackoff,
    int invocationConcurrency,
    int provisioningConcurrency,
    Local local) {

  private static final Duration DEFAULT_EXECUTION_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration DEFAULT_PROVISIONING_TIMEOUT = Duration.ofMinutes(2);
  private static final Duration DEFAULT_PROVISIONING_RETRY_BACKOFF = Duration.ofSeconds(10);
  private static final int DEFAULT_INVOCATION_CONCURRENCY = 4;
  private static final int DEFAULT_PROVISIONING_CONCURRENCY = 2;
  private static final Duration DEFAULT_LOCAL_INSTALL_TIMEOUT = Duration.ofMinutes(5);

  public ManagedCodeProperties {
    provider = provider == null ? "" : provider.strip();
    workerName = workerName == null || workerName.isBlank() ? "managed-script-worker" : workerName;
    executionTimeout = positive(executionTimeout, DEFAULT_EXECUTION_TIMEOUT);
    provisioningTimeout = positive(provisioningTimeout, DEFAULT_PROVISIONING_TIMEOUT);
    provisioningRetryBackoff =
        positive(provisioningRetryBackoff, DEFAULT_PROVISIONING_RETRY_BACKOFF);
    invocationConcurrency =
        invocationConcurrency > 0 ? invocationConcurrency : DEFAULT_INVOCATION_CONCURRENCY;
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
