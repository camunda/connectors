/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sendgrid;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import java.util.Objects;

public class SendGridTemplate {
  @NotEmpty private String id;
  @NotEmpty private Map<String, Object> data;

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public Map<String, Object> getData() {
    return data;
  }

  public void setData(final Map<String, Object> data) {
    this.data = data;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SendGridTemplate that = (SendGridTemplate) o;
    return Objects.equals(id, that.id) && Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, data);
  }

  @Override
  public String toString() {
    return "SendGridTemplate{" + "id='" + id + '\'' + ", data=" + data + '}';
  }
}
