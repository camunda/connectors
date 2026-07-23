/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.camunda.connector.aws.ObjectMapperSupplier;
import java.io.IOException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointResponse;

@JsonPropertyOrder({"body", "contentType", "customAttributes", "invokedProductionVariant"})
public record SageMakerSyncResponse(
    Object body, String contentType, String customAttributes, String invokedProductionVariant) {

  public SageMakerSyncResponse(InvokeEndpointResponse result) {
    this(
        mapResponseBody(result),
        result.contentType(),
        result.customAttributes(),
        result.invokedProductionVariant());
  }

  /* Maps the response body to the appropriate object type
  / https://docs.aws.amazon.com/sagemaker/latest/dg/clarify-online-explainability-invoke-endpoint.html#clarify-online-explainability-response
  */
  private static Object mapResponseBody(InvokeEndpointResponse result) {
    if (result.contentType().equals("application/json")) {
      return parseJsonResponseBody(result.body());
    }
    return result.body().asUtf8String();
  }

  private static Object parseJsonResponseBody(SdkBytes json) {
    try {
      return ObjectMapperSupplier.getMapperInstance().readValue(json.asByteArray(), Object.class);
    } catch (IOException e) {
      throw new RuntimeException("Error reading Sagemaker response.", e);
    }
  }
}
