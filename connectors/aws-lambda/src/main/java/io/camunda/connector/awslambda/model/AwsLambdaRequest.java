/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.awslambda.model;

import io.camunda.connector.api.annotation.Secret;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class AwsLambdaRequest {

  @Valid @NotNull @Secret private AuthenticationRequestData authentication;
  @Valid @NotNull @Secret private FunctionRequestData awsFunction;

  public AuthenticationRequestData getAuthentication() {
    return authentication;
  }

  public void setAuthentication(final AuthenticationRequestData authentication) {
    this.authentication = authentication;
  }

  public FunctionRequestData getAwsFunction() {
    return awsFunction;
  }

  public void setAwsFunction(final FunctionRequestData awsFunction) {
    this.awsFunction = awsFunction;
  }

  @Override
  public String toString() {
    return "AwsLambdaRequest{"
        + "authentication="
        + authentication
        + ", function="
        + awsFunction
        + "}";
  }
}
