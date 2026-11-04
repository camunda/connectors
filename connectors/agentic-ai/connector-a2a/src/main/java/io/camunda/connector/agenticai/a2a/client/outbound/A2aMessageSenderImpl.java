/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.outbound;

import static io.camunda.connector.agenticai.a2a.client.common.A2aErrorCodes.ERROR_CODE_A2A_CLIENT_SEND_MESSAGE_RESPONSE_TIMEOUT;

import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aOutgoingMessage;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkAgentCard;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClientConfig;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClientListener;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aCommonSendMessageConfiguration;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aCommonSendMessageConfiguration.A2aResponseRetrievalMode;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aCommonSendMessageConfiguration.A2aResponseRetrievalMode.Notification;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aSendMessageOperationParameters;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aStandaloneOperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.api.error.ConnectorException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class A2aMessageSenderImpl implements A2aMessageSender {

  private static final Logger LOGGER = LoggerFactory.getLogger(A2aMessageSenderImpl.class);

  private final A2aSdkClientFactory clientFactory;

  public A2aMessageSenderImpl(A2aSdkClientFactory clientFactory) {
    this.clientFactory = clientFactory;
  }

  @Override
  public A2aSendMessageResult sendMessage(
      A2aSdkAgentCard agentCard, SendMessageOperationConfiguration sendMessageOperation) {
    A2aOutgoingMessage message = createMessage(sendMessageOperation);
    CompletableFuture<A2aSendMessageResult> response = new CompletableFuture<>();
    A2aSdkClientListener listener =
        new A2aSdkClientListener() {
          @Override
          public void onResult(A2aSendMessageResult result) {
            response.complete(result);
          }

          @Override
          public void onError(Throwable error) {
            response.completeExceptionally(error);
          }
        };

    final var settings = sendMessageOperation.settings();
    A2aSdkClientConfig a2ASdkClientConfig = createA2aSdkClientConfig(settings);

    try (var a2aClient = clientFactory.buildClient(agentCard, listener, a2ASdkClientConfig)) {
      LOGGER.debug("Sending a message to the remote agent: [{}]", agentCard.name());
      a2aClient.sendMessage(message);

      try {
        return response.get(settings.timeout().toMillis(), TimeUnit.MILLISECONDS);
      } catch (TimeoutException e) {
        throw new ConnectorException(
            ERROR_CODE_A2A_CLIENT_SEND_MESSAGE_RESPONSE_TIMEOUT,
            "Timed out waiting for response from agent.",
            e);
      } catch (InterruptedException e) {
        // Re-interrupt the thread to preserve the interrupted status
        Thread.currentThread().interrupt();
        throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
      } catch (ExecutionException e) {
        throw new RuntimeException(e.getCause() != null ? e.getCause() : e);
      }
    }
  }

  private static A2aSdkClientConfig createA2aSdkClientConfig(
      A2aCommonSendMessageConfiguration settings) {
    final var retrievalMode = settings.responseRetrievalMode();

    A2aSdkClientConfig.PushNotificationConfig pushNotificationConfig = null;
    if (retrievalMode instanceof Notification notification) {
      pushNotificationConfig =
          new A2aSdkClientConfig.PushNotificationConfig(
              notification.webhookUrl(),
              notification.token(),
              notification.authenticationSchemes(),
              notification.credentials());
    }
    final var blocking = retrievalMode instanceof A2aResponseRetrievalMode.Blocking;
    return new A2aSdkClientConfig(settings.historyLength(), blocking, pushNotificationConfig);
  }

  private static A2aOutgoingMessage createMessage(
      SendMessageOperationConfiguration sendMessageOperation) {
    A2aSendMessageOperationParameters parameters = sendMessageOperation.params();
    return new A2aOutgoingMessage(
        parameters.text(),
        parameters.documents(),
        parameters.contextId(),
        parameters.taskId(),
        parameters.referenceTaskIds());
  }
}
