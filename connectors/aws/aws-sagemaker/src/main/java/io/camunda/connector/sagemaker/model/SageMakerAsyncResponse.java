/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointAsyncResponse;

@JsonPropertyOrder({"outputLocation", "failureLocation", "inferenceId"})
public record SageMakerAsyncResponse(
    String outputLocation, String failureLocation, String inferenceId) {

  public SageMakerAsyncResponse(InvokeEndpointAsyncResponse result) {
    this(result.outputLocation(), result.failureLocation(), result.inferenceId());
  }
}
