/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker.model;

import com.amazonaws.services.sagemakerruntime.model.InvokeEndpointAsyncResult;

public record SageMakerAsyncResponse(
    String outputLocation, String failureLocation, String inferenceId) {

  public SageMakerAsyncResponse(InvokeEndpointAsyncResult result) {
    this(result.getOutputLocation(), result.getFailureLocation(), result.getInferenceId());
  }
}
