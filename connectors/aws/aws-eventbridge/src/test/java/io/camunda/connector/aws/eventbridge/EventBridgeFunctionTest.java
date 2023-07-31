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

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.eventbridge.AmazonEventBridge;
import com.amazonaws.services.eventbridge.model.PutEventsRequest;
import com.amazonaws.services.eventbridge.model.PutEventsRequestEntry;
import com.amazonaws.services.eventbridge.model.PutEventsResult;
import com.amazonaws.services.eventbridge.model.PutEventsResultEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

  private EventBridgeFunction function;
  @Mock private AwsEventBridgeClientSupplier clientSupplier;
  @Mock private AmazonEventBridge client;
  @Captor private ArgumentCaptor<AWSCredentialsProvider> credentialsProviderArgumentCaptor;
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
        .thenReturn(new PutEventsResult().withEntries(new PutEventsResultEntry()));
    // When connector execute
    Object execute = function.execute(context);
    // Then
    assertThat(execute).isNotNull();
    PutEventsResult putEventsResult = objectMapper.convertValue(execute, PutEventsResult.class);
    assertThat(putEventsResult.getEntries()).isNotNull();

    AWSCredentials credentials = credentialsProviderArgumentCaptor.getValue().getCredentials();
    assertThat(credentials.getAWSAccessKeyId()).isEqualTo(ACCESS_KEY);
    assertThat(credentials.getAWSSecretKey()).isEqualTo(SECRET_KEY);

    var request = context.bindVariables(AwsEventBridgeRequest.class);

    PutEventsRequestEntry entry = putEventsRequestArgumentCaptor.getValue().getEntries().get(0);
    assertThat(entry.getDetail())
        .isEqualTo(
            ObjectMapperSupplier.getMapperInstance()
                .writeValueAsString(request.getInput().getDetail()));
    assertThat(entry.getEventBusName()).isEqualTo(EVENT_BUS_NAME);
    assertThat(entry.getSource()).isEqualTo(SOURCE);
    assertThat(entry.getDetailType()).isEqualTo(DETAIL_TYPE);

    assertThat(entry.getDetail()).isEqualTo(DETAIL_AS_STRING);
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
