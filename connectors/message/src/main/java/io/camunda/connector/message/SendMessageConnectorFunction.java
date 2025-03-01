/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.message;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.dsl.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.ConnectorElementType;
import io.camunda.connector.runtime.app.ZeebeClientContext;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.CorrelateMessageCommandStep1.CorrelateMessageCommandStep2;
import io.camunda.zeebe.client.api.command.CorrelateMessageCommandStep1.CorrelateMessageCommandStep3;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.zeebe.client.api.response.CorrelateMessageResponse;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    inputVariables = {
      "mode",
      "messageName",
      "correlationKey",
      "variables",
      "timeToLive",
      "messageId",
      "tenantId",
      "requestTimeout"
    },
    name = "message",
    type = "io.camunda:sendMessage:1")
@ElementTemplate(
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

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {

    SendMessageRequest messageRequest = context.bindVariables(SendMessageRequest.class);
    LOG.debug("Invoke send message connector with {}", messageRequest);

    ZeebeClient zeebeClient = ZeebeClientContext.getZeebeClient();
    LOG.debug("using Zeebe client {}", zeebeClient);

    if ("publish".equals(messageRequest.mode())) {
      PublishMessageCommandStep3 publishMessageCommand;
      PublishMessageCommandStep2 step2 =
          zeebeClient.newPublishMessageCommand().messageName(messageRequest.messageName());
      if (messageRequest.correlationKey() != null
          && messageRequest.correlationKey().isBlank() == false) {
        publishMessageCommand = step2.correlationKey(messageRequest.correlationKey());
      } else {
        publishMessageCommand = step2.withoutCorrelationKey();
      }
      if (messageRequest.messageId() != null) {
        publishMessageCommand.messageId(messageRequest.messageId());
      }
      if (messageRequest.variables() != null) {
        publishMessageCommand.variables(messageRequest.variables());
      }
      if (messageRequest.timeToLive() != null) {
        publishMessageCommand.timeToLive(messageRequest.timeToLive());
      }
      if (messageRequest.tenantId() != null) {
        publishMessageCommand.tenantId(messageRequest.tenantId());
      }
      if (messageRequest.requestTimeout() != null) {
        publishMessageCommand.requestTimeout(messageRequest.requestTimeout());
      }
      PublishMessageResponse publishMessageResponse = publishMessageCommand.send().join();
      LOG.debug("message published with messageKey {}", publishMessageResponse.getMessageKey());
      return publishMessageResponse;
    } else if ("correlate".equals(messageRequest.mode())) {
      CorrelateMessageCommandStep2 correlateMessageCommand =
          zeebeClient.newCorrelateMessageCommand().messageName(messageRequest.messageName());
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
      if (messageRequest.tenantId() != null) {
        correlateMessageCommandStep3.tenantId(messageRequest.tenantId());
      }
      if (messageRequest.requestTimeout() != null) {
        correlateMessageCommandStep3.requestTimeout(messageRequest.requestTimeout());
      }
      CorrelateMessageResponse correlateMessageResponse =
          correlateMessageCommandStep3.send().join();
      LOG.debug(
          "message correlated with message key {} and process instance key {}",
          correlateMessageResponse.getMessageKey(),
          correlateMessageResponse.getProcessInstanceKey());
      return correlateMessageResponse;
    } else {
      throw new Exception("mode '" + messageRequest.mode() + "' not supported");
    }
  }
}
