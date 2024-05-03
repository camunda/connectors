/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker.caller;

import com.amazonaws.services.sagemakerruntime.AmazonSageMakerRuntimeAsync;
import com.amazonaws.services.sagemakerruntime.model.InvokeEndpointAsyncRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.sagemaker.model.SageMakerAsyncResponse;
import io.camunda.connector.sagemaker.model.SageMakerRequest;
import java.util.function.BiFunction;

public final class SageMakerAsyncCaller {
  public static final BiFunction<
          AmazonSageMakerRuntimeAsync, SageMakerRequest, SageMakerAsyncResponse>
      ASYNC_CALLER =
          (runtime, request) -> {
            try {
              InvokeEndpointAsyncRequest invokeEndpointRequest = new InvokeEndpointAsyncRequest();
              invokeEndpointRequest.setEndpointName(request.getInput().endpointName());
              invokeEndpointRequest.setContentType(request.getInput().contentType());
              invokeEndpointRequest.setAccept(request.getInput().accept());
              invokeEndpointRequest.setCustomAttributes(request.getInput().customAttributes());
              invokeEndpointRequest.setInferenceId(request.getInput().inferenceId());
              invokeEndpointRequest.setInputLocation(request.getInput().inputLocation());
              invokeEndpointRequest.setInvocationTimeoutSeconds(
                  request.getInput().invocationTimeoutSeconds() != null
                      ? Integer.parseInt(request.getInput().invocationTimeoutSeconds())
                      : null);
              invokeEndpointRequest.setRequestTTLSeconds(
                  request.getInput().requestTTLSeconds() != null
                      ? Integer.parseInt(request.getInput().requestTTLSeconds())
                      : null);
              var result = runtime.invokeEndpointAsync(invokeEndpointRequest);
              return new SageMakerAsyncResponse(result);
            } catch (Exception e) {
              throw new ConnectorException(e);
            }
          };
}
