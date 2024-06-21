/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sns.outbound;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.sns.outbound.model.SnsConnectorResult;
import io.camunda.connector.sns.suppliers.SnsClientSupplier;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SnsConnectorFunctionTest extends BaseTest {

  private SnsConnectorFunction connector;
  private OutboundConnectorContext context;
  private PublishResult publishResult;

  @Mock private AmazonSNS snsClient;
  @Captor private ArgumentCaptor<PublishRequest> requestArgumentCaptor;

  @BeforeEach
  public void init() {
    context =
        OutboundConnectorContextBuilder.create()
            .secret(AWS_ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(AWS_SECRET_KEY, ACTUAL_SECRET_KEY)
            .variables(DEFAULT_REQUEST_BODY)
            .build();
    publishResult = new PublishResult();
    publishResult.setMessageId(MSG_ID);
  }

  @Test
  public void execute_shouldThrowExceptionWhenSNSClientNotExist() {
    // Given context with correct data and request
    connector = new SnsConnectorFunction();
    // When connector.execute(context) without amazon sns client
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
    Mockito.when(snsClient.publish(any(PublishRequest.class))).thenReturn(publishResult);
    SnsClientSupplier snsClientSupplier = Mockito.mock(SnsClientSupplier.class);
    Mockito.when(
            snsClientSupplier.getSnsClient(
                any(AWSCredentialsProvider.class), ArgumentMatchers.anyString()))
        .thenReturn(snsClient);
    connector = new SnsConnectorFunction(snsClientSupplier, objectMapper);

    // When
    Object execute = connector.execute(context);

    // Then
    Mockito.verify(snsClient, Mockito.times(1)).shutdown();

    Assertions.assertThat(execute).isInstanceOf(SnsConnectorResult.class);
    var result = (SnsConnectorResult) execute;
    Assertions.assertThat(result.getMessageId()).isEqualTo(MSG_ID);
  }

  @Test
  public void execute_shouldExecuteRequestWithJsonTypeMsg() throws JsonProcessingException {
    // Given
    Mockito.when(snsClient.publish(requestArgumentCaptor.capture())).thenReturn(publishResult);
    SnsClientSupplier snsClientSupplier = Mockito.mock(SnsClientSupplier.class);
    Mockito.when(
            snsClientSupplier.getSnsClient(
                any(AWSCredentialsProvider.class), ArgumentMatchers.anyString()))
        .thenReturn(snsClient);
    connector = new SnsConnectorFunction(snsClientSupplier, objectMapper);
    context =
        OutboundConnectorContextBuilder.create()
            .secret(AWS_ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(AWS_SECRET_KEY, ACTUAL_SECRET_KEY)
            .variables(REQUEST_WITH_JSON_MSG_BODY)
            .build();

    // When
    connector.execute(context);

    // Then
    Mockito.verify(snsClient, Mockito.times(1)).shutdown();
    String message = requestArgumentCaptor.getValue().getMessage();
    Assertions.assertThat(message).isEqualTo("{\"key\":\"value\"}");
  }

  @Test
  public void execute_shouldExecuteRequestWithJsonTypeMsgShouldNotEscape() {
    // Given
    Mockito.when(snsClient.publish(requestArgumentCaptor.capture())).thenReturn(publishResult);
    SnsClientSupplier snsClientSupplier = Mockito.mock(SnsClientSupplier.class);
    Mockito.when(
            snsClientSupplier.getSnsClient(
                any(AWSCredentialsProvider.class), ArgumentMatchers.anyString()))
        .thenReturn(snsClient);
    connector = new SnsConnectorFunction(snsClientSupplier, objectMapper);
    context =
        OutboundConnectorContextBuilder.create()
            .secret(AWS_ACCESS_KEY, ACTUAL_ACCESS_KEY)
            .secret(AWS_SECRET_KEY, ACTUAL_SECRET_KEY)
            .variables(REQUEST_WITH_JSON_MSG_BODY_SPECIAL_CHAR)
            .build();

    // When
    connector.execute(context);

    // Then
    Mockito.verify(snsClient, Mockito.times(1)).shutdown();
    String message = requestArgumentCaptor.getValue().getMessage();
    Assertions.assertThat(message).isEqualTo("{\"key\":\"\\\"normal\\\" value\"}");
  }
}
