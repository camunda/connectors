/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.authorization;

import java.io.IOException;

public class WebhookAuthorizationException extends IOException {

  private static final String MESSAGE_PATTERN = "Webhook authorization failed: %s";

  public WebhookAuthorizationException() {
    super();
  }

  public WebhookAuthorizationException(String message) {
    super(String.format(MESSAGE_PATTERN, message));
  }
}
