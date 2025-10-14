/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.sdk;

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
import io.camunda.connector.agenticai.a2a.client.api.A2aChannelProvider;
import io.camunda.connector.agenticai.a2a.client.api.A2aSdkClientFactory;
import io.grpc.ManagedChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.apache.commons.collections4.CollectionUtils;

public class A2aSdkClientFactoryImpl implements A2aSdkClientFactory {

  private final JSONRPCTransportConfig jsonrpcTransportConfig;
  private final RestTransportConfig restTransportConfig;
  private final A2aChannelProvider channelProvider;

  // Track channels per client instance
  private final Map<Client, List<ManagedChannel>> channelsByClient = new ConcurrentHashMap<>();

  public A2aSdkClientFactoryImpl(A2aChannelProvider channelProvider) {
    this.jsonrpcTransportConfig = new JSONRPCTransportConfig();
    this.restTransportConfig = new RestTransportConfig();
    this.channelProvider = channelProvider;
  }

  @Override
  public Client buildClient(
      AgentCard agentCard, BiConsumer<ClientEvent, AgentCard> consumer, int historyLength) {
    final List<ManagedChannel> createdChannels = new CopyOnWriteArrayList<>();

    final GrpcTransportConfig grpcTransportConfig =
        new GrpcTransportConfig(
            agentUrl -> {
              ManagedChannel channel = channelProvider.create(agentUrl);
              createdChannels.add(channel);
              return channel;
            });

    try {
      Client client =
          Client.builder(agentCard)
              .clientConfig(
                  new ClientConfig.Builder()
                      .setStreaming(false)
                      .setPolling(true)
                      .setHistoryLength(historyLength)
                      .build())
              .addConsumer(consumer)
              .withTransport(JSONRPCTransport.class, jsonrpcTransportConfig)
              .withTransport(RestTransport.class, restTransportConfig)
              .withTransport(GrpcTransport.class, grpcTransportConfig)
              .build();
      channelsByClient.put(client, createdChannels);
      return client;
    } catch (A2AClientException e) {
      // Ensure best-effort cleanup on early failures
      shutdownChannels(createdChannels);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void release(Client client) {
    try {
      if (client != null) {
        client.close();
      }
    } catch (Throwable ignored) {
      // best-effort close; continue to shut down channels
    }

    final List<ManagedChannel> channels = channelsByClient.remove(client);
    if (CollectionUtils.isNotEmpty(channels)) {
      shutdownChannels(channels);
    }
  }

  private static void shutdownChannels(List<ManagedChannel> channels) {
    for (ManagedChannel channel : channels) {
      try {
        channel.shutdown();
        if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
          channel.shutdownNow();
          channel.awaitTermination(2, TimeUnit.SECONDS);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        channel.shutdownNow();
      }
    }
    channels.clear();
  }
}
