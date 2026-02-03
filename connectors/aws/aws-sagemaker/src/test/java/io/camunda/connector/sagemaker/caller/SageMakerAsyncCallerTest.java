/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.sagemaker.caller;

import static io.camunda.connector.sagemaker.testutils.SageMakerTestUtils.ASYNC_EXECUTION_JSON;
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
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeAsyncClient;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointAsyncRequest;
import software.amazon.awssdk.services.sagemakerruntime.model.InvokeEndpointAsyncResponse;

class SageMakerAsyncCallerTest {

  @Test
  void sageMakerAsyncCaller_HappyCase() throws JsonProcessingException {
    var runtime = mock(SageMakerRuntimeAsyncClient.class);
    var mockedAwsCall = InvokeEndpointAsyncResponse.builder()
        .build();
    mockedAwsCall = mockedAwsCall.toBuilder().outputLocation("s3://result-bucket/result-object").build();
    mockedAwsCall = mockedAwsCall.toBuilder().inferenceId("inference01").build();
    mockedAwsCall = mockedAwsCall.toBuilder().failureLocation("s3://result-bucket/failures-object").build();
    when(runtime.invokeEndpointAsync(any(InvokeEndpointAsyncRequest.class)))
        .thenReturn(mockedAwsCall);
    var captor = ArgumentCaptor.forClass(InvokeEndpointAsyncRequest.class);
    var request =
        ObjectMapperSupplier.getMapperInstance()
            .readValue(ASYNC_EXECUTION_JSON, SageMakerRequest.class);

    SageMakerAsyncCaller.ASYNC_CALLER.apply(runtime, request);
    verify(runtime).invokeEndpointAsync(captor.capture());

    var mappedRequest = captor.getValue();
    assertThat(mappedRequest.endpointName()).isEqualTo(request.getInput().endpointName());
    assertThat(mappedRequest.contentType()).isEqualTo(request.getInput().contentType());
    assertThat(mappedRequest.endpointName()).isEqualTo(request.getInput().endpointName());
    assertThat(mappedRequest.accept()).isEqualTo(request.getInput().accept());
    assertThat(mappedRequest.customAttributes())
        .isEqualTo(request.getInput().customAttributes());
    assertThat(mappedRequest.inferenceId()).isEqualTo(request.getInput().inferenceId());
    assertThat(mappedRequest.inputLocation()).isEqualTo(request.getInput().inputLocation());
    assertThat(mappedRequest.invocationTimeoutSeconds())
        .isEqualTo(Integer.parseInt(request.getInput().invocationTimeoutSeconds()));
    assertThat(mappedRequest.requestTtlSeconds())
        .isEqualTo(Integer.parseInt(request.getInput().requestTTLSeconds()));
  }

  @Test
  void sageMakerAsyncCaller_ExceptionalCase() throws JsonProcessingException {
    var runtime = mock(SageMakerRuntimeAsyncClient.class);
    var request =
        ObjectMapperSupplier.getMapperInstance()
            .readValue(ASYNC_EXECUTION_JSON, SageMakerRequest.class);
    when(runtime.invokeEndpointAsync(any(InvokeEndpointAsyncRequest.class)))
        .thenThrow(new RuntimeException("Something went terribly wrong!"));
    Assertions.assertThrows(
        ConnectorException.class, () -> SageMakerAsyncCaller.ASYNC_CALLER.apply(runtime, request));
  }
}
