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
import java.util.function.BiFunction;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;

public final class SageMakerSyncCaller {

  public static final BiFunction<SageMakerRuntimeClient, SageMakerRequest, SageMakerSyncResponse>
      SYNC_REQUEST =
          (runtime, request) -> {
            try {
              InvokeEndpointRequest invokeEndpointRequest =
                  InvokeEndpointRequest.builder()
                      .endpointName(request.getInput().endpointName())
                      .body(
                          SdkBytes.fromByteArray(
                              ObjectMapperSupplier.getMapperInstance()
                                  .writeValueAsBytes(request.getInput().body())))
                      .contentType(request.getInput().contentType())
                      .accept(request.getInput().accept())
                      .customAttributes(request.getInput().customAttributes())
                      .targetModel(request.getInput().targetModel())
                      .targetVariant(request.getInput().targetVariant())
                      .targetContainerHostname(request.getInput().targetContainerHostname())
                      .inferenceId(request.getInput().inferenceId())
                      .enableExplanations(
                          request.getInput().enableExplanations()
                                  == SageMakerEnableExplanations.NOT_SET
                              ? null
                              : request.getInput().enableExplanations()
                                      == SageMakerEnableExplanations.YES
                                  ? "1"
                                  : "0")
                      .inferenceComponentName(request.getInput().inferenceComponentName())
                      .build();

              var result = runtime.invokeEndpoint(invokeEndpointRequest);
              return new SageMakerSyncResponse(result);
            } catch (Exception e) {
              throw new ConnectorException(e);
            }
          };
}
