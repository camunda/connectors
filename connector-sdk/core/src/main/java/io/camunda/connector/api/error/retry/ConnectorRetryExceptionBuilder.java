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

import static io.camunda.connector.api.error.retry.ConnectorRetryException.DEFAULT_RETRY_ERROR_CODE;
import static io.camunda.connector.api.error.retry.ConnectorRetryException.DEFAULT_RETRY_POLICY;

import org.apache.commons.lang3.StringUtils;

/** Builder for creating a {@link ConnectorRetryException}. */
public class ConnectorRetryExceptionBuilder {
  private String message;

  private String errorCode = DEFAULT_RETRY_ERROR_CODE;

  private Throwable cause;

  private ConnectorRetryException.RetryPolicy retryPolicy = DEFAULT_RETRY_POLICY;

  public ConnectorRetryExceptionBuilder cause(Throwable cause) {
    this.cause = cause;
    return this;
  }

  public ConnectorRetryExceptionBuilder errorCode(String errorCode) {
    this.errorCode = errorCode;
    return this;
  }

  public ConnectorRetryExceptionBuilder message(String message) {
    this.message = message;
    return this;
  }

  public ConnectorRetryExceptionBuilder retryPolicy(
      ConnectorRetryException.RetryPolicy retryPolicy) {
    this.retryPolicy = retryPolicy;
    return this;
  }

  /**
   * Builds a new {@link ConnectorRetryException}.
   *
   * @return the exception
   * @throws IllegalArgumentException if none of message, or cause is set
   */
  public ConnectorRetryException build() throws IllegalArgumentException {
    if (StringUtils.isBlank(message) && cause == null) {
      throw new IllegalArgumentException("At least one of message, or cause must be set.");
    }
    return new ConnectorRetryException(errorCode, message, cause, retryPolicy);
  }
}
