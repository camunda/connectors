/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sendgrid;

import java.util.List;

public record SendGridErrors(List<SendGridError> errors) {
  @Override
  public String toString() {
    return "SendGrid returned the following errors: "
        + String.join("; ", errors.stream().map(SendGridError::message).toList());
  }

  public record SendGridError(String message) {}
}
