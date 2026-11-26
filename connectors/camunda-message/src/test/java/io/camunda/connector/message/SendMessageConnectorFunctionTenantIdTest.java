/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.message;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.runtime.app.ZeebeClientContext;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.CorrelateMessageCommandStep1;
import io.camunda.zeebe.client.api.command.CorrelateMessageCommandStep1.CorrelateMessageCommandStep2;
import io.camunda.zeebe.client.api.command.CorrelateMessageCommandStep1.CorrelateMessageCommandStep3;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.zeebe.client.api.response.CorrelateMessageResponse;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

class SendMessageConnectorFunctionTenantIdTest {

  private static final String MESSAGE_NAME = "message";
  private static final String CORRELATION_KEY = "key";
  private static final String JOB_TENANT_ID = "tenant-a";
  private static final String OTHER_TENANT_ID = "tenant-b";

  private final ZeebeClient zeebeClient = mock(ZeebeClient.class);
  private final OutboundConnectorContext context = mock(OutboundConnectorContext.class);

  private final PublishMessageCommandStep1 publishStep1 = mock(PublishMessageCommandStep1.class);
  private final PublishMessageCommandStep2 publishStep2 = mock(PublishMessageCommandStep2.class);
  private final PublishMessageCommandStep3 publishStep3 = mock(PublishMessageCommandStep3.class);

  private final CorrelateMessageCommandStep1 correlateStep1 =
      mock(CorrelateMessageCommandStep1.class);
  private final CorrelateMessageCommandStep2 correlateStep2 =
      mock(CorrelateMessageCommandStep2.class);
  private final CorrelateMessageCommandStep3 correlateStep3 =
      mock(CorrelateMessageCommandStep3.class);

  private SendMessageConnectorFunction function;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    var jobContext = mock(JobContext.class);
    when(jobContext.getTenantId()).thenReturn(JOB_TENANT_ID);
    when(context.getJobContext()).thenReturn(jobContext);

    var applicationContext = mock(ApplicationContext.class);
    when(applicationContext.getBean(ZeebeClient.class)).thenReturn(zeebeClient);
    new ZeebeClientContext().setApplicationContext(applicationContext);

    when(zeebeClient.newPublishMessageCommand()).thenReturn(publishStep1);
    when(publishStep1.messageName(anyString())).thenReturn(publishStep2);
    when(publishStep2.correlationKey(anyString())).thenReturn(publishStep3);
    when(publishStep3.tenantId(anyString())).thenReturn(publishStep3);
    var publishFuture = mock(ZeebeFuture.class);
    when(publishStep3.send()).thenReturn(publishFuture);
    when(publishFuture.join()).thenReturn(mock(PublishMessageResponse.class));

    when(zeebeClient.newCorrelateMessageCommand()).thenReturn(correlateStep1);
    when(correlateStep1.messageName(anyString())).thenReturn(correlateStep2);
    when(correlateStep2.correlationKey(anyString())).thenReturn(correlateStep3);
    when(correlateStep3.tenantId(anyString())).thenReturn(correlateStep3);
    var correlateFuture = mock(ZeebeFuture.class);
    when(correlateStep3.send()).thenReturn(correlateFuture);
    when(correlateFuture.join()).thenReturn(mock(CorrelateMessageResponse.class));

    function = new SendMessageConnectorFunction();
  }

  @Test
  void publish_withoutTenantId_targetsJobTenant() throws Exception {
    givenRequest(new SendMessageRequest.CorrelationType.Publish(null, null), null);

    function.execute(context);

    verify(publishStep3).tenantId(JOB_TENANT_ID);
    verify(publishStep3).send();
  }

  @Test
  void publish_withJobTenantId_targetsJobTenant() throws Exception {
    givenRequest(new SendMessageRequest.CorrelationType.Publish(null, null), JOB_TENANT_ID);

    function.execute(context);

    verify(publishStep3).tenantId(JOB_TENANT_ID);
    verify(publishStep3).send();
  }

  @Test
  void publish_withOtherTenantId_failsWithoutSending() {
    givenRequest(new SendMessageRequest.CorrelationType.Publish(null, null), OTHER_TENANT_ID);

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining(JOB_TENANT_ID)
        .hasMessageContaining(OTHER_TENANT_ID);
    verify(publishStep3, never()).send();
  }

  @Test
  void correlate_withoutTenantId_targetsJobTenant() throws Exception {
    givenRequest(new SendMessageRequest.CorrelationType.CorrelateWithResult(), null);

    function.execute(context);

    verify(correlateStep3).tenantId(JOB_TENANT_ID);
    verify(correlateStep3).send();
  }

  @Test
  void correlate_withJobTenantId_targetsJobTenant() throws Exception {
    givenRequest(new SendMessageRequest.CorrelationType.CorrelateWithResult(), JOB_TENANT_ID);

    function.execute(context);

    verify(correlateStep3).tenantId(JOB_TENANT_ID);
    verify(correlateStep3).send();
  }

  @Test
  void correlate_withOtherTenantId_failsWithoutSending() {
    givenRequest(new SendMessageRequest.CorrelationType.CorrelateWithResult(), OTHER_TENANT_ID);

    assertThatThrownBy(() -> function.execute(context))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining(JOB_TENANT_ID)
        .hasMessageContaining(OTHER_TENANT_ID);
    verify(correlateStep3, never()).send();
  }

  private void givenRequest(SendMessageRequest.CorrelationType type, String tenantId) {
    when(context.bindVariables(SendMessageRequest.class))
        .thenReturn(
            new SendMessageRequest(MESSAGE_NAME, CORRELATION_KEY, null, type, tenantId, null));
  }
}
