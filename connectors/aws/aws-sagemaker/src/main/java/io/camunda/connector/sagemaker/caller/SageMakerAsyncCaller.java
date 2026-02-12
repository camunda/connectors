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
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeAsyncClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointAsyncRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointAsyncResponse;

public final class SageMakerAsyncCaller {
  public static final BiFunction<
          SageMakerRuntimeAsyncClient, SageMakerRequest, SageMakerAsyncResponse>
      ASYNC_CALLER =
          (runtime, request) -> {
            try {
              var input = request.getInput();
              InvokeEndpointAsyncRequest.Builder builder =
                  InvokeEndpointAsyncRequest.builder()
                      .endpointName(input.endpointName())
                      .contentType(input.contentType())
                      .accept(input.accept())
                      .customAttributes(input.customAttributes())
                      .inferenceId(input.inferenceId())
                      .inputLocation(input.inputLocation());
              if (input.invocationTimeoutSeconds() != null) {
                builder.invocationTimeoutSeconds(
                    Integer.parseInt(input.invocationTimeoutSeconds()));
              }
              if (input.requestTTLSeconds() != null) {
                builder.requestTTLSeconds(Integer.parseInt(input.requestTTLSeconds()));
              }
              CompletableFuture<InvokeEndpointAsyncResponse> result =
                  runtime.invokeEndpointAsync(builder.build());
              return new SageMakerAsyncResponse(result.join());
            } catch (Exception e) {
              throw new ConnectorException(e);
            }
          };
}
