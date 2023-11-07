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

/** A retryable exception indicating failed inbound event correlation */
public class ConnectorCorrelationException extends ConnectorException {

  public enum CorrelationErrorReason {
    FAULT_ZEEBE_CLIENT_STATUS,
    FAULT_IDEMPOTENCY_KEY,
    OTHER
  }

  private final CorrelationErrorReason reason;

  public ConnectorCorrelationException(CorrelationErrorReason reason) {
    super("Failed to correlate inbound event, reason: " + reason);
    this.reason = reason;
  }

  public ConnectorCorrelationException(CorrelationErrorReason reason, Throwable cause) {
    super("Failed to correlate inbound event, reason: " + reason, cause);
    this.reason = reason;
  }

  public ConnectorCorrelationException(String message) {
    super(message);
    this.reason = CorrelationErrorReason.OTHER;
  }

  public ConnectorCorrelationException(String message, Throwable cause) {
    super(message, cause);
    this.reason = CorrelationErrorReason.OTHER;
  }

  public CorrelationErrorReason getReason() {
    return reason;
  }
}
