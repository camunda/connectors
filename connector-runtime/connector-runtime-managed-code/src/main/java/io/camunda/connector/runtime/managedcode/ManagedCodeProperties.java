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
    Fake fake) {

  private static final Duration DEFAULT_EXECUTION_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration DEFAULT_PROVISIONING_TIMEOUT = Duration.ofMinutes(2);
  private static final Duration DEFAULT_PROVISIONING_RETRY_BACKOFF = Duration.ofSeconds(10);
  private static final int DEFAULT_INVOCATION_CONCURRENCY = 4;
  private static final int DEFAULT_PROVISIONING_CONCURRENCY = 2;
  private static final Duration DEFAULT_FAKE_PROVISIONING_DELAY = Duration.ofSeconds(2);

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
    fake = fake == null ? new Fake(null) : fake;
  }

  /**
   * Settings of the fake provider.
   *
   * @param provisioningDelay simulated time until a new deployment is ready; zero is allowed
   */
  public record Fake(Duration provisioningDelay) {
    public Fake {
      provisioningDelay =
          provisioningDelay == null || provisioningDelay.isNegative()
              ? DEFAULT_FAKE_PROVISIONING_DELAY
              : provisioningDelay;
    }
  }

  private static Duration positive(Duration value, Duration fallback) {
    return value != null && !value.isNegative() && !value.isZero() ? value : fallback;
  }
}
