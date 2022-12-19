/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.model.SnsConnectorResult;
import io.camunda.connector.suppliers.SnsClientSupplier;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class SnsConnectorFunctionTest extends BaseTest {

  private SnsConnectorFunction connector;
  private OutboundConnectorContext context;
  private PublishResult publishResult;

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
  public void execute_shouldExecuteRequestAndReturnResultWithMsgId() {
    // Given
    AmazonSNS snsClient = Mockito.mock(AmazonSNS.class);
    Mockito.when(snsClient.publish(ArgumentMatchers.any(PublishRequest.class)))
        .thenReturn(publishResult);
    SnsClientSupplier snsClientSupplier = Mockito.mock(SnsClientSupplier.class);
    Mockito.when(
            snsClientSupplier.snsClient(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString()))
        .thenReturn(snsClient);
    connector = new SnsConnectorFunction(snsClientSupplier, GSON);

    // When
    Object execute = connector.execute(context);

    // Then
    Mockito.verify(snsClient, Mockito.times(1)).shutdown();

    Assertions.assertThat(execute).isInstanceOf(SnsConnectorResult.class);
    var result = (SnsConnectorResult) execute;
    Assertions.assertThat(result.getMessageId()).isEqualTo(MSG_ID);
  }
}
