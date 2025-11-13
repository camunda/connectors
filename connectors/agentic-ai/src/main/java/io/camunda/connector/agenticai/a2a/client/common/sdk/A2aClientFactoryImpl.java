/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk;

import io.a2a.client.Client;
import io.a2a.client.ClientEvent;
import io.a2a.client.config.ClientConfig;
import io.a2a.client.transport.grpc.GrpcTransport;
import io.a2a.client.transport.grpc.GrpcTransportConfig;
import io.a2a.client.transport.jsonrpc.JSONRPCTransport;
import io.a2a.client.transport.jsonrpc.JSONRPCTransportConfig;
import io.a2a.client.transport.rest.RestTransport;
import io.a2a.client.transport.rest.RestTransportConfig;
import io.a2a.spec.A2AClientException;
import io.a2a.spec.AgentCard;
import io.camunda.connector.agenticai.a2a.client.common.A2aClientFactory;
import io.camunda.connector.agenticai.a2a.client.common.configuration.A2aCommonConfigurationProperties.TransportConfiguration;
import io.camunda.connector.agenticai.a2a.client.common.sdk.grpc.ManagedChannelFactory;
import java.util.function.BiConsumer;

public class A2aClientFactoryImpl implements A2aClientFactory {
  private final JSONRPCTransportConfig jsonrpcTransportConfig;
  private final RestTransportConfig restTransportConfig;
  private final TransportConfiguration transportConfiguration;

  public A2aClientFactoryImpl(TransportConfiguration transportConfiguration) {
    this.transportConfiguration = transportConfiguration;
    this.jsonrpcTransportConfig = new JSONRPCTransportConfig();
    this.restTransportConfig = new RestTransportConfig();
  }

  @Override
  public A2aClient buildClient(
      AgentCard agentCard, BiConsumer<ClientEvent, AgentCard> consumer, A2aClientConfig config) {
    // Create a channel factory that will create and track gRPC channels
    final ManagedChannelFactory managedChannelFactory =
        new ManagedChannelFactory(transportConfiguration.grpc().useTls());
    final GrpcTransportConfig grpcTransportConfig =
        new GrpcTransportConfig(managedChannelFactory::create);
    try {
      Client client =
          Client.builder(agentCard)
              .clientConfig(
                  new ClientConfig.Builder()
                      .setStreaming(false)
                      .setPolling(config.supportPolling() == null || config.supportPolling())
                      .setHistoryLength(config.historyLength())
                      .build())
              .addConsumer(consumer)
              .withTransport(JSONRPCTransport.class, jsonrpcTransportConfig)
              .withTransport(RestTransport.class, restTransportConfig)
              .withTransport(GrpcTransport.class, grpcTransportConfig)
              .build();
      return new A2aClient(client, managedChannelFactory);
    } catch (A2AClientException e) {
      // Ensure cleanup on early failures
      managedChannelFactory.close();
      throw new RuntimeException(e);
    }
  }
}
