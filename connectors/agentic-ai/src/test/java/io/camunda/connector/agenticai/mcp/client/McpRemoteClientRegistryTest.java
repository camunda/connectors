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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Ticker;
import dev.langchain4j.mcp.client.McpClient;
import io.camunda.connector.agenticai.mcp.client.McpRemoteClientRegistry.McpRemoteClientIdentifier;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.AuthenticationConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.AuthenticationConfiguration.AuthenticationType;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration.McpClientType;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.SseHttpMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.StreamableHttpMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpRemoteClientConfigurationProperties.ClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpRemoteClientConfigurationProperties.ClientConfiguration.ClientCacheConfiguration;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientDelegate;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.SseHttpMcpRemoteClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.SseHttpMcpRemoteClientTransportConfiguration.SseHttpMcpRemoteClientConnection;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.StreamableHttpMcpRemoteClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.StreamableHttpMcpRemoteClientTransportConfiguration.StreamableHttpMcpRemoteClientConnection;
import io.camunda.connector.agenticai.mcp.client.model.auth.Authentication;
import io.camunda.connector.agenticai.mcp.client.model.auth.BasicAuthentication;
import io.camunda.connector.agenticai.mcp.client.model.auth.BearerAuthentication;
import io.camunda.connector.agenticai.mcp.client.model.auth.OAuthAuthentication;
import io.camunda.connector.agenticai.mcp.client.model.auth.OAuthAuthentication.ClientAuthenticationMethod;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@SuppressWarnings("resource")
@ExtendWith(MockitoExtension.class)
class McpRemoteClientRegistryTest {

  private static final long PROCESS_DEFINITION_KEY = 123456L;
  private static final String ELEMENT_ID = "TestClientElement";
  private static final McpRemoteClientIdentifier CLIENT_ID =
      new McpRemoteClientIdentifier(PROCESS_DEFINITION_KEY, ELEMENT_ID);

  private static final String STREAMABLE_HTTP_URL = "http://localhost:123456/mcp";
  private static final String SSE_URL = "http://localhost:123456/sse";
  private static final Map<String, String> HTTP_HEADERS = Map.of("X-Foo", "dummy");
  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(12);

  private static final AuthenticationConfiguration NO_AUTHENTICATION =
      AuthenticationConfiguration.builder().type(AuthenticationType.NONE).build();

  private static final AuthenticationConfiguration BASIC_AUTHENTICATION =
      AuthenticationConfiguration.builder()
          .type(AuthenticationType.BASIC)
          .basic(new BasicAuthentication("test-username", "test-password"))
          .build();

  private static final AuthenticationConfiguration BEARER_AUTHENTICATION =
      AuthenticationConfiguration.builder()
          .type(AuthenticationType.BEARER)
          .bearer(new BearerAuthentication("test-token"))
          .build();

  private static final AuthenticationConfiguration OAUTH_AUTHENTICATION =
      AuthenticationConfiguration.builder()
          .type(AuthenticationType.OAUTH)
          .oauth(
              new OAuthAuthentication(
                  "http://auth.example.com/token",
                  "my-client-id",
                  "my-client-secret",
                  "https://api.example.com",
                  ClientAuthenticationMethod.BASIC_AUTH_HEADER,
                  "openid my-scope"))
          .build();

  private final McpClientConfiguration EXPECTED_STREAMABLE_HTTP_CLIENT_CONFIGURATION =
      createExpectedStreamableHttpClientConfiguration(NO_AUTHENTICATION);

  private final StreamableHttpMcpRemoteClientTransportConfiguration
      STREAMABLE_HTTP_TRANSPORT_CONFIG =
          createStreamableHttpTransportConfiguration(NO_AUTHENTICATION.authentication());

  @Mock private McpClientFactory clientFactory;
  @Mock private McpClientDelegate client;

  public static Stream<AuthenticationConfiguration> authenticationConfigurations() {
    return Stream.of(
        NO_AUTHENTICATION, BASIC_AUTHENTICATION, BEARER_AUTHENTICATION, OAUTH_AUTHENTICATION);
  }

  @ParameterizedTest
  @MethodSource("authenticationConfigurations")
  void createsStreamableHttpRemoteMcpClient(AuthenticationConfiguration authentication) {
    final var registry = new McpRemoteClientRegistry(createClientConfig(), clientFactory);

    when(clientFactory.createClient(
            CLIENT_ID.toString(), createExpectedStreamableHttpClientConfiguration(authentication)))
        .thenReturn(client);

    final var resolvedClient =
        registry.getClient(
            CLIENT_ID,
            createStreamableHttpTransportConfiguration(authentication.authentication()),
            false);

    assertThat(resolvedClient).isNotNull().isSameAs(this.client);
  }

  @ParameterizedTest
  @MethodSource("authenticationConfigurations")
  void createsSseRemoteMcpClient(AuthenticationConfiguration authentication) {
    final var registry = new McpRemoteClientRegistry(createClientConfig(), clientFactory);

    when(clientFactory.createClient(
            CLIENT_ID.toString(), createExpectedSseHttpClientConfiguration(authentication)))
        .thenReturn(client);

    final var resolvedClient =
        registry.getClient(
            CLIENT_ID, createSseTransportConfiguration(authentication.authentication()), false);

    assertThat(resolvedClient).isNotNull().isSameAs(this.client);
  }

  @Test
  void returnsCachedValue() {
    final var registry = new McpRemoteClientRegistry(createClientConfig(), clientFactory);

    when(clientFactory.createClient(
            CLIENT_ID.toString(), EXPECTED_STREAMABLE_HTTP_CLIENT_CONFIGURATION))
        .thenReturn(client);

    final var resolvedClient1 =
        registry.getClient(CLIENT_ID, STREAMABLE_HTTP_TRANSPORT_CONFIG, true);
    final var resolvedClient2 =
        registry.getClient(CLIENT_ID, STREAMABLE_HTTP_TRANSPORT_CONFIG, true);

    assertThat(resolvedClient1).isNotNull().isSameAs(client).isSameAs(resolvedClient2);

    verify(clientFactory, times(1)).createClient(any(), any());
  }

  @ParameterizedTest
  @CsvSource({"false,3", "true,0"})
  void doesNotCacheIfCacheDisabledOrConfiguredToZeroCacheSize(boolean enabled, long maximumSize)
      throws Exception {
    final var registry =
        new McpRemoteClientRegistry(
            createClientConfig(
                new ClientCacheConfiguration(enabled, maximumSize, Duration.ofMinutes(10))),
            clientFactory);
    final var client2 = mock(McpClientDelegate.class);

    when(clientFactory.createClient(
            CLIENT_ID.toString(), EXPECTED_STREAMABLE_HTTP_CLIENT_CONFIGURATION))
        .thenReturn(client, client2);

    // with cache disabled or size=0, getClient with cacheable=true still doesn't cache
    final var resolvedClient1 =
        registry.getClient(CLIENT_ID, STREAMABLE_HTTP_TRANSPORT_CONFIG, true);
    Thread.sleep(Duration.ofMillis(10));
    final var resolvedClient2 =
        registry.getClient(CLIENT_ID, STREAMABLE_HTTP_TRANSPORT_CONFIG, true);

    // should create new instances each time
    assertThat(resolvedClient1).isNotNull().isSameAs(client).isNotSameAs(resolvedClient2);
    assertThat(resolvedClient2).isNotNull().isSameAs(client2);

    // verify clients are created twice
    verify(clientFactory, times(2))
        .createClient(CLIENT_ID.toString(), EXPECTED_STREAMABLE_HTTP_CLIENT_CONFIGURATION);
  }

  @Test
  void cacheableParameterControlsCaching() {
    final var registry = new McpRemoteClientRegistry(createClientConfig(), clientFactory);
    final var client2 = mock(McpClientDelegate.class);

    when(clientFactory.createClient(
            CLIENT_ID.toString(), EXPECTED_STREAMABLE_HTTP_CLIENT_CONFIGURATION))
        .thenReturn(client, client2);

    // first call with cacheable=true should cache
    final var cachedClient1 = registry.getClient(CLIENT_ID, STREAMABLE_HTTP_TRANSPORT_CONFIG, true);
    final var cachedClient2 = registry.getClient(CLIENT_ID, STREAMABLE_HTTP_TRANSPORT_CONFIG, true);

    // should return same cached instance
    assertThat(cachedClient1)
        .isNotNull()
        .isSameAs(client)
        .isSameAs(cachedClient2)
        .isNotSameAs(client2);

    // call with cacheable=false should create new instance
    final var nonCachedClient =
        registry.getClient(CLIENT_ID, STREAMABLE_HTTP_TRANSPORT_CONFIG, false);

    assertThat(nonCachedClient).isNotNull().isSameAs(client2).isNotSameAs(cachedClient1);

    // factory should be called twice: once for cache, once for non-cached
    verify(clientFactory, times(2))
        .createClient(CLIENT_ID.toString(), EXPECTED_STREAMABLE_HTTP_CLIENT_CONFIGURATION);
  }

  @Test
  void closesCachedClientsOnSizeEviction() {
    final var registry = new McpRemoteClientRegistry(createClientConfig(), clientFactory);
    final List<McpClientDelegate> mockClients = new ArrayList<>();

    doAnswer(
            i -> {
              final var mockClient = mock(McpClientDelegate.class);
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
                        STREAMABLE_HTTP_TRANSPORT_CONFIG,
                        true))
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
  void closesCachedClientsOnTimeEviction() {
    final var fakeTicker = new TestTicker();
    try (final var mockedTicker = Mockito.mockStatic(Ticker.class, Answers.CALLS_REAL_METHODS)) {
      mockedTicker.when(Ticker::systemTicker).thenReturn(fakeTicker);

      final var registry = new McpRemoteClientRegistry(createClientConfig(), clientFactory);
      when(clientFactory.createClient(
              CLIENT_ID.toString(), EXPECTED_STREAMABLE_HTTP_CLIENT_CONFIGURATION))
          .thenReturn(client);
      final var resolvedClient =
          registry.getClient(CLIENT_ID, STREAMABLE_HTTP_TRANSPORT_CONFIG, true);

      fakeTicker.advance(Duration.ofMinutes(10).plusSeconds(1)); // exceed cache expiration time
      getCache(registry).cleanUp();

      await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(resolvedClient).close());
    }
  }

  @Test
  void closesCachedClientsOnClose() throws Exception {
    try (final var registry = new McpRemoteClientRegistry(createClientConfig(), clientFactory)) {
      when(clientFactory.createClient(
              CLIENT_ID.toString(), EXPECTED_STREAMABLE_HTTP_CLIENT_CONFIGURATION))
          .thenReturn(client);

      final var resolvedClient =
          registry.getClient(CLIENT_ID, STREAMABLE_HTTP_TRANSPORT_CONFIG, true);

      assertThat(resolvedClient).isNotNull().isSameAs(client);
    }

    verify(client).close();
  }

  @Test
  void nonCachedClientsAreNotClosedAutomatically() throws Exception {
    final var registry = new McpRemoteClientRegistry(createClientConfig(), clientFactory);

    when(clientFactory.createClient(
            CLIENT_ID.toString(), EXPECTED_STREAMABLE_HTTP_CLIENT_CONFIGURATION))
        .thenReturn(client);

    // get non-cached client
    final var nonCachedClient =
        registry.getClient(CLIENT_ID, STREAMABLE_HTTP_TRANSPORT_CONFIG, false);

    assertThat(nonCachedClient).isNotNull().isSameAs(client);

    // close the registry - should NOT close non-cached clients
    registry.close();

    verify(client, never()).close();
  }

  @Test
  void closingNonCachedClientExplicitly() throws Exception {
    final var registry = new McpRemoteClientRegistry(createClientConfig(), clientFactory);

    when(clientFactory.createClient(
            CLIENT_ID.toString(), EXPECTED_STREAMABLE_HTTP_CLIENT_CONFIGURATION))
        .thenReturn(client);

    // get non-cached client
    final var nonCachedClient =
        registry.getClient(CLIENT_ID, STREAMABLE_HTTP_TRANSPORT_CONFIG, false);

    assertThat(nonCachedClient).isNotNull().isSameAs(client);

    // caller is responsible for closing non-cached client
    registry.closeClient(CLIENT_ID, nonCachedClient);

    verify(client).close();
  }

  @Test
  void mixedCachingScenarios() {
    final var registry = new McpRemoteClientRegistry(createClientConfig(), clientFactory);
    final var client2 = mock(McpClientDelegate.class);
    final var client3 = mock(McpClientDelegate.class);

    when(clientFactory.createClient(
            CLIENT_ID.toString(), EXPECTED_STREAMABLE_HTTP_CLIENT_CONFIGURATION))
        .thenReturn(client, client2, client3);

    // create cached client
    final var cachedClient = registry.getClient(CLIENT_ID, STREAMABLE_HTTP_TRANSPORT_CONFIG, true);

    // create non-cached client (should be different instance)
    final var nonCachedClient1 =
        registry.getClient(CLIENT_ID, STREAMABLE_HTTP_TRANSPORT_CONFIG, false);

    // get cached client again (should be same as first)
    final var cachedClientAgain =
        registry.getClient(CLIENT_ID, STREAMABLE_HTTP_TRANSPORT_CONFIG, true);

    // create another non-cached client (should be different from all)
    final var nonCachedClient2 =
        registry.getClient(CLIENT_ID, STREAMABLE_HTTP_TRANSPORT_CONFIG, false);

    assertThat(cachedClient).isSameAs(client).isSameAs(cachedClientAgain);
    assertThat(nonCachedClient1).isSameAs(client2).isNotSameAs(cachedClient);
    assertThat(nonCachedClient2)
        .isSameAs(client3)
        .isNotSameAs(cachedClient)
        .isNotSameAs(nonCachedClient1);

    // factory should be called 3 times: once for cache, twice for non-cached
    verify(clientFactory, times(3))
        .createClient(CLIENT_ID.toString(), EXPECTED_STREAMABLE_HTTP_CLIENT_CONFIGURATION);
  }

  private static StreamableHttpMcpRemoteClientTransportConfiguration
      createStreamableHttpTransportConfiguration(Authentication authentication) {
    return new StreamableHttpMcpRemoteClientTransportConfiguration(
        new StreamableHttpMcpRemoteClientConnection(
            authentication, STREAMABLE_HTTP_URL, HTTP_HEADERS, HTTP_TIMEOUT));
  }

  private static McpClientConfiguration createExpectedStreamableHttpClientConfiguration(
      AuthenticationConfiguration authentication) {
    return new McpClientConfiguration(
        true,
        McpClientType.HTTP,
        null,
        new StreamableHttpMcpClientTransportConfiguration(
            STREAMABLE_HTTP_URL, HTTP_HEADERS, authentication, HTTP_TIMEOUT),
        null,
        null,
        null,
        null);
  }

  private static SseHttpMcpRemoteClientTransportConfiguration createSseTransportConfiguration(
      Authentication authentication) {
    return new SseHttpMcpRemoteClientTransportConfiguration(
        new SseHttpMcpRemoteClientConnection(authentication, SSE_URL, HTTP_HEADERS, HTTP_TIMEOUT));
  }

  private static McpClientConfiguration createExpectedSseHttpClientConfiguration(
      AuthenticationConfiguration authentication) {
    return new McpClientConfiguration(
        true,
        McpClientType.SSE,
        null,
        null,
        new SseHttpMcpClientTransportConfiguration(
            SSE_URL, HTTP_HEADERS, authentication, HTTP_TIMEOUT),
        null,
        null,
        null);
  }

  private ClientConfiguration createClientConfig() {
    return createClientConfig(new ClientCacheConfiguration(true, 3L, Duration.ofMinutes(10)));
  }

  private ClientConfiguration createClientConfig(ClientCacheConfiguration cacheConfiguration) {
    return new ClientConfiguration(cacheConfiguration);
  }

  @SuppressWarnings("unchecked")
  private Cache<@NonNull McpRemoteClientIdentifier, McpClient> getCache(
      McpRemoteClientRegistry registry) {
    return (Cache<@NonNull McpRemoteClientIdentifier, McpClient>)
        ReflectionTestUtils.getField(registry, "cache");
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
