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

import com.amazonaws.services.sagemakerruntime.AmazonSageMakerRuntimeAsync;
import com.amazonaws.services.sagemakerruntime.model.InvokeEndpointAsyncRequest;
import com.amazonaws.services.sagemakerruntime.model.InvokeEndpointAsyncResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.sagemaker.model.SageMakerRequest;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SageMakerAsyncCallerTest {

  @Test
  void sageMakerAsyncCaller_HappyCase() throws JsonProcessingException {
    var runtime = mock(AmazonSageMakerRuntimeAsync.class);
    var mockedAwsCall = new InvokeEndpointAsyncResult();
    mockedAwsCall.setOutputLocation("s3://result-bucket/result-object");
    mockedAwsCall.setInferenceId("inference01");
    mockedAwsCall.setFailureLocation("s3://result-bucket/failures-object");
    when(runtime.invokeEndpointAsync(any(InvokeEndpointAsyncRequest.class)))
        .thenReturn(mockedAwsCall);
    var captor = ArgumentCaptor.forClass(InvokeEndpointAsyncRequest.class);
    var request =
        ObjectMapperSupplier.getMapperInstance()
            .readValue(ASYNC_EXECUTION_JSON, SageMakerRequest.class);

    SageMakerAsyncCaller.ASYNC_CALLER.apply(runtime, request);
    verify(runtime).invokeEndpointAsync(captor.capture());

    var mappedRequest = captor.getValue();
    assertThat(mappedRequest.getEndpointName()).isEqualTo(request.getInput().endpointName());
    assertThat(mappedRequest.getContentType()).isEqualTo(request.getInput().contentType());
    assertThat(mappedRequest.getEndpointName()).isEqualTo(request.getInput().endpointName());
    assertThat(mappedRequest.getAccept()).isEqualTo(request.getInput().accept());
    assertThat(mappedRequest.getCustomAttributes())
        .isEqualTo(request.getInput().customAttributes());
    assertThat(mappedRequest.getInferenceId()).isEqualTo(request.getInput().inferenceId());
    assertThat(mappedRequest.getInputLocation()).isEqualTo(request.getInput().inputLocation());
    assertThat(mappedRequest.getInvocationTimeoutSeconds())
        .isEqualTo(Integer.parseInt(request.getInput().invocationTimeoutSeconds()));
    assertThat(mappedRequest.getRequestTTLSeconds())
        .isEqualTo(Integer.parseInt(request.getInput().requestTTLSeconds()));
  }

  @Test
  void sageMakerAsyncCaller_ExceptionalCase() throws JsonProcessingException {
    var runtime = mock(AmazonSageMakerRuntimeAsync.class);
    var request =
        ObjectMapperSupplier.getMapperInstance()
            .readValue(ASYNC_EXECUTION_JSON, SageMakerRequest.class);
    when(runtime.invokeEndpointAsync(any(InvokeEndpointAsyncRequest.class)))
        .thenThrow(new RuntimeException("Something went terribly wrong!"));
    Assert.assertThrows(
        ConnectorException.class, () -> SageMakerAsyncCaller.ASYNC_CALLER.apply(runtime, request));
  }
}
