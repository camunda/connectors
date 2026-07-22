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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.sagemaker.model.SageMakerAsyncResponse;
import io.camunda.connector.sagemaker.model.SageMakerRequest;
import java.util.concurrent.CompletableFuture;
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
    var mockedAwsCall =
        InvokeEndpointAsyncResponse.builder()
            .outputLocation("s3://result-bucket/result-object")
            .inferenceId("inference01")
            .failureLocation("s3://result-bucket/failures-object")
            .build();
    when(runtime.invokeEndpointAsync(any(InvokeEndpointAsyncRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(mockedAwsCall));
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
    assertThat(mappedRequest.customAttributes()).isEqualTo(request.getInput().customAttributes());
    assertThat(mappedRequest.inferenceId()).isEqualTo(request.getInput().inferenceId());
    assertThat(mappedRequest.inputLocation()).isEqualTo(request.getInput().inputLocation());
    assertThat(mappedRequest.invocationTimeoutSeconds())
        .isEqualTo(Integer.parseInt(request.getInput().invocationTimeoutSeconds()));
    assertThat(mappedRequest.requestTTLSeconds())
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

  /**
   * Golden-JSON shape test: the connector result, serialized with the real connectors {@link
   * ObjectMapper}, must reproduce the JSON shape the async invocation path documented and returned
   * before the AWS SDK v2 migration. This is the template for verifying result serialization across
   * the v2 migration effort for aws-sagemaker: build a realistic AWS SDK v2 {@link
   * InvokeEndpointAsyncResponse} (as a live InvokeEndpointAsync call would populate it), map it
   * through the real caller, serialize with the production mapper, and diff the full JSON tree
   * against the frozen expectation.
   */
  @Test
  public void invokeEndpointAsyncResult_serializesToDocumentedV1JsonShape()
      throws JsonProcessingException {
    // Given a fully populated InvokeEndpointAsyncResponse, as returned by a live
    // InvokeEndpointAsync call: output/failure S3 locations and the inference id.
    var runtime = mock(SageMakerRuntimeAsyncClient.class);
    var mockedAwsCall =
        InvokeEndpointAsyncResponse.builder()
            .outputLocation("s3://result-bucket/result-object")
            .failureLocation("s3://result-bucket/failures-object")
            .inferenceId("inference01")
            .build();
    when(runtime.invokeEndpointAsync(any(InvokeEndpointAsyncRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(mockedAwsCall));
    var request =
        ObjectMapperSupplier.getMapperInstance()
            .readValue(ASYNC_EXECUTION_JSON, SageMakerRequest.class);

    // When the connector maps it through the real caller
    Object mapped = SageMakerAsyncCaller.ASYNC_CALLER.apply(runtime, request);

    assertThat(mapped).isInstanceOf(SageMakerAsyncResponse.class);
    ObjectMapper productionMapper = ConnectorsObjectMapperSupplier.getCopy();
    JsonNode actual = productionMapper.valueToTree(mapped);

    // Then the JSON matches the pre-v2 (SDK v1) async-invocation output shape exactly.
    // sdkResponseMetadata/sdkHttpMetadata are ABSENT: SageMakerAsyncResponse only maps
    // outputLocation/failureLocation/inferenceId, so any AWS request/HTTP metadata the SDK result
    // carries is silently dropped today -- intentional v1 behavior being pinned for the migration.
    String expectedJson =
        """
        {
          "outputLocation": "s3://result-bucket/result-object",
          "failureLocation": "s3://result-bucket/failures-object",
          "inferenceId": "inference01"
        }
        """;
    JsonNode expected = productionMapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);
    // Tree equality above is key-order-insensitive; also pin the serialized field order to the
    // documented v1 layout.
    assertThat(productionMapper.writeValueAsString(mapped))
        .isEqualTo(productionMapper.writeValueAsString(expected));
  }
}
