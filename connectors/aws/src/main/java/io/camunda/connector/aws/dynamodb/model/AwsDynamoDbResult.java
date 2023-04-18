/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.dynamodb.model;

import java.util.Objects;

public class AwsDynamoDbResult {
  private String action;
  private String status;
  private Object response;

  public AwsDynamoDbResult(final String action, final String status) {
    this.action = action;
    this.status = status;
  }

  public AwsDynamoDbResult(final String action, final String status, final Object response) {
    this.action = action;
    this.status = status;
    this.response = response;
  }

  public String getAction() {
    return action;
  }

  public void setAction(final String action) {
    this.action = action;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(final String status) {
    this.status = status;
  }

  public Object getResponse() {
    return response;
  }

  public void setResponse(final Object response) {
    this.response = response;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AwsDynamoDbResult that = (AwsDynamoDbResult) o;
    return Objects.equals(action, that.action)
        && Objects.equals(status, that.status)
        && Objects.equals(response, that.response);
  }

  @Override
  public int hashCode() {
    return Objects.hash(action, status, response);
  }

  @Override
  public String toString() {
    return "AwsDynamoDbResult{"
        + "action='"
        + action
        + "'"
        + ", status='"
        + status
        + "'"
        + ", response="
        + response
        + "}";
  }
}
