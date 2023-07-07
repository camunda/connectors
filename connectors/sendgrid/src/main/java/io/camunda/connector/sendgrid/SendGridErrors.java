/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sendgrid;

import java.util.List;

public class SendGridErrors {

  List<SendGridError> errors;

  public List<SendGridError> getErrors() {
    return errors;
  }

  public void setErrors(final List<SendGridError> errors) {
    this.errors = errors;
  }

  @Override
  public String toString() {
    return "SendGrid returned the following errors: "
        + String.join("; ", errors.stream().map(SendGridError::getMessage).toList());
  }

  static class SendGridError {
    String message;

    public String getMessage() {
      return message;
    }

    public void setMessage(final String message) {
      this.message = message;
    }

    @Override
    public String toString() {
      return message;
    }
  }
}
