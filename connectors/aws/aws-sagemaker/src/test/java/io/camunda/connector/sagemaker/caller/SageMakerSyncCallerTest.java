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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.sagemaker.model.SageMakerRequest;
import io.camunda.connector.sagemaker.model.SageMakerSyncResponse;
import java.nio.charset.StandardCharsets;
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
    var mockedAwsCall =
        InvokeEndpointResponse.builder()
            .body(
                SdkBytes.fromByteArray(
                    ObjectMapperSupplier.getMapperInstance()
                        .writeValueAsBytes("{\"generated_text\": \"the answer is 42\"}")))
            .contentType("application/json")
            .customAttributes("my-custom-attribute")
            .invokedProductionVariant("variant01")
            .build();
    when(runtime.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(mockedAwsCall);
    var captor = ArgumentCaptor.forClass(InvokeEndpointRequest.class);
    var request =
        ObjectMapperSupplier.getMapperInstance()
            .readValue(REAL_TIME_EXECUTION_JSON, SageMakerRequest.class);

    SageMakerSyncCaller.SYNC_REQUEST.apply(runtime, request);
    verify(runtime).invokeEndpoint(captor.capture());

    var mappedRequest = captor.getValue();
    assertThat(mappedRequest.endpointName()).isEqualTo(request.getInput().endpointName());
    assertThat(mappedRequest.body())
        .isEqualTo(
            SdkBytes.fromByteArray(
                ObjectMapperSupplier.getMapperInstance()
                    .writeValueAsBytes(request.getInput().body())));
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

  /**
   * Golden-JSON shape test: the connector result, serialized with the real connectors {@link
   * ObjectMapper}, must reproduce the JSON shape the sync (real-time) invocation path documented
   * and returned before the AWS SDK v2 migration. This is the template for verifying result
   * serialization across the v2 migration effort for aws-sagemaker: build a realistic AWS SDK v2
   * {@link InvokeEndpointResponse} (as a live InvokeEndpoint call would populate it), map it
   * through the real caller, serialize with the production mapper, and diff the full JSON tree
   * against the frozen expectation.
   */
  @Test
  public void invokeEndpointResult_jsonBody_serializesToDocumentedV1JsonShape()
      throws JsonProcessingException {
    // Given a fully populated InvokeEndpointResponse with a JSON inference payload, as returned by
    // a live InvokeEndpoint call against an "application/json" endpoint.
    var runtime = mock(SageMakerRuntimeClient.class);
    String jsonBody =
        """
        {"predictions":[{"label":"POSITIVE","score":0.9821},{"label":"NEGATIVE","score":0.0179}],"modelId":"text-classification-v3"}""";
    var mockedAwsCall =
        InvokeEndpointResponse.builder()
            .body(SdkBytes.fromByteArray(jsonBody.getBytes(StandardCharsets.UTF_8)))
            .contentType("application/json")
            .customAttributes("tenant-id=42;request-source=order-service")
            .invokedProductionVariant("AllTraffic")
            .build();
    when(runtime.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(mockedAwsCall);
    var request =
        ObjectMapperSupplier.getMapperInstance()
            .readValue(REAL_TIME_EXECUTION_JSON, SageMakerRequest.class);

    // When the connector maps it through the real caller
    Object mapped = SageMakerSyncCaller.SYNC_REQUEST.apply(runtime, request);

    assertThat(mapped).isInstanceOf(SageMakerSyncResponse.class);
    ObjectMapper productionMapper = ConnectorsObjectMapperSupplier.getCopy();
    JsonNode actual = productionMapper.valueToTree(mapped);

    // Then the JSON matches the pre-v2 (SDK v1) sync-invocation output shape exactly: an
    // "application/json" body is parsed into a JSON object (not left as a string / base64) --
    // intentional v1 behavior being pinned for the migration. Also note sdkResponseMetadata /
    // sdkHttpMetadata are ABSENT: SageMakerSyncResponse only maps
    // body/contentType/customAttributes/invokedProductionVariant, so any AWS request/HTTP
    // metadata the SDK result carries is silently dropped today.
    String expectedJson =
        """
        {
          "body": {
            "predictions": [
              {"label": "POSITIVE", "score": 0.9821},
              {"label": "NEGATIVE", "score": 0.0179}
            ],
            "modelId": "text-classification-v3"
          },
          "contentType": "application/json",
          "customAttributes": "tenant-id=42;request-source=order-service",
          "invokedProductionVariant": "AllTraffic"
        }
        """;
    JsonNode expected = productionMapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);
    // Tree equality above is key-order-insensitive; also pin the serialized field order to the
    // documented v1 layout.
    assertThat(productionMapper.writeValueAsString(mapped))
        .isEqualTo(productionMapper.writeValueAsString(expected));
  }

  /**
   * Edge-case golden-JSON test: when the response content type is not "application/json",
   * mapResponseBody does not attempt to parse the body at all -- it just UTF-8-decodes the raw
   * bytes into a plain string. Pin that distinction so the v2 migration doesn't accidentally start
   * parsing (or base64-encoding) non-JSON payloads.
   */
  @Test
  public void invokeEndpointResult_nonJsonBody_serializesAsPlainStringNotParsed()
      throws JsonProcessingException {
    // Given a live InvokeEndpoint call against a non-JSON ("text/plain") endpoint.
    var runtime = mock(SageMakerRuntimeClient.class);
    String textBody = "prediction: positive (0.98)";
    var mockedAwsCall =
        InvokeEndpointResponse.builder()
            .body(SdkBytes.fromByteArray(textBody.getBytes(StandardCharsets.UTF_8)))
            .contentType("text/plain")
            .invokedProductionVariant("AllTraffic")
            .build();
    // customAttributes intentionally left unset -- the production mapper must serialize it as
    // an explicit null below: the mapper's default inclusion policy serializes null record
    // components rather than omitting them (no NON_NULL/NON_ABSENT inclusion is configured).
    when(runtime.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(mockedAwsCall);
    var request =
        ObjectMapperSupplier.getMapperInstance()
            .readValue(REAL_TIME_EXECUTION_JSON, SageMakerRequest.class);

    Object mapped = SageMakerSyncCaller.SYNC_REQUEST.apply(runtime, request);

    ObjectMapper productionMapper = ConnectorsObjectMapperSupplier.getCopy();
    JsonNode actual = productionMapper.valueToTree(mapped);
    String expectedJson =
        """
        {
          "body": "prediction: positive (0.98)",
          "contentType": "text/plain",
          "customAttributes": null,
          "invokedProductionVariant": "AllTraffic"
        }
        """;
    JsonNode expected = productionMapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);
    assertThat(productionMapper.writeValueAsString(mapped))
        .isEqualTo(productionMapper.writeValueAsString(expected));
  }

  /**
   * Edge-case regression guard: an "application/json" response with an EMPTY body cannot be parsed
   * as JSON. Today that surfaces as a {@link ConnectorException} wrapping the read failure (the
   * caller's catch-all wraps the {@link RuntimeException} thrown while constructing {@link
   * SageMakerSyncResponse}) rather than an empty/null result. This is intentional (if surprising)
   * v1 behavior being pinned for the migration: a non-JSON empty body would NOT throw (see {@link
   * #invokeEndpointResult_nonJsonBody_serializesAsPlainStringNotParsed}), so the mapping does
   * distinguish "empty" from "non-JSON".
   */
  @Test
  public void invokeEndpointResult_emptyBodyWithJsonContentType_throwsConnectorException()
      throws JsonProcessingException {
    var runtime = mock(SageMakerRuntimeClient.class);
    var mockedAwsCall =
        InvokeEndpointResponse.builder()
            .body(SdkBytes.fromByteArray(new byte[0]))
            .contentType("application/json")
            .build();
    when(runtime.invokeEndpoint(any(InvokeEndpointRequest.class))).thenReturn(mockedAwsCall);
    var request =
        ObjectMapperSupplier.getMapperInstance()
            .readValue(REAL_TIME_EXECUTION_JSON, SageMakerRequest.class);

    ConnectorException thrown =
        Assertions.assertThrows(
            ConnectorException.class,
            () -> SageMakerSyncCaller.SYNC_REQUEST.apply(runtime, request));
    assertThat(thrown.getCause()).hasMessageContaining("Error reading Sagemaker response.");
  }
}
