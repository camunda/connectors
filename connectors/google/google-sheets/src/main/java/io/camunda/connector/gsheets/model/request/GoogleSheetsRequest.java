/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.model.request;

import com.google.api.client.util.Key;
import io.camunda.connector.api.annotation.Secret;
import io.camunda.google.model.GoogleBaseRequest;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class GoogleSheetsRequest extends GoogleBaseRequest {

  @Key @Valid @NotNull @Secret private Input operation;

  public Input getOperation() {
    return operation;
  }

  public void setOperation(Input operation) {
    this.operation = operation;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GoogleSheetsRequest that = (GoogleSheetsRequest) o;
    return Objects.equals(authentication, that.authentication)
        && Objects.equals(operation, that.operation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(authentication, operation);
  }

  @Override
  public String toString() {
    return "GoogleSheetsRequest{"
        + "authentication="
        + authentication
        + ", operation="
        + operation
        + '}';
  }
}
