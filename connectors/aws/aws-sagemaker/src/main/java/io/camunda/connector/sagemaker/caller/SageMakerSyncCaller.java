/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker.caller;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.sagemaker.model.SageMakerEnableExplanations;
import io.camunda.connector.sagemaker.model.SageMakerRequest;
import io.camunda.connector.sagemaker.model.SageMakerSyncResponse;
import java.nio.ByteBuffer;
import java.util.function.BiFunction;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;

public final class SageMakerSyncCaller {

  public static final BiFunction<SageMakerRuntimeClient, SageMakerRequest, SageMakerSyncResponse>
      SYNC_REQUEST =
          (runtime, request) -> {
            try {
              InvokeEndpointRequest invokeEndpointRequest = InvokeEndpointRequest.builder()
                  .build();
              invokeEndpointRequest = invokeEndpointRequest.toBuilder().endpointName(request.getInput().endpointName()).build();
              invokeEndpointRequest = invokeEndpointRequest.toBuilder().body(ByteBuffer.wrap(
                  ObjectMapperSupplier.getMapperInstance()
                      .writeValueAsBytes(request.getInput().body()))).build();
              invokeEndpointRequest = invokeEndpointRequest.toBuilder().contentType(request.getInput().contentType()).build();
              invokeEndpointRequest = invokeEndpointRequest.toBuilder().accept(request.getInput().accept()).build();
              invokeEndpointRequest = invokeEndpointRequest.toBuilder().customAttributes(request.getInput().customAttributes()).build();
              invokeEndpointRequest = invokeEndpointRequest.toBuilder().targetModel(request.getInput().targetModel()).build();
              invokeEndpointRequest = invokeEndpointRequest.toBuilder().targetVariant(request.getInput().targetVariant()).build();

              invokeEndpointRequest = invokeEndpointRequest.toBuilder().targetContainerHostname(request.getInput().targetContainerHostname()).build();
              invokeEndpointRequest = invokeEndpointRequest.toBuilder().inferenceId(request.getInput().inferenceId()).build();

              invokeEndpointRequest = invokeEndpointRequest.toBuilder().enableExplanations(request.getInput().enableExplanations() == SageMakerEnableExplanations.NOT_SET
                  ? null
                  : request.getInput().enableExplanations() == SageMakerEnableExplanations.YES
                  ? "1"
                  : "0").build();

              invokeEndpointRequest = invokeEndpointRequest.toBuilder().inferenceComponentName(request.getInput().inferenceComponentName()).build();

              var result = runtime.invokeEndpoint(invokeEndpointRequest);
              return new SageMakerSyncResponse(result);
            } catch (Exception e) {
              throw new ConnectorException(e);
            }
          };
}
