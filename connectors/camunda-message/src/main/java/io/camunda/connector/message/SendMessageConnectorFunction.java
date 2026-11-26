/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.message;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CorrelateMessageCommandStep1.CorrelateMessageCommandStep2;
import io.camunda.client.api.command.CorrelateMessageCommandStep1.CorrelateMessageCommandStep3;
import io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.client.api.response.CorrelateMessageResponse;
import io.camunda.client.api.response.PublishMessageResponse;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.dsl.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.ConnectorElementType;
import io.camunda.connector.runtime.app.CamundaClientContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    inputVariables = {
      "messageName",
      "correlationKey",
      "variables",
      "correlationType",
      "tenantId",
      "requestTimeout"
    },
    name = "message",
    type = "io.camunda:sendMessage:1")
@ElementTemplate(
    engineVersion = "^8.3",
    id = "io.camunda.connectors.message.v1",
    inputDataClass = SendMessageRequest.class,
    name = "Send Message Connector",
    icon = "send.svg",
    elementTypes = {
      @ConnectorElementType(
          elementType = BpmnType.INTERMEDIATE_THROW_EVENT,
          appliesTo = BpmnType.INTERMEDIATE_THROW_EVENT,
          templateIdOverride = "io.camunda.connectors.message.intermediate.v1"),
      @ConnectorElementType(
          elementType = BpmnType.MESSAGE_END_EVENT,
          appliesTo = BpmnType.MESSAGE_END_EVENT,
          templateIdOverride = "io.camunda.connectors.message.end.v1"),
      @ConnectorElementType(
          elementType = BpmnType.SEND_TASK,
          appliesTo = BpmnType.TASK,
          templateIdOverride = "io.camunda.connectors.message.sendtask.v1")
    })
public class SendMessageConnectorFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(SendMessageConnectorFunction.class);

  CamundaClient camundaClient;

  public SendMessageConnectorFunction() {
    super();
    this.camundaClient = CamundaClientContext.getCamundaClient();
  }

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {

    SendMessageRequest messageRequest = context.bindVariables(SendMessageRequest.class);
    String tenantId = resolveTenantId(messageRequest, context.getJobContext().getTenantId());
    LOG.debug(
        "Invoke send message connector with name {} and correlation key {}",
        messageRequest.messageName(),
        messageRequest.correlationKey());

    switch (messageRequest.correlationType()) {
      case SendMessageRequest.CorrelationType.Publish publish -> {
        PublishMessageResponse publishMessageResponse =
            publishMessageWithBuffer(messageRequest, publish, tenantId);
        LOG.debug("message published with messageKey {}", publishMessageResponse.getMessageKey());
        return publishMessageResponse;
      }
      case SendMessageRequest.CorrelationType.CorrelateWithResult correlateWithResult -> {
        CorrelateMessageResponse correlateMessageResponse =
            correlateMessageWithResponse(messageRequest, tenantId);
        LOG.debug(
            "message correlated with message key {} and process instance key {}",
            correlateMessageResponse.getMessageKey(),
            correlateMessageResponse.getProcessInstanceKey());
        return correlateMessageResponse;
      }
    }
  }

  /**
   * The message always targets the tenant the job runs in. The runtime sends it with its own
   * identity, which may be authorized for other tenants too, so honouring a different requested
   * tenant would let a process reach into a tenant it does not belong to.
   */
  private static String resolveTenantId(SendMessageRequest messageRequest, String jobTenantId) {
    String requestedTenantId = messageRequest.tenantId();
    if (requestedTenantId != null
        && !requestedTenantId.isBlank()
        && !requestedTenantId.equals(jobTenantId)) {
      throw new ConnectorInputException(
          "The Send Message connector can only send messages to the tenant of the job '%s', but tenant '%s' was requested"
              .formatted(jobTenantId, requestedTenantId));
    }
    return jobTenantId;
  }

  private PublishMessageResponse publishMessageWithBuffer(
      SendMessageRequest messageRequest,
      SendMessageRequest.CorrelationType.Publish publish,
      String tenantId) {
    PublishMessageCommandStep3 publishMessageCommand;
    PublishMessageCommandStep2 step2 =
        camundaClient.newPublishMessageCommand().messageName(messageRequest.messageName());
    if (messageRequest.correlationKey() != null
        && messageRequest.correlationKey().isBlank() == false) {
      publishMessageCommand = step2.correlationKey(messageRequest.correlationKey());
    } else {
      publishMessageCommand = step2.withoutCorrelationKey();
    }
    if (publish.messageId() != null) {
      publishMessageCommand.messageId(publish.messageId());
    }
    if (messageRequest.variables() != null) {
      publishMessageCommand.variables(messageRequest.variables());
    }
    if (publish.timeToLive() != null) {
      publishMessageCommand.timeToLive(publish.timeToLive());
    }
    if (tenantId != null) {
      publishMessageCommand.tenantId(tenantId);
    }
    if (messageRequest.requestTimeout() != null) {
      publishMessageCommand.requestTimeout(messageRequest.requestTimeout());
    }
    PublishMessageResponse publishMessageResponse = publishMessageCommand.send().join();
    return publishMessageResponse;
  }

  private CorrelateMessageResponse correlateMessageWithResponse(
      SendMessageRequest messageRequest, String tenantId) {
    CorrelateMessageCommandStep2 correlateMessageCommand =
        camundaClient.newCorrelateMessageCommand().messageName(messageRequest.messageName());
    CorrelateMessageCommandStep3 correlateMessageCommandStep3;
    if (messageRequest.correlationKey() == null || messageRequest.correlationKey().isBlank()) {
      correlateMessageCommandStep3 = correlateMessageCommand.withoutCorrelationKey();
    } else {
      correlateMessageCommandStep3 =
          correlateMessageCommand.correlationKey(messageRequest.correlationKey());
    }
    if (messageRequest.variables() != null) {
      correlateMessageCommandStep3.variables(messageRequest.variables());
    }
    if (tenantId != null) {
      correlateMessageCommandStep3.tenantId(tenantId);
    }
    if (messageRequest.requestTimeout() != null) {
      correlateMessageCommandStep3.requestTimeout(messageRequest.requestTimeout());
    }
    CorrelateMessageResponse correlateMessageResponse = correlateMessageCommandStep3.send().join();
    return correlateMessageResponse;
  }
}
