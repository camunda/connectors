/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.a2a.spec.AgentCard;
import io.a2a.spec.TransportProtocol;
import io.camunda.connector.agenticai.a2a.client.api.A2aClientFactory;
import io.camunda.connector.agenticai.a2a.client.configuration.A2aConnectorConfigurationProperties.TransportConfiguration;
import io.camunda.connector.agenticai.a2a.client.configuration.A2aConnectorConfigurationProperties.TransportConfiguration.GrpcConfiguration;
import org.junit.jupiter.api.Test;

class A2aClientFactoryTest {

  @Test
  void createsClientWithGrpcTransportWithTls() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aClientFactory factory = new A2aClientFactoryImpl(transportConfig);
    AgentCard agentCard = mock(AgentCard.class);

    when(agentCard.preferredTransport()).thenReturn(TransportProtocol.GRPC.asString());
    when(agentCard.url()).thenReturn("localhost:50051");

    A2aClient a2aClient = buildClient(agentCard, factory);

    assertThat(a2aClient).isNotNull();

    a2aClient.close();
  }

  @Test
  void createsClientWithGrpcTransportWithoutTls() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(false));
    A2aClientFactory factory = new A2aClientFactoryImpl(transportConfig);
    AgentCard agentCard = mock(AgentCard.class);

    when(agentCard.preferredTransport()).thenReturn(TransportProtocol.GRPC.asString());
    when(agentCard.url()).thenReturn("localhost:50051");

    A2aClient a2aClient = buildClient(agentCard, factory);

    assertThat(a2aClient).isNotNull();

    a2aClient.close();
  }

  @Test
  void createsClientWithRestTransport() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aClientFactory factory = new A2aClientFactoryImpl(transportConfig);
    AgentCard agentCard = mock(AgentCard.class);

    when(agentCard.preferredTransport()).thenReturn(TransportProtocol.HTTP_JSON.asString());
    when(agentCard.url()).thenReturn("http://localhost:8080");

    A2aClient a2aClient = buildClient(agentCard, factory);

    assertThat(a2aClient).isNotNull();

    a2aClient.close();
  }

  @Test
  void createsClientWithJsonRpcTransport() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aClientFactory factory = new A2aClientFactoryImpl(transportConfig);
    AgentCard agentCard = mock(AgentCard.class);

    when(agentCard.preferredTransport()).thenReturn(TransportProtocol.JSONRPC.asString());
    when(agentCard.url()).thenReturn("http://localhost:8080/rpc");

    A2aClient a2aClient = buildClient(agentCard, factory);

    assertThat(a2aClient).isNotNull();

    a2aClient.close();
  }

  @Test
  void factoryCanCreateMultipleClients() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aClientFactory factory = new A2aClientFactoryImpl(transportConfig);

    AgentCard agentCard1 = mock(AgentCard.class);
    when(agentCard1.preferredTransport()).thenReturn(TransportProtocol.GRPC.asString());
    when(agentCard1.url()).thenReturn("localhost:50051");

    AgentCard agentCard2 = mock(AgentCard.class);
    when(agentCard2.preferredTransport()).thenReturn(TransportProtocol.HTTP_JSON.asString());
    when(agentCard2.url()).thenReturn("http://localhost:8080");

    A2aClient client1 = buildClient(agentCard1, factory);
    A2aClient client2 = buildClient(agentCard2, factory);

    assertThat(client1).isNotNull();
    assertThat(client2).isNotNull();
    assertThat(client1).isNotSameAs(client2);

    client1.close();
    client2.close();
  }

  @Test
  void closingClientDoesNotThrowException() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aClientFactory factory = new A2aClientFactoryImpl(transportConfig);
    AgentCard agentCard = mock(AgentCard.class);

    when(agentCard.preferredTransport()).thenReturn(TransportProtocol.GRPC.asString());
    when(agentCard.url()).thenReturn("localhost:50051");

    A2aClient a2aClient = buildClient(agentCard, factory);

    // Should not throw
    a2aClient.close();
    // Calling close again should also not throw
    a2aClient.close();
  }

  @Test
  void throwsRuntimeExceptionWhenClientBuildingFails() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aClientFactory factory = new A2aClientFactoryImpl(transportConfig);
    AgentCard agentCard = mock(AgentCard.class);
    // Set up an invalid configuration that will cause A2AClientException
    when(agentCard.preferredTransport()).thenReturn(null);
    when(agentCard.url()).thenReturn("localhost:50051");

    assertThatThrownBy(() -> buildClient(agentCard, factory)).isInstanceOf(RuntimeException.class);
  }

  private static A2aClient buildClient(AgentCard agentCard, A2aClientFactory factory) {
    return factory.buildClient(agentCard, (event, card) -> {}, 3);
  }
}
