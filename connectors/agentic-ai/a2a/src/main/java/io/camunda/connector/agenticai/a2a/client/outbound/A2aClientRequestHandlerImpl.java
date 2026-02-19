/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.spec.AgentCard;
import io.camunda.connector.agenticai.a2a.client.common.A2aAgentCardFetcher;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aAgentCard;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aClientResponse;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aClientResponseBuilder;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aMessage;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aClientRequest;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aCommonSendMessageConfiguration.A2aResponseRetrievalMode.Notification;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aCommonSendMessageConfiguration.A2aResponseRetrievalMode.Polling;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aConnectorModeConfiguration.StandaloneModeConfiguration;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aConnectorModeConfiguration.ToolModeConfiguration;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aSendMessageOperationParameters;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aStandaloneOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aStandaloneOperationConfiguration.FetchAgentCardOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aStandaloneOperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aToolOperationConfiguration;
import java.util.UUID;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

public class A2aClientRequestHandlerImpl implements A2aClientRequestHandler {

  private final A2aAgentCardFetcher agentCardFetcher;
  private final A2aMessageSender a2aMessageSender;
  private final ObjectMapper objectMapper;

  public A2aClientRequestHandlerImpl(
      A2aAgentCardFetcher agentCardFetcher,
      A2aMessageSender a2aMessageSender,
      ObjectMapper objectMapper) {
    this.agentCardFetcher = agentCardFetcher;
    this.a2aMessageSender = a2aMessageSender;
    this.objectMapper = objectMapper;
  }

  @Override
  public A2aClientResponse handle(A2aClientRequest request) {
    A2aStandaloneOperationConfiguration operation;
    switch (request.data().connectorMode()) {
      case StandaloneModeConfiguration standaloneOperationConfiguration ->
          operation = standaloneOperationConfiguration.operation();
      case ToolModeConfiguration toolOperationConfiguration ->
          operation = convertOperation(toolOperationConfiguration.toolOperation());
      default ->
          throw new IllegalArgumentException(
              "Unsupported connectorMode: " + request.data().connectorMode());
    }
    return switch (operation) {
      case FetchAgentCardOperationConfiguration ignored -> handleFetchAgentCard(request);
      case SendMessageOperationConfiguration sendMessageOperation ->
          handleSendMessage(request, sendMessageOperation);
    };
  }

  private A2aClientResponse handleFetchAgentCard(A2aClientRequest request) {
    A2aAgentCard a2aAgentCard = agentCardFetcher.fetchAgentCard(request.data().connection());
    return A2aClientResponse.builder()
        .result(a2aAgentCard)
        .pollingData(new A2aClientResponse.PollingData(UUID.randomUUID().toString()))
        .build();
  }

  private A2aClientResponse handleSendMessage(
      A2aClientRequest request, SendMessageOperationConfiguration sendMessageOperation) {
    AgentCard agentCard = agentCardFetcher.fetchAgentCardRaw(request.data().connection());
    A2aSendMessageResult a2aSendMessageResult =
        a2aMessageSender.sendMessage(agentCard, sendMessageOperation);

    A2aClientResponseBuilder responseBuilder =
        A2aClientResponse.builder().result(a2aSendMessageResult);
    if (sendMessageOperation.settings().responseRetrievalMode()
        instanceof Notification notification) {
      if (StringUtils.isNotBlank(notification.token())) {
        responseBuilder.pushNotificationData(
            new A2aClientResponse.PushNotificationData(notification.token()));
      }
    } else if (sendMessageOperation.settings().responseRetrievalMode() instanceof Polling) {
      String taskOrMessageId =
          switch (a2aSendMessageResult) {
            case A2aTask task -> task.id();
            case A2aMessage message -> message.messageId();
          };
      responseBuilder.pollingData(new A2aClientResponse.PollingData(taskOrMessageId));
    }
    return responseBuilder.build();
  }

  private A2aStandaloneOperationConfiguration convertOperation(
      A2aToolOperationConfiguration operation) {
    switch (operation.operation()) {
      case FetchAgentCardOperationConfiguration.FETCH_AGENT_CARD_ID -> {
        return new FetchAgentCardOperationConfiguration();
      }
      case SendMessageOperationConfiguration.SEND_MESSAGE_ID -> {
        if (MapUtils.isEmpty(operation.params())) {
          throw new IllegalArgumentException(
              "'params' cannot be null or empty for operation: '%s'"
                  .formatted(operation.operation()));
        }
        final var parameters =
            objectMapper.convertValue(operation.params(), A2aSendMessageOperationParameters.class);
        return new SendMessageOperationConfiguration(parameters, operation.sendMessageSettings());
      }
      default ->
          throw new IllegalArgumentException(
              "Unsupported operation: '%s'".formatted(operation.operation()));
    }
  }
}
