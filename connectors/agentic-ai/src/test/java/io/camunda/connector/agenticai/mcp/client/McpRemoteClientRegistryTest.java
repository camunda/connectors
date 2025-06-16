/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Ticker;
import dev.langchain4j.mcp.client.McpClient;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry.McpRemoteClientIdentifier;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.HttpMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpRemoteClientConfigurationProperties.ClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpRemoteClientConfigurationProperties.ClientConfiguration.ClientCacheConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientRequest.McpRemoteClientRequestData.HttpConnectionConfiguration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("resource")
@ExtendWith(MockitoExtension.class)
class McpRemoteClientRegistryTest {

  private static final long PROCESS_DEFINITION_KEY = 123456L;
  private static final String ELEMENT_ID = "TestClientElement";
  private static final McpRemoteClientIdentifier CLIENT_ID =
      new McpRemoteClientIdentifier(PROCESS_DEFINITION_KEY, ELEMENT_ID);

  private static final String SSE_URL = "http://localhost:123456/sse";
  private static final Map<String, String> HTTP_HEADERS = Map.of("Authorization", "dummy");
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(12);

  private static final HttpConnectionConfiguration HTTP_CONFIG =
      new HttpConnectionConfiguration(SSE_URL, HTTP_HEADERS, HTTP_TIMEOUT);

  private static final McpClientConfiguration EXPECTED_CLIENT_CONFIGURATION =
      new McpClientConfiguration(
          true,
          null,
          new HttpMcpClientTransportConfiguration(SSE_URL, HTTP_HEADERS, HTTP_TIMEOUT, true, false),
          null,
          null,
          null);

  @Mock private McpClientFactory<McpClient> clientFactory;
  @Mock private McpClient client;

  @Test
  void createsRemoteMcpClient() {
    final var registry = new McpRemoteClientRegistry<>(createClientConfig(), clientFactory);

    when(clientFactory.createClient(CLIENT_ID.toString(), EXPECTED_CLIENT_CONFIGURATION))
        .thenReturn(client);

    final var resolvedClient = registry.getClient(CLIENT_ID, HTTP_CONFIG);

    assertThat(resolvedClient).isNotNull().isSameAs(this.client);
  }

  @Test
  void returnsCachedValue() {
    final var registry = new McpRemoteClientRegistry<>(createClientConfig(), clientFactory);

    when(clientFactory.createClient(CLIENT_ID.toString(), EXPECTED_CLIENT_CONFIGURATION))
        .thenReturn(client);

    final var resolvedClient1 = registry.getClient(CLIENT_ID, HTTP_CONFIG);
    final var resolvedClient2 = registry.getClient(CLIENT_ID, HTTP_CONFIG);

    assertThat(resolvedClient1).isNotNull().isSameAs(client).isSameAs(resolvedClient2);
  }

  @ParameterizedTest
  @CsvSource({"false,3", "true,0"})
  void doesNotCacheIfCacheDisabledOrConfiguredToZeroCacheSize(boolean enabled, long maximumSize)
      throws InterruptedException {
    final var registry =
        new McpRemoteClientRegistry<>(
            createClientConfig(
                new ClientCacheConfiguration(enabled, maximumSize, Duration.ofMinutes(10))),
            clientFactory);
    final var client2 = mock(McpClient.class);

    when(clientFactory.createClient(CLIENT_ID.toString(), EXPECTED_CLIENT_CONFIGURATION))
        .thenReturn(client, client2);

    final var resolvedClient1 = registry.getClient(CLIENT_ID, HTTP_CONFIG);
    Thread.sleep(Duration.ofMillis(10));
    final var resolvedClient2 = registry.getClient(CLIENT_ID, HTTP_CONFIG);

    assertThat(resolvedClient1).isNotNull().isSameAs(client).isNotSameAs(resolvedClient2);
    assertThat(resolvedClient2).isNotNull().isSameAs(client2);
  }

  @Test
  void closesClientsOnSizeEviction() {
    final var registry = new McpRemoteClientRegistry<>(createClientConfig(), clientFactory);
    final List<McpClient> mockClients = new ArrayList<>();

    doAnswer(
            i -> {
              final var mockClient = mock(McpClient.class);
              mockClients.add(mockClient);
              return mockClient;
            })
        .when(clientFactory)
        .createClient(anyString(), any());

    final var resolvedClients =
        IntStream.range(1, 6)
            .mapToObj(
                i ->
                    registry.getClient(
                        new McpRemoteClientIdentifier(PROCESS_DEFINITION_KEY, "client-" + i),
                        HTTP_CONFIG))
            .toList();

    assertThat(resolvedClients).hasSize(5).containsExactlyElementsOf(mockClients);

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(mockClients)
                  .filteredOnAssertions(client -> verify(client).close())
                  .hasSize(2); // max cache size is configured to 3
            });
  }

  @Test
  void closesClientsOnTimeEviction() {
    final var fakeTicker = new TestTicker();
    try (final var mockedTicker = Mockito.mockStatic(Ticker.class, Answers.CALLS_REAL_METHODS)) {
      mockedTicker.when(Ticker::systemTicker).thenReturn(fakeTicker);

      final var registry = new McpRemoteClientRegistry<>(createClientConfig(), clientFactory);
      when(clientFactory.createClient(CLIENT_ID.toString(), EXPECTED_CLIENT_CONFIGURATION))
          .thenReturn(client);
      final var resolvedClient = registry.getClient(CLIENT_ID, HTTP_CONFIG);

      fakeTicker.advance(Duration.ofMinutes(10).plusSeconds(1)); // exceed cache expiration time

      await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(resolvedClient).close());
    }
  }

  @Test
  void closesClientsOnClose() throws Exception {
    try (final var registry = new McpRemoteClientRegistry<>(createClientConfig(), clientFactory)) {
      when(clientFactory.createClient(CLIENT_ID.toString(), EXPECTED_CLIENT_CONFIGURATION))
          .thenReturn(client);

      final var resolvedClient = registry.getClient(CLIENT_ID, HTTP_CONFIG);

      assertThat(resolvedClient).isNotNull().isSameAs(client);
    }

    verify(client).close();
  }

  private ClientConfiguration createClientConfig() {
    return createClientConfig(new ClientCacheConfiguration(true, 3L, Duration.ofMinutes(10)));
  }

  private ClientConfiguration createClientConfig(ClientCacheConfiguration cacheConfiguration) {
    return new ClientConfiguration(true, false, cacheConfiguration);
  }

  private static class TestTicker implements Ticker {
    private final AtomicLong nanos = new AtomicLong(0);

    @Override
    public long read() {
      return nanos.get();
    }

    public void advance(Duration duration) {
      nanos.addAndGet(duration.toNanos());
    }
  }
}
