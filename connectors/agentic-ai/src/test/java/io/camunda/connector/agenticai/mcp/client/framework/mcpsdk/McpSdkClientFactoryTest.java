/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.AuthenticationConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.AuthenticationConfiguration.AuthenticationType;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration.McpClientType;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.SseHttpMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.StdioMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.StreamableHttpMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientDelegate;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.framework.bootstrap.McpClientHeadersSupplierFactory;
import io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc.McpSdkMcpClientDelegate;
import io.camunda.connector.agenticai.mcp.client.model.auth.BearerAuthentication;
import io.modelcontextprotocol.client.McpSyncClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@WireMockTest
@ExtendWith(MockitoExtension.class)
class McpSdkClientFactoryTest {

  private static final String CLIENT_ID = "test-client-id";

  private static final AuthenticationConfiguration BEARER_AUTHENTICATION =
      AuthenticationConfiguration.builder()
          .type(AuthenticationType.BEARER)
          .bearer(new BearerAuthentication("test-token"))
          .build();

  private static final Map<String, String> EXPECTED_HEADERS =
      Map.of(
          "X-Dummy", "Test",
          "Authorization", "Bearer test-token");

  @Mock private McpClientHeadersSupplierFactory headersSupplierFactory;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private McpSdkClientFactory factory;

  @BeforeEach
  void setUp() {
    WireMock.reset();
    factory = new McpSdkClientFactory(objectMapper, headersSupplierFactory);
  }

  @Test
  void createsStdioMcpClient() {
    final var stdioConfig = createStdioMcpClientTransportConfiguration(List.of("arg1", "arg2"));
    final var client =
        factory.createClient(
            CLIENT_ID, createMcpClientConfiguration(McpClientType.STDIO, stdioConfig, null, null));

    assertClientIsOfCorrectType(client);
  }

  @Test
  void createsStdioMcpClientWithoutArguments() {
    final var stdioConfig = createStdioMcpClientTransportConfiguration(List.of());
    final var client =
        factory.createClient(
            CLIENT_ID, createMcpClientConfiguration(McpClientType.STDIO, stdioConfig, null, null));

    assertClientIsOfCorrectType(client);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/mcp", "/mcp/cluster", "/abc123/mcp/cluster"})
  void createsStreamableHttpMcpClient(String endpoint, WireMockRuntimeInfo wireMock) {
    final var streamableHttpTransportConfig =
        createStreamableHttpMcpClientTransportConfiguration(wireMock, endpoint);
    when(headersSupplierFactory.createHttpHeadersSupplier(streamableHttpTransportConfig))
        .thenReturn(() -> EXPECTED_HEADERS);

    final var client =
        factory.createClient(
            CLIENT_ID,
            createMcpClientConfiguration(
                McpClientType.HTTP, null, streamableHttpTransportConfig, null));

    assertClientIsOfCorrectType(client);

    assertThatThrownBy(() -> client.listTools(AllowDenyList.allowingEverything()))
        .hasMessage("Client failed to initialize listing tools");

    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              WireMock.verify(
                  1,
                  postRequestedFor(urlPathEqualTo(endpoint))
                      .withHeader("Content-Type", equalTo("application/json"))
                      .withHeader("Accept", equalTo("application/json, text/event-stream"))
                      .withHeader("Authorization", equalTo("Bearer test-token"))
                      .withHeader("X-Dummy", equalTo("Test")));
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"/sse", "/sse/cluster", "/abc123/sse/cluster"})
  void createsSseHttpMcpClient(String endpoint, WireMockRuntimeInfo wireMock) {
    final var sseConfig = createSseHttpMcpClientTransportConfiguration(wireMock, endpoint);
    when(headersSupplierFactory.createHttpHeadersSupplier(sseConfig))
        .thenReturn(() -> EXPECTED_HEADERS);

    final var client =
        factory.createClient(
            CLIENT_ID, createMcpClientConfiguration(McpClientType.SSE, null, null, sseConfig));

    assertClientIsOfCorrectType(client);

    assertThatThrownBy(() -> client.listTools(AllowDenyList.allowingEverything()))
        .hasMessage("Client failed to initialize listing tools");

    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              WireMock.verify(
                  1,
                  getRequestedFor(urlPathEqualTo(endpoint))
                      .withHeader("Accept", equalTo("text/event-stream"))
                      .withHeader("Authorization", equalTo("Bearer test-token"))
                      .withHeader("X-Dummy", equalTo("Test")));
            });
  }

  @Test
  void doesNotFailWithNullTimeouts() {
    final var stdioConfig = createStdioMcpClientTransportConfiguration(List.of());
    final var client =
        factory.createClient(
            CLIENT_ID,
            new McpClientConfiguration(
                true, McpClientType.STDIO, stdioConfig, null, null, null, null, null));

    assertClientIsOfCorrectType(client);
  }

  private void assertClientIsOfCorrectType(McpClientDelegate clientDelegate) {
    assertThat(clientDelegate).isInstanceOf(McpSdkMcpClientDelegate.class);
    var internalClient = ReflectionTestUtils.getField(clientDelegate, "delegate");
    assertThat(internalClient).isInstanceOf(McpSyncClient.class);
  }

  private static McpClientConfiguration createMcpClientConfiguration(
      McpClientType type,
      StdioMcpClientTransportConfiguration stdioConfig,
      StreamableHttpMcpClientTransportConfiguration httpConfig,
      SseHttpMcpClientTransportConfiguration sseConfig) {
    return new McpClientConfiguration(
        true,
        type,
        stdioConfig,
        httpConfig,
        sseConfig,
        Duration.ofMillis(100),
        Duration.ofMillis(200),
        Duration.ofMillis(300));
  }

  private static StdioMcpClientTransportConfiguration createStdioMcpClientTransportConfiguration(
      List<String> args) {
    return new StdioMcpClientTransportConfiguration("command", args, Map.of("ENV_VAR", "value"));
  }

  private static StreamableHttpMcpClientTransportConfiguration
      createStreamableHttpMcpClientTransportConfiguration(
          WireMockRuntimeInfo wireMock, String endpoint) {
    return new StreamableHttpMcpClientTransportConfiguration(
        "http://localhost:%d%s".formatted(wireMock.getHttpPort(), endpoint),
        Map.of("X-Dummy", "Test"),
        BEARER_AUTHENTICATION,
        Duration.ofMillis(200));
  }

  private static SseHttpMcpClientTransportConfiguration
      createSseHttpMcpClientTransportConfiguration(WireMockRuntimeInfo wireMock, String endpoint) {
    return new SseHttpMcpClientTransportConfiguration(
        "http://localhost:%d%s".formatted(wireMock.getHttpPort(), endpoint),
        Map.of("X-Dummy", "Test"),
        BEARER_AUTHENTICATION,
        Duration.ofMillis(200));
  }
}
