/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql.model;

import java.util.Objects;

public class ErrorResponse {
  private String error;

  private String errorCode;

  public String getError() {
    return error;
  }

  public void setError(final String error) {
    this.error = error;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ErrorResponse that = (ErrorResponse) o;
    return error.equals(that.error) && errorCode.equals(that.errorCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(error, errorCode);
  }

  @Override
  public String toString() {
    return "ErrorResponse{" + "error='" + error + '\'' + ", errorCode='" + errorCode + '\'' + '}';
  }
}
