/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker.caller;

import com.amazonaws.services.sagemakerruntime.AmazonSageMakerRuntime;
import com.amazonaws.services.sagemakerruntime.model.InvokeEndpointRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.sagemaker.model.SageMakerEnableExplanations;
import io.camunda.connector.sagemaker.model.SageMakerRequest;
import io.camunda.connector.sagemaker.model.SageMakerSyncResponse;
import java.nio.ByteBuffer;
import java.util.function.BiFunction;

public final class SageMakerSyncCaller {

  public static final BiFunction<AmazonSageMakerRuntime, SageMakerRequest, SageMakerSyncResponse>
      SYNC_REQUEST =
          (runtime, request) -> {
            try {
              InvokeEndpointRequest invokeEndpointRequest = new InvokeEndpointRequest();
              invokeEndpointRequest.setEndpointName(request.getInput().endpointName());
              invokeEndpointRequest.setBody(
                  ByteBuffer.wrap(
                      ObjectMapperSupplier.getMapperInstance()
                          .writeValueAsBytes(request.getInput().body())));
              invokeEndpointRequest.setContentType(request.getInput().contentType());
              invokeEndpointRequest.setAccept(request.getInput().accept());
              invokeEndpointRequest.setCustomAttributes(request.getInput().customAttributes());
              invokeEndpointRequest.setTargetModel(request.getInput().targetModel());
              invokeEndpointRequest.setTargetVariant(request.getInput().targetVariant());

              invokeEndpointRequest.setTargetContainerHostname(
                  request.getInput().targetContainerHostname());
              invokeEndpointRequest.setInferenceId(request.getInput().inferenceId());

              invokeEndpointRequest.setEnableExplanations(
                  request.getInput().enableExplanations() == SageMakerEnableExplanations.NOT_SET
                      ? null
                      : request.getInput().enableExplanations() == SageMakerEnableExplanations.YES
                          ? "1"
                          : "0");

              invokeEndpointRequest.setInferenceComponentName(
                  request.getInput().inferenceComponentName());

              var result = runtime.invokeEndpoint(invokeEndpointRequest);
              return new SageMakerSyncResponse(result);
            } catch (Exception e) {
              throw new ConnectorException(e);
            }
          };
}
