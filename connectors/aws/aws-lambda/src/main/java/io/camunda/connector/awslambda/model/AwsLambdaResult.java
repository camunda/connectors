/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.awslambda.model;

import com.amazonaws.services.lambda.model.InvokeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public class AwsLambdaResult {

  private Integer statusCode;
  private String executedVersion;
  private Object payload;

  public AwsLambdaResult(final InvokeResult invokeResult, final ObjectMapper objectMapper) {
    this.statusCode = invokeResult.getStatusCode();
    this.executedVersion = invokeResult.getExecutedVersion();
    try {
      this.payload = objectMapper.readValue(invokeResult.getPayload().array(), Object.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Integer getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(final Integer statusCode) {
    this.statusCode = statusCode;
  }

  public String getExecutedVersion() {
    return executedVersion;
  }

  public void setExecutedVersion(final String executedVersion) {
    this.executedVersion = executedVersion;
  }

  public Object getPayload() {
    return payload;
  }

  public void setPayload(final String payload) {
    this.payload = payload;
  }

  @Override
  public String toString() {
    return "AwsLambdaResult{"
        + "statusCode="
        + statusCode
        + ", executedVersion='"
        + executedVersion
        + "'"
        + ", payload='"
        + payload
        + "'"
        + "}";
  }
}
