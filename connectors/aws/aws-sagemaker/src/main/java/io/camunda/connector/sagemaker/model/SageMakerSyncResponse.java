/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker.model;

import io.camunda.connector.aws.ObjectMapperSupplier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointResponse;

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
    byte[] bodyBytes = result.body().asByteArray();
    if ("application/json".equals(result.contentType())) {
      try {
        return ObjectMapperSupplier.getMapperInstance().readValue(bodyBytes, Object.class);
      } catch (IOException e) {
        throw new RuntimeException("Error reading Sagemaker response.", e);
      }
    }
    return new String(bodyBytes, StandardCharsets.UTF_8);
  }
}
