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

import com.amazonaws.ResponseMetadata;
import com.amazonaws.services.sagemakerruntime.AmazonSageMakerRuntime;
import com.amazonaws.services.sagemakerruntime.model.InvokeEndpointRequest;
import com.amazonaws.services.sagemakerruntime.model.InvokeEndpointResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.sagemaker.model.SageMakerRequest;
import io.camunda.connector.sagemaker.model.SageMakerSyncResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
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
    Assertions.assertThrows(
        ConnectorException.class, () -> SageMakerSyncCaller.SYNC_REQUEST.apply(runtime, request));
  }

  /**
   * Golden-JSON shape test: the connector result, serialized with the real connectors {@link
   * ObjectMapper}, must reproduce the JSON shape the sync (real-time) invocation path documented
   * and returned before the AWS SDK v2 migration. This is the template for verifying result
   * serialization across the v2 migration effort for aws-sagemaker: build a realistic AWS SDK v1
   * {@link InvokeEndpointResult} (as a live InvokeEndpoint call would populate it), map it through
   * the real caller, serialize with the production mapper, and diff the full JSON tree against the
   * frozen expectation.
   */
  @Test
  public void invokeEndpointResult_jsonBody_serializesToDocumentedV1JsonShape()
      throws JsonProcessingException {
    // Given a fully populated InvokeEndpointResult with a JSON inference payload, as returned by a
    // live InvokeEndpoint call against an "application/json" endpoint.
    var runtime = mock(AmazonSageMakerRuntime.class);
    var mockedAwsCall = new InvokeEndpointResult();
    String jsonBody =
        """
        {"predictions":[{"label":"POSITIVE","score":0.9821},{"label":"NEGATIVE","score":0.0179}],"modelId":"text-classification-v3"}""";
    mockedAwsCall.setBody(ByteBuffer.wrap(jsonBody.getBytes(StandardCharsets.UTF_8)));
    mockedAwsCall.setContentType("application/json");
    mockedAwsCall.setCustomAttributes("tenant-id=42;request-source=order-service");
    mockedAwsCall.setInvokedProductionVariant("AllTraffic");
    // A live call also populates request/HTTP metadata inherited from AmazonWebServiceResult; set
    // it here for realism even though the assertion below shows it never reaches the connector
    // result.
    mockedAwsCall.setSdkResponseMetadata(
        new ResponseMetadata(
            Map.of(ResponseMetadata.AWS_REQUEST_ID, "929bf054-193b-48e6-ab80-3aeeb613b415")));
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
    // metadata the SDK result carries (populated above) is silently dropped today.
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
    var runtime = mock(AmazonSageMakerRuntime.class);
    var mockedAwsCall = new InvokeEndpointResult();
    String textBody = "prediction: positive (0.98)";
    mockedAwsCall.setBody(ByteBuffer.wrap(textBody.getBytes(StandardCharsets.UTF_8)));
    mockedAwsCall.setContentType("text/plain");
    mockedAwsCall.setInvokedProductionVariant("AllTraffic");
    // customAttributes intentionally left unset -- the production mapper must serialize it as
    // an explicit null below (unset bean property, FAIL_ON_EMPTY_BEANS disabled).
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
    var runtime = mock(AmazonSageMakerRuntime.class);
    var mockedAwsCall = new InvokeEndpointResult();
    mockedAwsCall.setBody(ByteBuffer.wrap(new byte[0]));
    mockedAwsCall.setContentType("application/json");
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
