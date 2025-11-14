/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.sdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.a2a.client.Client;
import io.a2a.spec.AgentCard;
import io.a2a.spec.TransportProtocol;
import io.camunda.connector.agenticai.a2a.client.common.configuration.A2aClientCommonConfigurationProperties.TransportConfiguration;
import io.camunda.connector.agenticai.a2a.client.common.configuration.A2aClientCommonConfigurationProperties.TransportConfiguration.GrpcConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;

class A2ASdkClientFactoryTest {

  @Test
  void createsClientWithGrpcTransportWithTls() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aSdkClientFactory factory = new A2aSdkClientFactoryImpl(transportConfig);
    AgentCard agentCard = mock(AgentCard.class);

    when(agentCard.preferredTransport()).thenReturn(TransportProtocol.GRPC.asString());
    when(agentCard.url()).thenReturn("localhost:50051");

    A2aSdkClient client = buildClient(agentCard, factory);

    assertThat(client).isNotNull();

    client.close();
  }

  @Test
  void createsClientWithGrpcTransportWithoutTls() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(false));
    A2aSdkClientFactory factory = new A2aSdkClientFactoryImpl(transportConfig);
    AgentCard agentCard = mock(AgentCard.class);

    when(agentCard.preferredTransport()).thenReturn(TransportProtocol.GRPC.asString());
    when(agentCard.url()).thenReturn("localhost:50051");

    A2aSdkClient client = buildClient(agentCard, factory);

    assertThat(client).isNotNull();

    client.close();
  }

  @Test
  void createsClientWithRestTransport() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aSdkClientFactory factory = new A2aSdkClientFactoryImpl(transportConfig);
    AgentCard agentCard = mock(AgentCard.class);

    when(agentCard.preferredTransport()).thenReturn(TransportProtocol.HTTP_JSON.asString());
    when(agentCard.url()).thenReturn("http://localhost:8080");

    A2aSdkClient client = buildClient(agentCard, factory);

    assertThat(client).isNotNull();

    client.close();
  }

  @Test
  void createsClientWithJsonRpcTransport() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aSdkClientFactory factory = new A2aSdkClientFactoryImpl(transportConfig);
    AgentCard agentCard = mock(AgentCard.class);

    when(agentCard.preferredTransport()).thenReturn(TransportProtocol.JSONRPC.asString());
    when(agentCard.url()).thenReturn("http://localhost:8080/rpc");

    A2aSdkClient client = buildClient(agentCard, factory);

    assertThat(client).isNotNull();

    client.close();
  }

  @Test
  void factoryCanCreateMultipleClients() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aSdkClientFactory factory = new A2aSdkClientFactoryImpl(transportConfig);

    AgentCard agentCard1 = mock(AgentCard.class);
    when(agentCard1.preferredTransport()).thenReturn(TransportProtocol.GRPC.asString());
    when(agentCard1.url()).thenReturn("localhost:50051");

    AgentCard agentCard2 = mock(AgentCard.class);
    when(agentCard2.preferredTransport()).thenReturn(TransportProtocol.HTTP_JSON.asString());
    when(agentCard2.url()).thenReturn("http://localhost:8080");

    A2aSdkClient client1 = buildClient(agentCard1, factory);
    A2aSdkClient client2 = buildClient(agentCard2, factory);

    assertThat(client1).isNotNull();
    assertThat(client2).isNotNull();
    assertThat(client1).isNotSameAs(client2);

    client1.close();
    client2.close();
  }

  @Test
  void closingClientDoesNotThrowException() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aSdkClientFactory factory = new A2aSdkClientFactoryImpl(transportConfig);
    AgentCard agentCard = mock(AgentCard.class);

    when(agentCard.preferredTransport()).thenReturn(TransportProtocol.GRPC.asString());
    when(agentCard.url()).thenReturn("localhost:50051");

    A2aSdkClient client = buildClient(agentCard, factory);

    // Should not throw
    client.close();
    // Calling close again should also not throw
    client.close();
  }

  @Test
  void throwsRuntimeExceptionWhenClientBuildingFails() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aSdkClientFactory factory = new A2aSdkClientFactoryImpl(transportConfig);
    AgentCard agentCard = mock(AgentCard.class);
    // Set up an invalid configuration that will cause A2AClientException
    when(agentCard.preferredTransport()).thenReturn(null);
    when(agentCard.url()).thenReturn("localhost:50051");

    assertThatThrownBy(() -> buildClient(agentCard, factory)).isInstanceOf(RuntimeException.class);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = {true, false})
  void passesCorrectParametersToClientConfigBuilder(Boolean supportPolling) {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aSdkClientFactory factory = new A2aSdkClientFactoryImpl(transportConfig);
    AgentCard agentCard = mock(AgentCard.class);

    when(agentCard.preferredTransport()).thenReturn(TransportProtocol.GRPC.asString());
    when(agentCard.url()).thenReturn("localhost:50051");

    try (var mockClient = mockStatic(Client.class, Answers.CALLS_REAL_METHODS)) {
      var clientBuilder = spy(Client.builder(agentCard));
      mockClient.when(() -> Client.builder(agentCard)).thenReturn(clientBuilder);

      A2aSdkClientConfig config = new A2aSdkClientConfig(5, supportPolling);
      A2aSdkClient client = factory.buildClient(agentCard, (event, card) -> {}, config);

      verify(clientBuilder)
          .clientConfig(
              assertArg(
                  clientConfig -> {
                    assertThat(clientConfig.isStreaming()).isFalse();
                    assertThat(clientConfig.getHistoryLength()).isEqualTo(5);
                    assertThat(clientConfig.isPolling())
                        .isEqualTo(supportPolling == null || supportPolling);
                  }));

      assertThat(client).isNotNull();
      client.close();
    }
  }

  private static A2aSdkClient buildClient(AgentCard agentCard, A2aSdkClientFactory factory) {
    return factory.buildClient(agentCard, (event, card) -> {}, new A2aSdkClientConfig(3, false));
  }
}
