/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.outbound;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.common.suppliers.AmazonSQSClientSupplier;
import io.camunda.connector.outbound.model.SqsConnectorResult;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class SqsConnectorFunctionTest extends BaseTest {

  private SqsConnectorFunction connector;
  private OutboundConnectorContext context;
  private SendMessageResult sendMessageResult;

  @BeforeEach
  public void init() {
    context =
        OutboundConnectorContextBuilder.create()
            .secret(AWS_ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(AWS_SECRET_KEY, ACTUAL_SECRET_KEY)
            .variables(DEFAULT_REQUEST_BODY_WITH_JSON_PAYLOAD)
            .build();
    sendMessageResult = new SendMessageResult();
    sendMessageResult.setMessageId(MSG_ID);
  }

  @Test
  public void execute_shouldThrowExceptionWhenSQSClientNotExist() {
    // Given context with correct data and request
    connector = new SqsConnectorFunction();
    // When connector.execute(context) without amazon sqs client
    // Then we expect SdkClientException
    assertThrows(
        SdkClientException.class,
        () -> connector.execute(context),
        "SdkClientException from amazon was expected");
  }

  @Test
  public void execute_shouldExecuteRequestAndReturnResultWithMsgId()
      throws JsonProcessingException {
    // Given
    AmazonSQS sqsClient = Mockito.mock(AmazonSQS.class);
    Mockito.when(sqsClient.sendMessage(ArgumentMatchers.any(SendMessageRequest.class)))
        .thenReturn(sendMessageResult);
    AmazonSQSClientSupplier sqsClientSupplier = Mockito.mock(AmazonSQSClientSupplier.class);
    Mockito.when(
            sqsClientSupplier.sqsClient(
                any(AWSCredentialsProvider.class), ArgumentMatchers.anyString()))
        .thenReturn(sqsClient);
    connector = new SqsConnectorFunction(sqsClientSupplier, objectMapper);

    // When
    Object execute = connector.execute(context);

    // Then
    Mockito.verify(sqsClient, Mockito.times(1)).shutdown();

    Assertions.assertThat(execute).isInstanceOf(SqsConnectorResult.class);
    var result = (SqsConnectorResult) execute;
    Assertions.assertThat(result.getMessageId()).isEqualTo(MSG_ID);
  }

  @Test
  public void execute_shouldPassPayloadAsJsonWhenJsonArrivesFromForm()
      throws JsonProcessingException {
    // Given
    AmazonSQS sqsClient = Mockito.mock(AmazonSQS.class);
    Mockito.when(sqsClient.sendMessage(ArgumentMatchers.any(SendMessageRequest.class)))
        .thenReturn(sendMessageResult);
    AmazonSQSClientSupplier sqsClientSupplier = Mockito.mock(AmazonSQSClientSupplier.class);
    Mockito.when(
            sqsClientSupplier.sqsClient(
                any(AWSCredentialsProvider.class), ArgumentMatchers.anyString()))
        .thenReturn(sqsClient);
    ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
    Mockito.when(sqsClient.sendMessage(captor.capture())).thenReturn(new SendMessageResult());
    connector = new SqsConnectorFunction(sqsClientSupplier, objectMapper);

    // When
    connector.execute(context);

    // Then
    SendMessageRequest capturedRequest = captor.getValue();
    Assertions.assertThat(capturedRequest.getMessageBody()).isEqualTo("{\"data\":\"ok\"}");
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
    sendMessageResult = new SendMessageResult();
    sendMessageResult.setMessageId(MSG_ID);
    AmazonSQS sqsClient = Mockito.mock(AmazonSQS.class);
    Mockito.when(sqsClient.sendMessage(ArgumentMatchers.any(SendMessageRequest.class)))
        .thenReturn(sendMessageResult);
    AmazonSQSClientSupplier sqsClientSupplier = Mockito.mock(AmazonSQSClientSupplier.class);
    Mockito.when(
            sqsClientSupplier.sqsClient(
                any(AWSCredentialsProvider.class), ArgumentMatchers.anyString()))
        .thenReturn(sqsClient);
    ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
    Mockito.when(sqsClient.sendMessage(captor.capture())).thenReturn(new SendMessageResult());
    connector = new SqsConnectorFunction(sqsClientSupplier, objectMapper);

    // When
    connector.execute(context);

    // Then
    SendMessageRequest capturedRequest = captor.getValue();
    Assertions.assertThat(capturedRequest.getMessageBody()).isEqualTo("I am a string value!");
  }
}
