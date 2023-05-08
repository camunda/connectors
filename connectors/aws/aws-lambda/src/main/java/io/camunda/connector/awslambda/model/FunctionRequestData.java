/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.awslambda.model;

import io.camunda.connector.api.annotation.Secret;
import java.util.Objects;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class FunctionRequestData {

  @NotEmpty @Secret private String functionName;
  @NotNull private Object payload;
  private OperationType operationType; // this is not use and not implemented yet

  public String getFunctionName() {
    return functionName;
  }

  public void setFunctionName(final String functionName) {
    this.functionName = functionName;
  }

  public Object getPayload() {
    return payload;
  }

  public void setPayload(final Object payload) {
    this.payload = payload;
  }

  public OperationType getOperationType() {
    return operationType;
  }

  public void setOperationType(final OperationType operationType) {
    this.operationType = operationType;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FunctionRequestData that = (FunctionRequestData) o;
    return Objects.equals(functionName, that.functionName)
        && Objects.equals(payload, that.payload)
        && operationType == that.operationType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(functionName, payload, operationType);
  }

  @Override
  public String toString() {
    return "FunctionRequestData{functionName=[REDACTED], "
        + "payload=[REDACTED]}, "
        + "operationType="
        + operationType
        + "}";
  }
}
