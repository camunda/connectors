/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker.model;

import com.amazonaws.services.sagemakerruntime.model.InvokeEndpointResult;
import java.nio.charset.StandardCharsets;

public record SageMakerSyncResponse(
    String body, String contentType, String customAttributes, String invokedProductionVariant) {

  public SageMakerSyncResponse(InvokeEndpointResult result) {
    this(
        StandardCharsets.UTF_8.decode(result.getBody()).toString(),
        result.getContentType(),
        result.getCustomAttributes(),
        result.getInvokedProductionVariant());
  }
}
