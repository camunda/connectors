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
package io.camunda.connector.api.inbound.webhook;

import io.camunda.connector.api.error.ConnectorException;

/**
 * Unchecked exception indicating issues with a webhook connector. Must define an HTTP status code
 * that will be returned to the caller.
 */
public sealed class WebhookConnectorException extends ConnectorException {

  protected int statusCode;

  public WebhookConnectorException(int statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public static final class WebhookSecurityException extends WebhookConnectorException {

    public enum Reason {
      INVALID_SIGNATURE,
      INVALID_CREDENTIALS,
      FORBIDDEN,
      OTHER
    }

    public WebhookSecurityException(int statusCode, Reason reason, String message) {
      super(statusCode, "Reason: " + reason + ". Details: " + message);
    }

    public WebhookSecurityException(int statusCode, Reason reason) {
      super(statusCode, "Reason: " + reason);
    }
  }
}
