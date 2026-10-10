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

/** Configuration for the optional managed-code reconciliation workload. */
@ConfigurationProperties(prefix = "camunda.connector.managed-code")
public record ManagedCodeProperties(
    boolean enabled,
    String provider,
    String worker,
    Duration interval,
    Duration initialDelay,
    Duration leaseDuration,
    int batchSize,
    int concurrency,
    int queueCapacity,
    Duration shutdownTimeout,
    boolean localExecutionEnabled,
    Duration executionTimeout,
    int invocationConcurrency) {

  private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(5);
  private static final Duration DEFAULT_LEASE_DURATION = Duration.ofMinutes(2);
  private static final int DEFAULT_BATCH_SIZE = 20;
  private static final int DEFAULT_CONCURRENCY = 4;
  private static final int DEFAULT_QUEUE_CAPACITY = 64;
  private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration DEFAULT_EXECUTION_TIMEOUT = Duration.ofSeconds(30);
  private static final int DEFAULT_INVOCATION_CONCURRENCY = 4;

  public ManagedCodeProperties {
    provider = provider == null ? "" : provider;
    worker = worker == null || worker.isBlank() ? "managed-code-runtime" : worker;
    interval = positive(interval, DEFAULT_INTERVAL);
    initialDelay = initialDelay == null || initialDelay.isNegative() ? Duration.ZERO : initialDelay;
    leaseDuration = positive(leaseDuration, DEFAULT_LEASE_DURATION);
    batchSize = positive(batchSize, DEFAULT_BATCH_SIZE);
    concurrency = positive(concurrency, DEFAULT_CONCURRENCY);
    queueCapacity = positive(queueCapacity, DEFAULT_QUEUE_CAPACITY);
    shutdownTimeout = positive(shutdownTimeout, DEFAULT_SHUTDOWN_TIMEOUT);
    executionTimeout = positive(executionTimeout, DEFAULT_EXECUTION_TIMEOUT);
    invocationConcurrency = positive(invocationConcurrency, DEFAULT_INVOCATION_CONCURRENCY);
  }

  private static int positive(int value, int fallback) {
    return value > 0 ? value : fallback;
  }

  private static Duration positive(Duration value, Duration fallback) {
    return value != null && !value.isNegative() && !value.isZero() ? value : fallback;
  }
}
