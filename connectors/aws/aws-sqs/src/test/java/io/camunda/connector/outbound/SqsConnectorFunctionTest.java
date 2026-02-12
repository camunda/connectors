/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.outbound;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.common.suppliers.AmazonSQSClientSupplier;
import io.camunda.connector.outbound.model.SqsConnectorResult;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

public class SqsConnectorFunctionTest extends BaseTest {

  private SqsConnectorFunction connector;
  private OutboundConnectorContext context;
  private SendMessageResponse sendMessageResult;

  @BeforeEach
  public void init() {
    context =
        OutboundConnectorContextBuilder.create()
            .secret(AWS_ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(AWS_SECRET_KEY, ACTUAL_SECRET_KEY)
            .variables(DEFAULT_REQUEST_BODY_WITH_JSON_PAYLOAD)
            .build();
    sendMessageResult = SendMessageResponse.builder().build();
    sendMessageResult = sendMessageResult.toBuilder().messageId(MSG_ID).build();
  }

  @Test
  public void execute_shouldThrowExceptionWhenSQSClientNotExist() {
    // Given context with correct data and request
    connector = new SqsConnectorFunction();
    // When connector.execute(context) without amazon sqs client
    // Then we expect SdkClientException
    assertThrows(
        SqsException.class, () -> connector.execute(context), "SqsException from amazon expected");
  }

  @Test
  public void execute_shouldExecuteRequestAndReturnResultWithMsgId()
      throws JsonProcessingException {
    // Given
    SqsClient sqsClient = Mockito.mock(SqsClient.class);
    Mockito.when(sqsClient.sendMessage(ArgumentMatchers.any(SendMessageRequest.class)))
        .thenReturn(sendMessageResult);
    AmazonSQSClientSupplier sqsClientSupplier = Mockito.mock(AmazonSQSClientSupplier.class);
    Mockito.when(
            sqsClientSupplier.sqsClient(
                any(AwsCredentialsProvider.class), ArgumentMatchers.anyString()))
        .thenReturn(sqsClient);
    connector = new SqsConnectorFunction(sqsClientSupplier, objectMapper);

    // When
    Object execute = connector.execute(context);

    // Then
    Mockito.verify(sqsClient, Mockito.times(1)).close();

    Assertions.assertThat(execute).isInstanceOf(SqsConnectorResult.class);
    var result = (SqsConnectorResult) execute;
    Assertions.assertThat(result.getMessageId()).isEqualTo(MSG_ID);
  }

  @Test
  public void execute_shouldPassPayloadAsJsonWhenJsonArrivesFromForm()
      throws JsonProcessingException {
    // Given
    SqsClient sqsClient = Mockito.mock(SqsClient.class);
    Mockito.when(sqsClient.sendMessage(ArgumentMatchers.any(SendMessageRequest.class)))
        .thenReturn(sendMessageResult);
    AmazonSQSClientSupplier sqsClientSupplier = Mockito.mock(AmazonSQSClientSupplier.class);
    Mockito.when(
            sqsClientSupplier.sqsClient(
                any(AwsCredentialsProvider.class), ArgumentMatchers.anyString()))
        .thenReturn(sqsClient);
    ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
    Mockito.when(sqsClient.sendMessage(captor.capture()))
        .thenReturn(SendMessageResponse.builder().build());
    connector = new SqsConnectorFunction(sqsClientSupplier, objectMapper);

    // When
    connector.execute(context);

    // Then
    SendMessageRequest capturedRequest = captor.getValue();
    Assertions.assertThat(capturedRequest.messageBody()).isEqualTo("{\"data\":\"ok\"}");
  }

  @Test
  public void execute_shouldPassPayloadAsStringWhenStringArrivesFromForm()
      throws JsonProcessingException {
    // Given
    context =
        OutboundConnectorContextBuilder.create()
            .secret(AWS_ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(AWS_SECRET_KEY, ACTUAL_SECRET_KEY)
            .variables(DEFAULT_REQUEST_BODY_WITH_STRING_PAYLOAD)
            .build();
    sendMessageResult = SendMessageResponse.builder().build();
    sendMessageResult = sendMessageResult.toBuilder().messageId(MSG_ID).build();
    SqsClient sqsClient = Mockito.mock(SqsClient.class);
    Mockito.when(sqsClient.sendMessage(ArgumentMatchers.any(SendMessageRequest.class)))
        .thenReturn(sendMessageResult);
    AmazonSQSClientSupplier sqsClientSupplier = Mockito.mock(AmazonSQSClientSupplier.class);
    Mockito.when(
            sqsClientSupplier.sqsClient(
                any(AwsCredentialsProvider.class), ArgumentMatchers.anyString()))
        .thenReturn(sqsClient);
    ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
    Mockito.when(sqsClient.sendMessage(captor.capture()))
        .thenReturn(SendMessageResponse.builder().build());
    connector = new SqsConnectorFunction(sqsClientSupplier, objectMapper);

    // When
    connector.execute(context);

    // Then
    SendMessageRequest capturedRequest = captor.getValue();
    Assertions.assertThat(capturedRequest.messageBody()).isEqualTo("I am a string value!");
  }
}
