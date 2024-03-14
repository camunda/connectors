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

import io.camunda.connector.api.error.ConnectorException;
import java.io.Serial;
import java.time.Duration;

/**
 * Unchecked exception indicating issues with a connector. Extends {@link ConnectorException} and
 * adds the ability to specify the number of retries and the backoff duration through a {@link
 * RetryPolicy}.
 *
 * <p>Default retry policy is 3 retries without any backoff duration. Please use the {@link
 * ConnectorRetryExceptionBuilder} to create a new instance of this exception.
 *
 * @see RetryPolicy
 * @see RetryPolicy.RetryStrategy
 * @see ConnectorException
 */
public class ConnectorRetryException extends ConnectorException {

  /**
   * The input variable name for the retry context. Should be injected in your connector's
   * inputVariables when registering a worker.
   */
  public static final String RETRY_CONTEXT_INPUT_VARIABLE = "retryContext";

  public static final int DEFAULT_RETRIES = 3;

  public static final String DEFAULT_RETRY_ERROR_CODE = "RETRY_ERROR";
  public static final String CATCH_ALL_ERROR_CODE = "CATCH_ALL_ERROR";

  /** Default policy: 3 retries without any backoff */
  static final RetryPolicy DEFAULT_RETRY_POLICY = new RetryPolicy(DEFAULT_RETRIES);

  @Serial private static final long serialVersionUID = 1L;

  protected RetryPolicy retryPolicy;

  ConnectorRetryException(
      String errorCode, String message, Throwable cause, RetryPolicy retryPolicy) {
    super(errorCode, message, cause);
    this.retryPolicy = retryPolicy;
  }

  /**
   * @return the number of retries
   */
  public int getRetries() {
    return retryPolicy.retries();
  }

  /**
   * @return the {@link RetryPolicy}
   */
  public RetryPolicy getRetryPolicy() {
    return retryPolicy;
  }

  /**
   * Represents the retry policy for a connector. The policy includes the number of retries, the
   * initial backoff duration, and the retry strategy.
   *
   * <p>
   *
   * @param retries the number of retries
   * @param initialBackoff the initial backoff duration
   * @param retryStrategy the retry strategy
   * @see RetryStrategy
   */
  public record RetryPolicy(int retries, Duration initialBackoff, RetryStrategy retryStrategy) {
    static final double DEFAULT_MULTIPLIER = 1.6;

    public RetryPolicy(int retries, Duration initialBackoff) {
      this(retries, initialBackoff, RetryStrategy.LINEAR);
    }

    public RetryPolicy(int retries) {
      this(retries, null);
    }

    public Duration getNextBackoffDuration(int attemptedRetries) {
      if (initialBackoff == null) return null;

      return switch (retryStrategy) {
        case LINEAR -> initialBackoff;
        case EXPONENTIAL -> initialBackoff.multipliedBy(
            Math.round(Math.pow(DEFAULT_MULTIPLIER, attemptedRetries)));
      };
    }

    public enum RetryStrategy {
      EXPONENTIAL,
      LINEAR,
    }
  }
}
