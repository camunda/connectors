/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker.model;

import com.amazonaws.services.sagemakerruntime.model.InvokeEndpointResult;
import io.camunda.connector.aws.ObjectMapperSupplier;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record SageMakerSyncResponse(
    Object body, String contentType, String customAttributes, String invokedProductionVariant) {

  public SageMakerSyncResponse(InvokeEndpointResult result) {
    this(
        mapResponseBody(result),
        result.getContentType(),
        result.getCustomAttributes(),
        result.getInvokedProductionVariant());
  }

  /* Maps the response body to the appropriate object type
  / https://docs.aws.amazon.com/sagemaker/latest/dg/clarify-online-explainability-invoke-endpoint.html#clarify-online-explainability-response
  */
  private static Object mapResponseBody(InvokeEndpointResult result) {
    if (result.getContentType().equals("application/json")) {
      return parseJsonResponseBody(result.getBody());
    }
    return StandardCharsets.UTF_8.decode(result.getBody()).toString();
  }

  private static Object parseJsonResponseBody(ByteBuffer json) {
    try {
      return ObjectMapperSupplier.getMapperInstance().readValue(json.array(), Object.class);
    } catch (IOException e) {
      throw new RuntimeException("Error reading Sagemaker response.", e);
    }
  }
}
