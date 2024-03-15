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
package io.camunda.connector.api.error.retry;

import java.time.Duration;
import java.util.Map;

public class RetryContext {
  private final Map<String, Integer> attemptedRetriesByErrorCode;
  private final int initialJobRetries;
  boolean compatibilityMode;

  public RetryContext(Map<String, Integer> attemptedRetriesByErrorCode, int initialJobRetries) {
    this(attemptedRetriesByErrorCode, initialJobRetries, true);
  }

  private RetryContext(
      Map<String, Integer> attemptedRetriesByErrorCode,
      int initialJobRetries,
      boolean compatibilityMode) {
    this.attemptedRetriesByErrorCode = attemptedRetriesByErrorCode;
    this.initialJobRetries = initialJobRetries;
    this.compatibilityMode = compatibilityMode;
  }

  /**
   * Creates a new RetryContext with compatibility mode enabled. This mode is used to ensure
   * compatibility with connectors that don't fetch the "retryContext" variable. We artificially
   * increment the retry count for the first error code.
   */
  public static RetryContext createWithCompatibilityMode(String errorCode, int initialJobRetries) {
    return new RetryContext(Map.of(errorCode, 1), initialJobRetries, true);
  }

  public RetryContext incrementAttemptedRetries(String errorCode) {
    checkAndUpdateCompatibilityMode();
    attemptedRetriesByErrorCode.compute(errorCode, (k, v) -> v == null ? 0 : v + 1);
    return this;
  }

  /**
   * We call it compatibility mode because we assume that the connectors won't fetch the
   * "retryContext" variable. We artificially increment the retry count for the first error code.
   * Without this increment we might end up in a situation where the connector will retry the job
   * indefinitely.
   */
  private void checkAndUpdateCompatibilityMode() {
    // this means we have 1 map entry with a value of 1
    if (compatibilityMode) {
      compatibilityMode = false;
      attemptedRetriesByErrorCode.put(attemptedRetriesByErrorCode.keySet().iterator().next(), 0);
    }
  }

  public RetryConfig computeNextRetryConfig(
      String errorCode, ConnectorRetryException.RetryPolicy retryPolicy) {
    Integer attemptedRetries = attemptedRetriesByErrorCode.get(errorCode);
    if (attemptedRetries == null) {
      throw new IllegalArgumentException("No retry entry found for error code " + errorCode);
    }
    int totalRetries;
    if (ConnectorRetryException.CATCH_ALL_ERROR_CODE.equals(errorCode)) {
      totalRetries = initialJobRetries;
    } else {
      totalRetries = retryPolicy.retries();
    }
    return new RetryConfig(
        totalRetries - attemptedRetries, retryPolicy.getNextBackoffDuration(attemptedRetries));
  }

  public record RetryConfig(int remainingRetries, Duration backoffDuration) {}
}
