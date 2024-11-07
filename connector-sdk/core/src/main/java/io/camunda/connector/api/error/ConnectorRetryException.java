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
package io.camunda.connector.api.error;

import java.io.Serial;
import java.time.Duration;

public class ConnectorRetryException extends ConnectorException {

  @Serial private static final long serialVersionUID = 1L;

  private final Integer retries;

  private final Duration backoffDuration;

  ConnectorRetryException(
      String errorCode,
      String message,
      Throwable cause,
      Integer retries,
      Duration backoffDuration) {
    super(errorCode, message, cause);
    this.retries = retries;
    this.backoffDuration = backoffDuration;
  }

  public static ConnectorRetryExceptionBuilder builder() {
    return new ConnectorRetryExceptionBuilder();
  }

  public Integer getRetries() {
    return retries;
  }

  public Duration getBackoffDuration() {
    return backoffDuration;
  }
}
