/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.a2a.client.Client;
import io.a2a.spec.AgentCard;
import io.a2a.spec.TransportProtocol;
import io.camunda.connector.agenticai.a2a.client.api.A2aChannelProvider;
import io.camunda.connector.agenticai.a2a.client.api.A2aSdkClientFactory;
import io.camunda.connector.agenticai.a2a.client.sdk.A2aSdkClientFactoryImpl;
import io.grpc.ManagedChannel;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ClientFactoryTest {

  @Test
  void closesChannelsCreatedByChannelProvider_whenClientIsClosed() throws Exception {
    A2aChannelProvider provider = mock(A2aChannelProvider.class);

    ManagedChannel managedChannel = mock(ManagedChannel.class);
    when(managedChannel.awaitTermination(anyLong(), any(TimeUnit.class))).thenReturn(true);
    when(provider.create(any())).thenReturn(managedChannel);

    A2aSdkClientFactory factory = new A2aSdkClientFactoryImpl(provider);

    AgentCard agentCard = mock(AgentCard.class);
    when(agentCard.preferredTransport()).thenReturn(TransportProtocol.GRPC.asString());
    when(agentCard.url()).thenReturn("http://example.com");

    Client client = factory.buildClient(agentCard, (event, card) -> {});

    factory.release(client);

    verify(provider).create(any());
    verify(managedChannel).shutdown();
    verify(managedChannel).awaitTermination(anyLong(), any(TimeUnit.class));
    verify(managedChannel, never()).shutdownNow();
  }

  @Test
  void releaseInvokesClientClose() {
    A2aChannelProvider provider = mock(A2aChannelProvider.class);
    A2aSdkClientFactory factory = new A2aSdkClientFactoryImpl(provider);
    Client client = mock(Client.class);

    factory.release(client);

    verify(client).close();
  }

  @Test
  void closesChannelsWithShutdownNow_whenAwaitTerminationTimesOut() throws Exception {
    A2aChannelProvider provider = mock(A2aChannelProvider.class);

    ManagedChannel managedChannel = mock(ManagedChannel.class);
    when(managedChannel.awaitTermination(anyLong(), any(TimeUnit.class)))
        .thenReturn(false)
        .thenReturn(true);
    when(provider.create(any())).thenReturn(managedChannel);

    A2aSdkClientFactory factory = new A2aSdkClientFactoryImpl(provider);

    AgentCard agentCard = mock(AgentCard.class);
    when(agentCard.preferredTransport()).thenReturn(TransportProtocol.GRPC.asString());
    when(agentCard.url()).thenReturn("http://example.com");

    Client client = factory.buildClient(agentCard, (event, card) -> {});

    factory.release(client);

    verify(provider).create(any());
    verify(managedChannel).shutdown();
    verify(managedChannel).shutdownNow();
    // Verify awaitTermination was called at least twice (before and after shutdownNow)
    verify(managedChannel, times(2)).awaitTermination(anyLong(), any(TimeUnit.class));
  }
}
