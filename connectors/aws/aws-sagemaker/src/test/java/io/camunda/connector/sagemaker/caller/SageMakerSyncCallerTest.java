/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker.caller;

import static io.camunda.connector.sagemaker.testutils.SageMakerTestUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.sagemaker.model.SageMakerRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointResponse;

class SageMakerSyncCallerTest {

  @Test
  void sageMakerSyncCaller_HappyCase() throws JsonProcessingException {
    var runtime = mock(SageMakerRuntimeClient.class);
    var mockedAwsCall = InvokeEndpointResponse.builder().build();
    mockedAwsCall =
        mockedAwsCall.toBuilder()
            .body(
                SdkBytes.fromByteArray(
                    ObjectMapperSupplier.getMapperInstance()
                        .writeValueAsBytes("{\"generated_text\": \"the answer is 42\"}")))
            .build();
    mockedAwsCall = mockedAwsCall.toBuilder().contentType("application/json").build();
    mockedAwsCall = mockedAwsCall.toBuilder().customAttributes("my-custom-attribute").build();
    mockedAwsCall = mockedAwsCall.toBuilder().invokedProductionVariant("variant01").build();
    when(runtime.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(mockedAwsCall);
    var captor = ArgumentCaptor.forClass(InvokeEndpointRequest.class);
    var request =
        ObjectMapperSupplier.getMapperInstance()
            .readValue(REAL_TIME_EXECUTION_JSON, SageMakerRequest.class);

    SageMakerSyncCaller.SYNC_REQUEST.apply(runtime, request);
    verify(runtime).invokeEndpoint(captor.capture());

    var mappedRequest = captor.getValue();
    assertThat(mappedRequest.endpointName()).isEqualTo(request.getInput().endpointName());
    assertThat(mappedRequest.body().asByteArray())
        .isEqualTo(
            ObjectMapperSupplier.getMapperInstance().writeValueAsBytes(request.getInput().body()));
    assertThat(mappedRequest.contentType()).isEqualTo(request.getInput().contentType());
    assertThat(mappedRequest.accept()).isEqualTo(request.getInput().accept());
    assertThat(mappedRequest.customAttributes()).isEqualTo(request.getInput().customAttributes());
    assertThat(mappedRequest.targetModel()).isEqualTo(request.getInput().targetModel());
    assertThat(mappedRequest.targetVariant()).isEqualTo(request.getInput().targetVariant());
    assertThat(mappedRequest.targetContainerHostname())
        .isEqualTo(request.getInput().targetContainerHostname());
    assertThat(mappedRequest.inferenceId()).isEqualTo(request.getInput().inferenceId());
    assertThat(mappedRequest.enableExplanations()).isNull();
    assertThat(mappedRequest.inferenceComponentName())
        .isEqualTo(request.getInput().inferenceComponentName());
  }

  @Test
  void sageMaker_ExceptionCase() throws JsonProcessingException {
    var runtime = mock(SageMakerRuntimeClient.class);
    var request =
        ObjectMapperSupplier.getMapperInstance()
            .readValue(REAL_TIME_EXECUTION_JSON, SageMakerRequest.class);
    when(runtime.invokeEndpoint(any(InvokeEndpointRequest.class)))
        .thenThrow(new RuntimeException("Something went terribly wrong!"));
    Assertions.assertThrows(
        ConnectorException.class, () -> SageMakerSyncCaller.SYNC_REQUEST.apply(runtime, request));
  }
}
