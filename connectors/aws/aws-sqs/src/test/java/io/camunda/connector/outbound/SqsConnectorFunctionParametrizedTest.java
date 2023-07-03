/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.outbound;

import static io.camunda.connector.outbound.BaseTest.ACTUAL_ACCESS_KEY;
import static io.camunda.connector.outbound.BaseTest.ACTUAL_QUEUE_REGION;
import static io.camunda.connector.outbound.BaseTest.ACTUAL_QUEUE_URL;
import static io.camunda.connector.outbound.BaseTest.ACTUAL_SECRET_KEY;
import static io.camunda.connector.outbound.BaseTest.AWS_ACCESS_KEY;
import static io.camunda.connector.outbound.BaseTest.AWS_SECRET_KEY;
import static io.camunda.connector.outbound.BaseTest.MSG_ID;
import static io.camunda.connector.outbound.BaseTest.SQS_QUEUE_URL;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.common.suppliers.AmazonSQSClientSupplier;
import io.camunda.connector.outbound.model.SqsConnectorRequest;
import io.camunda.connector.outbound.model.SqsConnectorResult;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
class SqsConnectorFunctionParametrizedTest {

  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/outbound/success-test-cases.json";

  private static final String FAIL_CASES_RESOURCE_PATH =
      "src/test/resources/requests/outbound/fail-test-cases.json";

  private static final ObjectMapper objectMapper = ObjectMapperSupplier.getMapperInstance();

  @Mock private AmazonSQSClientSupplier sqsClientSupplier;
  @Mock private AmazonSQS sqsClient;
  @Captor private ArgumentCaptor<SendMessageRequest> sendMessageRequest;

  private SqsConnectorFunction function;

  @BeforeEach
  void setup() {
    function = new SqsConnectorFunction(sqsClientSupplier, objectMapper);
  }

  @ParameterizedTest
  @MethodSource("successRequestCases")
  void execute_ShouldSucceedSuccessCases(final String input) throws JsonProcessingException {
    // given
    when(sqsClientSupplier.sqsClient(any(AWSCredentialsProvider.class), eq(ACTUAL_QUEUE_REGION)))
        .thenReturn(sqsClient);
    SendMessageResult sendMessageResult = mock(SendMessageResult.class);
    when(sendMessageResult.getMessageId()).thenReturn(MSG_ID);
    when(sqsClient.sendMessage(sendMessageRequest.capture())).thenReturn(sendMessageResult);
    OutboundConnectorContext ctx =
        OutboundConnectorContextBuilder.create()
            .variables(input)
            .secret(AWS_SECRET_KEY, ACTUAL_SECRET_KEY)
            .secret(AWS_ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(SQS_QUEUE_URL, ACTUAL_QUEUE_URL)
            .build();
    // when
    var request = ctx.bindVariables(SqsConnectorRequest.class);
    Object connectorResultObject = function.execute(ctx);
    SendMessageRequest initialRequest = sendMessageRequest.getValue();

    // then
    assertThat(connectorResultObject).isInstanceOf(SqsConnectorResult.class);
    SqsConnectorResult connectorResult = (SqsConnectorResult) connectorResultObject;
    assertThat(connectorResult.getMessageId()).isEqualTo(MSG_ID);
    assertThat(initialRequest.getMessageBody())
        .isEqualTo(objectMapper.writeValueAsString(request.getQueue().getMessageBody()));
    assertThat(initialRequest.getMessageAttributes().size())
        .isEqualTo(request.getQueue().getAwsSqsNativeMessageAttributes().size());
  }

  @ParameterizedTest
  @MethodSource("failRequestCases")
  @MockitoSettings(strictness = Strictness.LENIENT)
  void execute_ShouldThrowExceptionOnMalformedRequests(final String incomingJson) {
    // given
    when(sqsClientSupplier.sqsClient(any(AWSCredentialsProvider.class), eq(ACTUAL_QUEUE_REGION)))
        .thenReturn(sqsClient);
    SendMessageResult sendMessageResult = mock(SendMessageResult.class);
    when(sendMessageResult.getMessageId()).thenReturn(MSG_ID);
    when(sqsClient.sendMessage(sendMessageRequest.capture())).thenReturn(sendMessageResult);

    OutboundConnectorContext ctx =
        OutboundConnectorContextBuilder.create()
            .variables(incomingJson)
            .validation(new DefaultValidationProvider())
            .secret(AWS_SECRET_KEY, ACTUAL_SECRET_KEY)
            .secret(AWS_ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(SQS_QUEUE_URL, ACTUAL_QUEUE_URL)
            .build();

    // when & then
    assertThrows(Exception.class, () -> function.execute(ctx));
  }

  private static Stream<String> successRequestCases() throws IOException {
    return loadRequestCasesFromFile(SUCCESS_CASES_RESOURCE_PATH);
  }

  private static Stream<String> failRequestCases() throws IOException {
    return loadRequestCasesFromFile(FAIL_CASES_RESOURCE_PATH);
  }

  @SuppressWarnings("unchecked")
  private static Stream<String> loadRequestCasesFromFile(final String fileName) throws IOException {
    final String cases = readString(new File(fileName).toPath(), UTF_8);
    return objectMapper.readValue(cases, new TypeReference<List<JsonNode>>() {}).stream()
        .map(JsonNode::toString);
  }
}
