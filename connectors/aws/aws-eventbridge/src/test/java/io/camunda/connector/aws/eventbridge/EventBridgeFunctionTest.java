/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.eventbridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.DefaultAwsResponseMetadata;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;

@ExtendWith(MockitoExtension.class)
class EventBridgeFunctionTest {

  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/request/success-cases-eventbridge.json";

  private static final String VALIDATION_TEST_CASES_RESOURCE_PATH =
      "src/test/resources/request/validation-test-cases.json";

  private static final String ACCESS_KEY = "my-aws-access-key";
  private static final String SECRET_KEY = "my-aws-secret-key";
  private static final String REGION = "ua-east-1";
  private static final String EVENT_BUS_NAME = "event-bus-name";
  private static final String SOURCE = "event-source";
  private static final String DETAIL_TYPE = "detail-type";
  private static final String INNER_DETAIL_VALUE = "innerValue";

  private static final String SECRETS_ACCESS_KEY = "ACCESS_KEY";
  private static final String SECRETS_SECRET_KEY = "SECRET_KEY";
  private static final String SECRETS_REGION = "REGION";
  private static final String SECRETS_EVENT_BUS_NAME = "EVENT_BUS_NAME";
  private static final String SECRETS_SOURCE = "SOURCE";
  private static final String SECRETS_DETAIL_TYPE = "DETAIL_TYPE";
  private static final String SECRETS_INNER_DETAIL_VALUE = "INNER_DETAIL_VALUE";

  private static final String DETAIL_AS_STRING = "{\"key1\":{\"innerKey\":\"innerValue\"}}";
  private static final String EVENT_ID = "0dcd8361-d287-b9eb-bbb2-a2512851c2bf";
  private static final String REQUEST_ID = "929bf054-193b-48e6-ab80-3aeeb613b415";

  private EventBridgeFunction function;
  @Mock private AwsEventBridgeClientSupplier clientSupplier;
  @Mock private EventBridgeClient client;
  @Captor private ArgumentCaptor<AwsCredentialsProvider> credentialsProviderArgumentCaptor;
  @Captor private ArgumentCaptor<PutEventsRequest> putEventsRequestArgumentCaptor;
  private ObjectMapper objectMapper;

  @BeforeEach
  public void init() {
    objectMapper = ObjectMapperSupplier.getMapperInstance();
    function = new EventBridgeFunction(clientSupplier, objectMapper);
  }

  @ParameterizedTest(name = "execute connector with valid data")
  @MethodSource("successCases")
  public void execute_shouldExecuteRequest(String input) throws JsonProcessingException {
    // Given valid data
    OutboundConnectorContext context =
        getContextBuilderWithSecrets()
            .variables(input)
            .validation(new DefaultValidationProvider())
            .build();
    when(clientSupplier.getAmazonEventBridgeClient(
            credentialsProviderArgumentCaptor.capture(), eq(REGION)))
        .thenReturn(client);
    when(client.putEvents(putEventsRequestArgumentCaptor.capture()))
        .thenReturn(
            PutEventsResponse.builder()
                .failedEntryCount(0)
                .entries(PutEventsResultEntry.builder().eventId(EVENT_ID).build())
                .build());
    // When connector execute
    Object execute = function.execute(context);
    // Then the result carries the actual PutEvents outcome (regression guard: failedEntryCount and
    // the per-entry eventId must survive the v2 -> connector-result mapping).
    assertThat(execute).isInstanceOf(EventBridgeResult.class);
    EventBridgeResult result = (EventBridgeResult) execute;
    assertThat(result.failedEntryCount()).isZero();
    assertThat(result.entries()).hasSize(1);
    EventBridgeResult.Entry resultEntry = result.entries().get(0);
    assertThat(resultEntry.eventId()).isEqualTo(EVENT_ID);
    assertThat(resultEntry.errorCode()).isNull();
    assertThat(resultEntry.errorMessage()).isNull();

    AwsCredentials credentials = credentialsProviderArgumentCaptor.getValue().resolveCredentials();
    assertThat(credentials.accessKeyId()).isEqualTo(ACCESS_KEY);
    assertThat(credentials.secretAccessKey()).isEqualTo(SECRET_KEY);

    var request = context.bindVariables(AwsEventBridgeRequest.class);

    PutEventsRequestEntry entry = putEventsRequestArgumentCaptor.getValue().entries().get(0);
    assertThat(entry.detail())
        .isEqualTo(
            ObjectMapperSupplier.getMapperInstance()
                .writeValueAsString(request.getInput().getDetail()));
    assertThat(entry.eventBusName()).isEqualTo(EVENT_BUS_NAME);
    assertThat(entry.source()).isEqualTo(SOURCE);
    assertThat(entry.detailType()).isEqualTo(DETAIL_TYPE);

    assertThat(entry.detail()).isEqualTo(DETAIL_AS_STRING);
  }

  /**
   * Golden-JSON shape test: the connector result, serialized with the real connectors {@link
   * ObjectMapper}, must reproduce the output contract the connector documented before the AWS SDK
   * v2 migration (see README). This is the template for verifying result serialization across the
   * v2 migration effort: build a realistic v2 SDK response, map it through the connector, serialize
   * with the production mapper, and diff the full JSON tree against the frozen expectation.
   */
  @Test
  public void putEventsResult_serializesToDocumentedV1JsonShape() throws JsonProcessingException {
    // Given a fully populated AWS SDK v2 PutEventsResponse, as returned by a live PutEvents call:
    // one delivered entry (eventId), one failed entry (errorCode/errorMessage), a partial-failure
    // count, request metadata and HTTP metadata.
    var responseBuilder =
        PutEventsResponse.builder()
            .failedEntryCount(1)
            .entries(
                PutEventsResultEntry.builder().eventId(EVENT_ID).build(),
                PutEventsResultEntry.builder()
                    .errorCode("InternalFailure")
                    .errorMessage("Internal Service Failure")
                    .build());
    responseBuilder.responseMetadata(
        DefaultAwsResponseMetadata.create(Map.of("AWS_REQUEST_ID", REQUEST_ID)));
    responseBuilder.sdkHttpResponse(
        SdkHttpResponse.builder()
            .statusCode(200)
            .putHeader("Content-Length", "85")
            .putHeader("Content-Type", "application/x-amz-json-1.1")
            .putHeader("x-amzn-RequestId", REQUEST_ID)
            .build());
    PutEventsResponse response = responseBuilder.build();

    // When the connector maps it and the runtime serializes the result with the production mapper
    EventBridgeResult result = EventBridgeResult.from(response);
    ObjectMapper productionMapper = ConnectorsObjectMapperSupplier.getCopy();
    JsonNode actual = productionMapper.valueToTree(result);

    // Then the JSON matches the pre-v2 (AWS SDK v1) output shape exactly, including explicit nulls.
    String expectedJson =
        """
        {
          "sdkResponseMetadata": {
            "requestId": "929bf054-193b-48e6-ab80-3aeeb613b415"
          },
          "sdkHttpMetadata": {
            "httpHeaders": {
              "Content-Length": "85",
              "Content-Type": "application/x-amz-json-1.1",
              "x-amzn-RequestId": "929bf054-193b-48e6-ab80-3aeeb613b415"
            },
            "httpStatusCode": 200,
            "allHttpHeaders": {
              "Content-Length": ["85"],
              "Content-Type": ["application/x-amz-json-1.1"],
              "x-amzn-RequestId": ["929bf054-193b-48e6-ab80-3aeeb613b415"]
            }
          },
          "failedEntryCount": 1,
          "entries": [
            {
              "eventId": "0dcd8361-d287-b9eb-bbb2-a2512851c2bf",
              "errorCode": null,
              "errorMessage": null
            },
            {
              "eventId": null,
              "errorCode": "InternalFailure",
              "errorMessage": "Internal Service Failure"
            }
          ]
        }
        """;
    JsonNode expected = productionMapper.readTree(expectedJson);
    assertThat(actual).isEqualTo(expected);
    // Tree equality above is key-order-insensitive; also pin the serialized field order
    // (@JsonPropertyOrder) to the documented v1 layout.
    assertThat(productionMapper.writeValueAsString(result))
        .isEqualTo(productionMapper.writeValueAsString(expected));
  }

  /**
   * v2 falls back to the literal request id {@code "UNKNOWN"} when {@code AWS_REQUEST_ID} is absent
   * from the response metadata map; v1 exposed a {@code null} {@code requestId} in that case, with
   * the {@code sdkResponseMetadata} object still present. Guards against collapsing the whole
   * object to {@code null} instead of normalizing just the {@code requestId} field.
   */
  @Test
  public void putEventsResult_normalizesUnknownRequestIdWithoutDroppingMetadataObject() {
    var responseBuilder =
        PutEventsResponse.builder()
            .entries(PutEventsResultEntry.builder().eventId(EVENT_ID).build());
    responseBuilder.responseMetadata(DefaultAwsResponseMetadata.create(Map.of()));

    EventBridgeResult result = EventBridgeResult.from(responseBuilder.build());

    assertThat(result.sdkResponseMetadata()).isNotNull();
    assertThat(result.sdkResponseMetadata().requestId()).isNull();
  }

  @ParameterizedTest()
  @MethodSource("validationTestCases")
  public void execute_shouldThrowExceptionWhenDataNotValid(String input) {
    // Given invalid data (without all required fields)
    OutboundConnectorContext context =
        getContextBuilderWithSecrets()
            .variables(input)
            .validation(new DefaultValidationProvider())
            .build();
    // When connector execute
    // Then throw ConnectorInputException
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> function.execute(context),
            "ConnectorInputException was expected");
    // Then we except exception with message
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input:");
  }

  private OutboundConnectorContextBuilder getContextBuilderWithSecrets() {
    return OutboundConnectorContextBuilder.create()
        .secret(SECRETS_ACCESS_KEY, ACCESS_KEY)
        .secret(SECRETS_SECRET_KEY, SECRET_KEY)
        .secret(SECRETS_REGION, REGION)
        .secret(SECRETS_EVENT_BUS_NAME, EVENT_BUS_NAME)
        .secret(SECRETS_SOURCE, SOURCE)
        .secret(SECRETS_DETAIL_TYPE, DETAIL_TYPE)
        .secret(SECRETS_INNER_DETAIL_VALUE, INNER_DETAIL_VALUE);
  }

  private static Stream<String> successCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_CASES_RESOURCE_PATH);
  }

  private static Stream<String> validationTestCases() throws IOException {
    return loadTestCasesFromResourceFile(VALIDATION_TEST_CASES_RESOURCE_PATH);
  }

  protected static Stream<String> loadTestCasesFromResourceFile(final String fileWithTestCasesUri)
      throws IOException {
    final ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper
        .readValue(new File(fileWithTestCasesUri), new TypeReference<List<JsonNode>>() {})
        .stream()
        .map(JsonNode::toString)
        .peek(System.out::println);
  }
}
