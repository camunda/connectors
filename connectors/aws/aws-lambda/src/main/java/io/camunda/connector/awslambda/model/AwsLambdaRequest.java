/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.awslambda.model;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class AwsLambdaRequest extends AwsBaseRequest {

  @Valid @NotNull @Secret private FunctionRequestData awsFunction;

  public FunctionRequestData getAwsFunction() {
    return awsFunction;
  }

  public void setAwsFunction(final FunctionRequestData awsFunction) {
    this.awsFunction = awsFunction;
  }

  @Override
  public String toString() {
    return "AwsLambdaRequest{" + "awsFunction=" + awsFunction + "} " + super.toString();
  }
}
