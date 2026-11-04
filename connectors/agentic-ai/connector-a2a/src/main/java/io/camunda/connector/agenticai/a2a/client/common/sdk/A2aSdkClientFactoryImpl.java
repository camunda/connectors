/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk;

import io.camunda.connector.agenticai.a2a.client.common.configuration.A2aClientCommonConfigurationProperties.TransportConfiguration;
import io.camunda.connector.agenticai.a2a.client.common.sdk.convert.A2aDocumentToPartConverter;
import io.camunda.connector.agenticai.a2a.client.common.sdk.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.common.sdk.grpc.ManagedChannelFactory;
import io.camunda.connector.agenticai.a2a.client.common.sdk.http.A2aHttpClientFactory;
import java.util.function.BiConsumer;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransport;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransportConfig;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfig;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AuthenticationInfo;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class A2aSdkClientFactoryImpl implements A2aSdkClientFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(A2aSdkClientFactoryImpl.class);

  private final JSONRPCTransportConfig jsonrpcTransportConfig;
  private final RestTransportConfig restTransportConfig;
  private final TransportConfiguration transportConfiguration;
  private final A2aSendMessageResponseHandler responseHandler;
  private final A2aDocumentToPartConverter documentToPartConverter;
  private final A2aSdkObjectConverter sdkObjectConverter;

  public A2aSdkClientFactoryImpl(
      TransportConfiguration transportConfiguration,
      A2aHttpClientFactory httpClientFactory,
      A2aSendMessageResponseHandler responseHandler,
      A2aDocumentToPartConverter documentToPartConverter,
      A2aSdkObjectConverter sdkObjectConverter) {
    this.transportConfiguration = transportConfiguration;
    this.responseHandler = responseHandler;
    this.documentToPartConverter = documentToPartConverter;
    this.sdkObjectConverter = sdkObjectConverter;
    A2AHttpClient httpClient = httpClientFactory.createHttpClient();
    this.jsonrpcTransportConfig = new JSONRPCTransportConfig(httpClient);
    this.restTransportConfig = new RestTransportConfig(httpClient);
  }

  @Override
  public A2aSdkClient buildClient(
      A2aSdkAgentCard agentCard, A2aSdkClientListener listener, A2aSdkClientConfig config) {
    // Create a channel factory that will create and track gRPC channels
    final ManagedChannelFactory managedChannelFactory =
        new ManagedChannelFactory(transportConfiguration.grpc().useTls());
    final GrpcTransportConfig grpcTransportConfig =
        new GrpcTransportConfig(managedChannelFactory::create);

    final var clientConfigBuilder =
        new ClientConfig.Builder()
            .setStreaming(false)
            .setPolling(!config.blocking())
            .setHistoryLength(config.historyLength());
    final var pushNotificationConfig = config.pushNotificationConfig();
    if (pushNotificationConfig != null) {
      clientConfigBuilder.setTaskPushNotificationConfig(
          createPushNotificationConfig(pushNotificationConfig));
    }

    BiConsumer<ClientEvent, AgentCard> consumer =
        (event, ignore) -> {
          try {
            listener.onResult(responseHandler.handleClientEvent(event));
          } catch (Exception e) {
            listener.onError(e);
          }
        };

    try {
      Client client =
          Client.builder(agentCard.sdkAgentCard())
              .clientConfig(clientConfigBuilder.build())
              .addConsumer(consumer)
              .withTransport(JSONRPCTransport.class, jsonrpcTransportConfig)
              .withTransport(RestTransport.class, restTransportConfig)
              .withTransport(GrpcTransport.class, grpcTransportConfig)
              .build();
      return new A2aSdkClient(
          client, managedChannelFactory, documentToPartConverter, sdkObjectConverter);
    } catch (A2AClientException e) {
      // Ensure cleanup on early failures
      managedChannelFactory.close();
      throw new RuntimeException(e);
    }
  }

  private static TaskPushNotificationConfig createPushNotificationConfig(
      A2aSdkClientConfig.PushNotificationConfig config) {
    final var token = StringUtils.isBlank(config.token()) ? null : config.token();

    // A2A 1.0 supports a single authentication scheme per push notification configuration.
    AuthenticationInfo authentication = null;
    final var schemes = config.authSchemes();
    if (CollectionUtils.isNotEmpty(schemes)) {
      if (schemes.size() > 1) {
        LOGGER.warn(
            "A2A 1.0 supports a single push notification authentication scheme. Using '{}' and ignoring {}.",
            schemes.getFirst(),
            schemes.subList(1, schemes.size()));
      }
      authentication = new AuthenticationInfo(schemes.getFirst(), config.credentials());
    }

    return new TaskPushNotificationConfig(null, null, config.url(), token, authentication, null);
  }
}
