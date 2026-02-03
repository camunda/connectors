/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker.caller;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.sagemaker.model.SageMakerAsyncResponse;
import io.camunda.connector.sagemaker.model.SageMakerRequest;
import java.util.function.BiFunction;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeAsyncClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointAsyncRequest;

public final class SageMakerAsyncCaller {
  public static final BiFunction<
          SageMakerRuntimeAsyncClient, SageMakerRequest, SageMakerAsyncResponse>
      ASYNC_CALLER =
          (runtime, request) -> {
            try {
              InvokeEndpointAsyncRequest invokeEndpointRequest = InvokeEndpointAsyncRequest.builder()
                  .build();
              invokeEndpointRequest = invokeEndpointRequest.toBuilder().endpointName(request.getInput().endpointName()).build();
              invokeEndpointRequest = invokeEndpointRequest.toBuilder().contentType(request.getInput().contentType()).build();
              invokeEndpointRequest = invokeEndpointRequest.toBuilder().accept(request.getInput().accept()).build();
              invokeEndpointRequest = invokeEndpointRequest.toBuilder().customAttributes(request.getInput().customAttributes()).build();
              invokeEndpointRequest = invokeEndpointRequest.toBuilder().inferenceId(request.getInput().inferenceId()).build();
              invokeEndpointRequest = invokeEndpointRequest.toBuilder().inputLocation(request.getInput().inputLocation()).build();
              invokeEndpointRequest = invokeEndpointRequest.toBuilder().invocationTimeoutSeconds(request.getInput().invocationTimeoutSeconds() != null
                  ? Integer.parseInt(request.getInput().invocationTimeoutSeconds())
                  : null).build();
              invokeEndpointRequest = invokeEndpointRequest.toBuilder().requestTtlSeconds(request.getInput().requestTTLSeconds() != null
                  ? Integer.parseInt(request.getInput().requestTTLSeconds())
                  : null).build();
              var result = runtime.invokeEndpointAsync(invokeEndpointRequest);
              return new SageMakerAsyncResponse(result);
            } catch (Exception e) {
              throw new ConnectorException(e);
            }
          };
}
