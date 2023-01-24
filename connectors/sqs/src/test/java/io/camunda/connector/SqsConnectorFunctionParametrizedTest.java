/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

import static io.camunda.connector.BaseTest.ACTUAL_ACCESS_KEY;
import static io.camunda.connector.BaseTest.ACTUAL_QUEUE_REGION;
import static io.camunda.connector.BaseTest.ACTUAL_QUEUE_URL;
import static io.camunda.connector.BaseTest.ACTUAL_SECRET_KEY;
import static io.camunda.connector.BaseTest.AWS_ACCESS_KEY;
import static io.camunda.connector.BaseTest.AWS_SECRET_KEY;
import static io.camunda.connector.BaseTest.GSON;
import static io.camunda.connector.BaseTest.MSG_ID;
import static io.camunda.connector.BaseTest.SQS_QUEUE_URL;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.model.SqsConnectorRequest;
import io.camunda.connector.model.SqsConnectorResult;
import io.camunda.connector.suppliers.SqsClientSupplier;
import io.camunda.connector.suppliers.SqsGsonComponentSupplier;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SqsConnectorFunctionParametrizedTest {

  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/success-test-cases.json";

  private static final String FAIL_CASES_RESOURCE_PATH =
      "src/test/resources/requests/fail-test-cases.json";

  @Mock private SqsClientSupplier sqsClientSupplier;
  @Mock private AmazonSQS sqsClient;
  @Captor private ArgumentCaptor<SendMessageRequest> sendMessageRequest;

  private SqsConnectorFunction function;

  @BeforeEach
  void setup() {
    function = new SqsConnectorFunction(sqsClientSupplier, SqsGsonComponentSupplier.gsonInstance());
  }

  @ParameterizedTest
  @MethodSource("successRequestCases")
  void execute_ShouldSucceedSuccessCases(final String incomingJson) {
    // given
    SqsConnectorRequest expectedRequest = GSON.fromJson(incomingJson, SqsConnectorRequest.class);
    when(sqsClientSupplier.sqsClient(ACTUAL_ACCESS_KEY, ACTUAL_SECRET_KEY, ACTUAL_QUEUE_REGION))
        .thenReturn(sqsClient);
    SendMessageResult sendMessageResult = mock(SendMessageResult.class);
    when(sendMessageResult.getMessageId()).thenReturn(MSG_ID);
    when(sqsClient.sendMessage(sendMessageRequest.capture())).thenReturn(sendMessageResult);
    OutboundConnectorContext ctx =
        OutboundConnectorContextBuilder.create()
            .variables(incomingJson)
            .secret(AWS_SECRET_KEY, ACTUAL_SECRET_KEY)
            .secret(AWS_ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(SQS_QUEUE_URL, ACTUAL_QUEUE_URL)
            .build();
    // when
    Object connectorResultObject = function.execute(ctx);
    SendMessageRequest initialRequest = sendMessageRequest.getValue();

    // then
    assertThat(connectorResultObject).isInstanceOf(SqsConnectorResult.class);
    SqsConnectorResult connectorResult = (SqsConnectorResult) connectorResultObject;
    assertThat(connectorResult.getMessageId()).isEqualTo(MSG_ID);
    assertThat(initialRequest.getMessageBody())
        .isEqualTo(GSON.toJson(expectedRequest.getQueue().getMessageBody()));
    assertThat(initialRequest.getMessageAttributes().size())
        .isEqualTo(expectedRequest.getQueue().getMessageAttributes().size());
  }

  @ParameterizedTest
  @MethodSource("failRequestCases")
  void execute_ShouldThrowExceptionOnMalformedRequests(final String incomingJson) {
    // given
    SqsConnectorRequest expectedRequest = GSON.fromJson(incomingJson, SqsConnectorRequest.class);
    OutboundConnectorContext ctx =
        OutboundConnectorContextBuilder.create()
            .variables(incomingJson)
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
    var array = GSON.fromJson(cases, ArrayList.class);
    return array.stream().map(GSON::toJson).map(Arguments::of);
  }
}
