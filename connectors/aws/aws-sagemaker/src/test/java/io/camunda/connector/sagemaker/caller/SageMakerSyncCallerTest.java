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

import com.amazonaws.services.sagemakerruntime.AmazonSageMakerRuntime;
import com.amazonaws.services.sagemakerruntime.model.InvokeEndpointRequest;
import com.amazonaws.services.sagemakerruntime.model.InvokeEndpointResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.sagemaker.model.SageMakerRequest;
import java.nio.ByteBuffer;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SageMakerSyncCallerTest {

  @Test
  void sageMakerSyncCaller_HappyCase() throws JsonProcessingException {
    var runtime = mock(AmazonSageMakerRuntime.class);
    var mockedAwsCall = new InvokeEndpointResult();
    mockedAwsCall.setBody(
        ByteBuffer.wrap(
            ObjectMapperSupplier.getMapperInstance()
                .writeValueAsBytes("{\"generated_text\": \"the answer is 42\"}")));
    mockedAwsCall.setContentType("application/json");
    mockedAwsCall.setCustomAttributes("my-custom-attribute");
    mockedAwsCall.setInvokedProductionVariant("variant01");
    when(runtime.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(mockedAwsCall);
    var captor = ArgumentCaptor.forClass(InvokeEndpointRequest.class);
    var request =
        ObjectMapperSupplier.getMapperInstance()
            .readValue(REAL_TIME_EXECUTION_JSON, SageMakerRequest.class);

    SageMakerSyncCaller.SYNC_REQUEST.apply(runtime, request);
    verify(runtime).invokeEndpoint(captor.capture());

    var mappedRequest = captor.getValue();
    assertThat(mappedRequest.getEndpointName()).isEqualTo(request.getInput().endpointName());
    assertThat(mappedRequest.getBody())
        .isEqualTo(
            ByteBuffer.wrap(
                ObjectMapperSupplier.getMapperInstance()
                    .writeValueAsBytes(request.getInput().body())));
    assertThat(mappedRequest.getContentType()).isEqualTo(request.getInput().contentType());
    assertThat(mappedRequest.getAccept()).isEqualTo(request.getInput().accept());
    assertThat(mappedRequest.getCustomAttributes())
        .isEqualTo(request.getInput().customAttributes());
    assertThat(mappedRequest.getTargetModel()).isEqualTo(request.getInput().targetModel());
    assertThat(mappedRequest.getTargetVariant()).isEqualTo(request.getInput().targetVariant());
    assertThat(mappedRequest.getTargetContainerHostname())
        .isEqualTo(request.getInput().targetContainerHostname());
    assertThat(mappedRequest.getInferenceId()).isEqualTo(request.getInput().inferenceId());
    assertThat(mappedRequest.getEnableExplanations()).isNull();
    assertThat(mappedRequest.getInferenceComponentName())
        .isEqualTo(request.getInput().inferenceComponentName());
  }

  @Test
  void sageMaker_ExceptionCase() throws JsonProcessingException {
    var runtime = mock(AmazonSageMakerRuntime.class);
    var request =
        ObjectMapperSupplier.getMapperInstance()
            .readValue(REAL_TIME_EXECUTION_JSON, SageMakerRequest.class);
    when(runtime.invokeEndpoint(any(InvokeEndpointRequest.class)))
        .thenThrow(new RuntimeException("Something went terribly wrong!"));
    Assert.assertThrows(
        ConnectorException.class, () -> SageMakerSyncCaller.SYNC_REQUEST.apply(runtime, request));
  }
}
