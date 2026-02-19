/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.outbound;

import static io.camunda.connector.agenticai.a2a.client.common.A2aErrorCodes.ERROR_CODE_A2A_CLIENT_SEND_MESSAGE_RESPONSE_TIMEOUT;

import io.a2a.client.ClientEvent;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aSendMessageResult;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClientConfig;
import io.camunda.connector.agenticai.a2a.client.common.sdk.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.outbound.convert.A2aDocumentToPartConverter;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aCommonSendMessageConfiguration;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aCommonSendMessageConfiguration.A2aResponseRetrievalMode;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aCommonSendMessageConfiguration.A2aResponseRetrievalMode.Notification;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aSendMessageOperationParameters;
import io.camunda.connector.agenticai.a2a.client.outbound.model.A2aStandaloneOperationConfiguration.SendMessageOperationConfiguration;
import io.camunda.connector.api.error.ConnectorException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class A2aMessageSenderImpl implements A2aMessageSender {

  private static final Logger LOGGER = LoggerFactory.getLogger(A2aMessageSenderImpl.class);

  private final A2aDocumentToPartConverter documentToPartConverter;
  private final A2aSendMessageResponseHandler sendMessageResponseHandler;
  private final A2aSdkClientFactory clientFactory;

  public A2aMessageSenderImpl(
      A2aDocumentToPartConverter documentToPartConverter,
      A2aSendMessageResponseHandler sendMessageResponseHandler,
      A2aSdkClientFactory clientFactory) {
    this.documentToPartConverter = documentToPartConverter;
    this.sendMessageResponseHandler = sendMessageResponseHandler;
    this.clientFactory = clientFactory;
  }

  @Override
  public A2aSendMessageResult sendMessage(
      AgentCard agentCard, SendMessageOperationConfiguration sendMessageOperation) {
    Message message = createMessage(sendMessageOperation);
    CompletableFuture<A2aSendMessageResult> response = new CompletableFuture<>();
    BiConsumer<ClientEvent, AgentCard> consumer =
        (event, ignore) -> {
          try {
            A2aSendMessageResult result = sendMessageResponseHandler.handleClientEvent(event);
            response.complete(result);
          } catch (Exception e) {
            response.completeExceptionally(e);
          }
        };

    final var settings = sendMessageOperation.settings();
    A2aSdkClientConfig a2ASdkClientConfig = createA2aSdkClientConfig(settings);

    try (var a2aClient = clientFactory.buildClient(agentCard, consumer, a2ASdkClientConfig)) {
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

  private Message createMessage(SendMessageOperationConfiguration sendMessageOperation) {
    List<Part<?>> parts = new ArrayList<>();
    A2aSendMessageOperationParameters parameters = sendMessageOperation.params();
    parts.add(new TextPart(parameters.text()));
    parts.addAll(documentToPartConverter.convert(parameters.documents()));

    Message.Builder builder = new Message.Builder().role(Message.Role.USER).parts(parts);

    if (StringUtils.isNotBlank(parameters.contextId())) {
      builder.contextId(parameters.contextId());
    }

    if (StringUtils.isNotBlank(parameters.taskId())) {
      builder.taskId(parameters.taskId());
    }

    if (CollectionUtils.isNotEmpty(parameters.referenceTaskIds())) {
      builder.referenceTaskIds(parameters.referenceTaskIds());
    }

    return builder.build();
  }
}
