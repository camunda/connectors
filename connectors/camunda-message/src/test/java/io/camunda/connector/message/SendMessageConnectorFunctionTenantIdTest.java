/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.CorrelateMessageCommandStep1;
import io.camunda.client.api.command.CorrelateMessageCommandStep1.CorrelateMessageCommandStep2;
import io.camunda.client.api.command.CorrelateMessageCommandStep1.CorrelateMessageCommandStep3;
import io.camunda.client.api.command.PublishMessageCommandStep1;
import io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.client.api.response.CorrelateMessageResponse;
import io.camunda.client.api.response.PublishMessageResponse;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.runtime.app.CamundaClientContext;
import io.camunda.connector.runtime.tenant.PhysicalTenantClientSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

class SendMessageConnectorFunctionTenantIdTest {

  private static final String MESSAGE_NAME = "message";
  private static final String CORRELATION_KEY = "key";
  private static final String TENANT_ID = "tenant-a";

  private final CamundaClient camundaClient = mock(CamundaClient.class);
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
    var selector = mock(PhysicalTenantClientSelector.class);
    when(context.getJobContext()).thenReturn(jobContext);
    when(selector.forJob(jobContext)).thenReturn(camundaClient);

    var applicationContext = mock(ApplicationContext.class);
    when(applicationContext.getBean(PhysicalTenantClientSelector.class)).thenReturn(selector);
    new CamundaClientContext().setApplicationContext(applicationContext);

    when(camundaClient.newPublishMessageCommand()).thenReturn(publishStep1);
    when(publishStep1.messageName(anyString())).thenReturn(publishStep2);
    when(publishStep2.correlationKey(anyString())).thenReturn(publishStep3);
    when(publishStep3.tenantId(anyString())).thenReturn(publishStep3);
    var publishFuture = mock(CamundaFuture.class);
    when(publishStep3.send()).thenReturn(publishFuture);
    when(publishFuture.join()).thenReturn(mock(PublishMessageResponse.class));

    when(camundaClient.newCorrelateMessageCommand()).thenReturn(correlateStep1);
    when(correlateStep1.messageName(anyString())).thenReturn(correlateStep2);
    when(correlateStep2.correlationKey(anyString())).thenReturn(correlateStep3);
    when(correlateStep3.tenantId(anyString())).thenReturn(correlateStep3);
    var correlateFuture = mock(CamundaFuture.class);
    when(correlateStep3.send()).thenReturn(correlateFuture);
    when(correlateFuture.join()).thenReturn(mock(CorrelateMessageResponse.class));

    function = new SendMessageConnectorFunction();
  }

  @Test
  void publish_withTenantId_setsTenantIdOnCommand() throws Exception {
    givenRequest(new SendMessageRequest.CorrelationType.Publish(null, null), TENANT_ID);

    function.execute(context);

    verify(publishStep3).tenantId(TENANT_ID);
  }

  @Test
  void publish_withoutTenantId_doesNotSetTenantIdOnCommand() throws Exception {
    givenRequest(new SendMessageRequest.CorrelationType.Publish(null, null), null);

    function.execute(context);

    verify(publishStep3, never()).tenantId(any());
    verify(publishStep3).send();
  }

  @Test
  void correlate_withTenantId_setsTenantIdOnCommand() throws Exception {
    givenRequest(new SendMessageRequest.CorrelationType.CorrelateWithResult(), TENANT_ID);

    function.execute(context);

    verify(correlateStep3).tenantId(TENANT_ID);
  }

  @Test
  void correlate_withoutTenantId_doesNotSetTenantIdOnCommand() throws Exception {
    givenRequest(new SendMessageRequest.CorrelationType.CorrelateWithResult(), null);

    function.execute(context);

    verify(correlateStep3, never()).tenantId(any());
    verify(correlateStep3).send();
  }

  private void givenRequest(SendMessageRequest.CorrelationType type, String tenantId) {
    when(context.bindVariables(SendMessageRequest.class))
        .thenReturn(
            new SendMessageRequest(MESSAGE_NAME, CORRELATION_KEY, null, type, tenantId, null));
  }
}
