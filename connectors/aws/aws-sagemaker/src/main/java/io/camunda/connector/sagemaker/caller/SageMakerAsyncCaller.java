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
              InvokeEndpointAsyncRequest invokeEndpointRequest =
                  InvokeEndpointAsyncRequest.builder()
                      .endpointName(request.getInput().endpointName())
                      .contentType(request.getInput().contentType())
                      .accept(request.getInput().accept())
                      .customAttributes(request.getInput().customAttributes())
                      .inferenceId(request.getInput().inferenceId())
                      .inputLocation(request.getInput().inputLocation())
                      .invocationTimeoutSeconds(
                          request.getInput().invocationTimeoutSeconds() != null
                              ? Integer.parseInt(request.getInput().invocationTimeoutSeconds())
                              : null)
                      .requestTTLSeconds(
                          request.getInput().requestTTLSeconds() != null
                              ? Integer.parseInt(request.getInput().requestTTLSeconds())
                              : null)
                      .build();
              // SageMakerRuntimeAsyncClient is the SDK v2 non-blocking (Future-based) client;
              // .join() blocks for the result so this caller keeps returning a plain
              // SageMakerAsyncResponse, matching the pre-v2 (blocking) behavior.
              var result = runtime.invokeEndpointAsync(invokeEndpointRequest).join();
              return new SageMakerAsyncResponse(result);
            } catch (Exception e) {
              throw new ConnectorException(e);
            }
          };
}
