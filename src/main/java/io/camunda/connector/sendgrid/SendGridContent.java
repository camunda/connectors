/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sendgrid;

import io.camunda.connector.api.annotation.Secret;
import jakarta.validation.constraints.NotEmpty;
import java.util.Objects;

public class SendGridContent {
  @NotEmpty @Secret private String subject;
  @NotEmpty @Secret private String type;
  @NotEmpty @Secret private String value;

  public String getSubject() {
    return subject;
  }

  public void setSubject(final String subject) {
    this.subject = subject;
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public String getValue() {
    return value;
  }

  public void setValue(final String value) {
    this.value = value;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SendGridContent that = (SendGridContent) o;
    return Objects.equals(subject, that.subject)
        && Objects.equals(type, that.type)
        && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(subject, type, value);
  }

  @Override
  public String toString() {
    return "SendGridContent{"
        + "subject='"
        + subject
        + '\''
        + ", type='"
        + type
        + '\''
        + ", value='"
        + value
        + '\''
        + '}';
  }
}
