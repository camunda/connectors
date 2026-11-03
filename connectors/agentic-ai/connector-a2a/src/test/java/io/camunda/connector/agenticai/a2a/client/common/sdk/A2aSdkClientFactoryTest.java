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

import io.camunda.connector.agenticai.a2a.client.common.configuration.A2aClientCommonConfigurationProperties.TransportConfiguration;
import io.camunda.connector.agenticai.a2a.client.common.configuration.A2aClientCommonConfigurationProperties.TransportConfiguration.GrpcConfiguration;
import io.camunda.connector.agenticai.a2a.client.common.sdk.convert.A2aDocumentToPartConverter;
import io.camunda.connector.agenticai.a2a.client.common.sdk.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.common.sdk.http.A2aHttpClientFactory;
import java.util.List;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;

class A2aSdkClientFactoryTest {

  private static A2aHttpClientFactory mockHttpClientFactory() {
    A2aHttpClientFactory factory = mock(A2aHttpClientFactory.class);
    when(factory.createHttpClient()).thenReturn(mock(A2AHttpClient.class));
    return factory;
  }

  private static A2aSdkClientFactory newFactory(TransportConfiguration transportConfig) {
    return new A2aSdkClientFactoryImpl(
        transportConfig,
        mockHttpClientFactory(),
        mock(A2aSendMessageResponseHandler.class),
        mock(A2aDocumentToPartConverter.class),
        mock(A2aSdkObjectConverter.class));
  }

  private static A2aSdkAgentCard agentCard(String transport, String url) {
    return new A2aSdkAgentCard(
        AgentCard.builder()
            .name("Test agent")
            .description("Test agent description")
            .version("1.0.0")
            .capabilities(AgentCapabilities.builder().build())
            .defaultInputModes(List.of("text"))
            .defaultOutputModes(List.of("text"))
            .skills(List.of())
            .supportedInterfaces(List.of(new AgentInterface(transport, url)))
            .build());
  }

  @Test
  void createsClientWithGrpcTransportWithTls() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aSdkClientFactory factory = newFactory(transportConfig);
    A2aSdkAgentCard agentCard = agentCard(TransportProtocol.GRPC.asString(), "localhost:50051");

    A2aSdkClient client = buildClient(agentCard, factory);

    assertThat(client).isNotNull();

    client.close();
  }

  @Test
  void createsClientWithGrpcTransportWithoutTls() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(false));
    A2aSdkClientFactory factory = newFactory(transportConfig);
    A2aSdkAgentCard agentCard = agentCard(TransportProtocol.GRPC.asString(), "localhost:50051");

    A2aSdkClient client = buildClient(agentCard, factory);

    assertThat(client).isNotNull();

    client.close();
  }

  @Test
  void createsClientWithRestTransport() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aSdkClientFactory factory = newFactory(transportConfig);
    A2aSdkAgentCard agentCard =
        agentCard(TransportProtocol.HTTP_JSON.asString(), "http://localhost:8080");

    A2aSdkClient client = buildClient(agentCard, factory);

    assertThat(client).isNotNull();

    client.close();
  }

  @Test
  void createsClientWithJsonRpcTransport() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aSdkClientFactory factory = newFactory(transportConfig);
    A2aSdkAgentCard agentCard =
        agentCard(TransportProtocol.JSONRPC.asString(), "http://localhost:8080/rpc");

    A2aSdkClient client = buildClient(agentCard, factory);

    assertThat(client).isNotNull();

    client.close();
  }

  @Test
  void factoryCanCreateMultipleClients() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aSdkClientFactory factory = newFactory(transportConfig);

    A2aSdkAgentCard agentCard1 = agentCard(TransportProtocol.GRPC.asString(), "localhost:50051");

    A2aSdkAgentCard agentCard2 =
        agentCard(TransportProtocol.HTTP_JSON.asString(), "http://localhost:8080");

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
    A2aSdkClientFactory factory = newFactory(transportConfig);
    A2aSdkAgentCard agentCard = agentCard(TransportProtocol.GRPC.asString(), "localhost:50051");

    A2aSdkClient client = buildClient(agentCard, factory);

    // Should not throw
    client.close();
    // Calling close again should also not throw
    client.close();
  }

  @Test
  void throwsRuntimeExceptionWhenClientBuildingFails() {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aSdkClientFactory factory = newFactory(transportConfig);
    A2aSdkAgentCard agentCard = agentCard("UNKNOWN", "localhost:50051");

    assertThatThrownBy(() -> buildClient(agentCard, factory)).isInstanceOf(RuntimeException.class);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = {true, false})
  void passesCorrectParametersToClientConfigBuilderWithoutPushNotification(Boolean blocking) {
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aSdkClientFactory factory = newFactory(transportConfig);
    A2aSdkAgentCard agentCard = agentCard(TransportProtocol.GRPC.asString(), "localhost:50051");

    try (var mockClient = mockStatic(Client.class, Answers.CALLS_REAL_METHODS)) {
      var clientBuilder = spy(Client.builder(agentCard.sdkAgentCard()));
      mockClient.when(() -> Client.builder(agentCard.sdkAgentCard())).thenReturn(clientBuilder);

      boolean isBlocking = blocking != null && blocking;
      A2aSdkClientConfig config = new A2aSdkClientConfig(5, isBlocking, null);
      A2aSdkClient client = factory.buildClient(agentCard, A2aSdkClientListener.NOOP, config);

      verify(clientBuilder)
          .clientConfig(
              assertArg(clientConfig -> {
                assertThat(clientConfig.isStreaming()).isFalse();
                assertThat(clientConfig.getHistoryLength()).isEqualTo(5);
                assertThat(clientConfig.isPolling()).isEqualTo(!isBlocking);
                assertThat(clientConfig.getTaskPushNotificationConfig()).isNull();
              }));

      assertThat(client).isNotNull();
      client.close();
    }
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"bearer", "api-key"})
  void passesCorrectParametersToClientConfigBuilderWithPushNotification(String authScheme) {
    String credentials = authScheme != null ? "test-credentials-" + authScheme : null;
    List<String> authSchemes = authScheme != null ? List.of(authScheme) : List.of();
    final var transportConfig = new TransportConfiguration(new GrpcConfiguration(true));
    A2aSdkClientFactory factory = newFactory(transportConfig);
    A2aSdkAgentCard agentCard = agentCard(TransportProtocol.GRPC.asString(), "localhost:50051");

    try (var mockClient = mockStatic(Client.class, Answers.CALLS_REAL_METHODS)) {
      var clientBuilder = spy(Client.builder(agentCard.sdkAgentCard()));
      mockClient.when(() -> Client.builder(agentCard.sdkAgentCard())).thenReturn(clientBuilder);

      var pushNotificationConfig =
          new A2aSdkClientConfig.PushNotificationConfig(
              "https://example.com/webhook", "my-token", authSchemes, credentials);
      A2aSdkClientConfig config = new A2aSdkClientConfig(5, false, pushNotificationConfig);
      A2aSdkClient client = factory.buildClient(agentCard, A2aSdkClientListener.NOOP, config);

      verify(clientBuilder)
          .clientConfig(
              assertArg(clientConfig -> {
                assertThat(clientConfig.isStreaming()).isFalse();
                assertThat(clientConfig.getHistoryLength()).isEqualTo(5);
                assertThat(clientConfig.isPolling()).isTrue();
                var pushConfig = clientConfig.getTaskPushNotificationConfig();
                assertThat(pushConfig).isNotNull();
                assertThat(pushConfig.url()).isEqualTo("https://example.com/webhook");
                assertThat(pushConfig.token()).isEqualTo("my-token");
                var authInfo = pushConfig.authentication();
                if (authScheme == null) {
                  assertThat(authInfo).isNull();
                  return;
                }
                assertThat(authInfo).isNotNull();
                assertThat(authInfo.scheme()).isEqualTo(authScheme);
                var creds = authInfo.credentials();
                if (credentials != null) {
                  assertThat(creds).isEqualTo(credentials);
                } else {
                  assertThat(creds).isNull();
                }
              }));

      assertThat(client).isNotNull();
      client.close();
    }
  }

  private static A2aSdkClient buildClient(A2aSdkAgentCard agentCard, A2aSdkClientFactory factory) {
    return factory.buildClient(
        agentCard,
        A2aSdkClientListener.NOOP,
        new A2aSdkClientConfigBuilder().historyLength(3).blocking(true).build());
  }
}
