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

public record RetryContext(
    Map<String, Integer> attemptedRetriesByErrorCode,
    int initialJobRetries,
    Duration initialJobBackoffDuration) {

  public RetryContext incrementAttemptedRetries(String errorCode) {
    attemptedRetriesByErrorCode.compute(errorCode, (k, v) -> v == null ? 0 : v + 1);
    return this;
  }

  public RetryConfig computeNextRetryConfig(
      String errorCode, ConnectorRetryException.RetryPolicy retryPolicy) {
    Integer attemptedRetries = attemptedRetriesByErrorCode.get(errorCode);
    if (attemptedRetries == null) {
      throw new IllegalArgumentException(
          "No retry configuration found for error code " + errorCode);
    }
    return new RetryConfig(
        retryPolicy.retries() - attemptedRetries,
        retryPolicy.getNextBackoffDuration(attemptedRetries));
  }

  public record RetryConfig(int remainingRetries, Duration backoffDuration) {}
}
