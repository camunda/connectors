/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.AuthenticationConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.AuthenticationConfiguration.AuthenticationType;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration.McpClientType;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.SseHttpMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.StdioMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.StreamableHttpMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.execution.McpClientDelegate;
import io.camunda.connector.agenticai.mcp.client.framework.bootstrap.McpClientHeadersSupplierFactory;
import io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.rpc.McpSdkMcpClientDelegate;
import io.camunda.connector.agenticai.mcp.client.model.auth.BearerAuthentication;
import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.modelcontextprotocol.client.McpSyncClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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

  @Mock private JdkHttpClientProxyConfigurator httpClientProxyConfigurator;
  @Mock private McpClientHeadersSupplierFactory headersSupplierFactory;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private McpSdkClientFactory factory;

  @BeforeEach
  void setUp() {
    factory =
        new McpSdkClientFactory(objectMapper, httpClientProxyConfigurator, headersSupplierFactory);
  }

  @Test
  void createsStdioMcpClient() {
    final var stdioConfig = createStdioMcpClientTransportConfiguration(List.of("arg1", "arg2"));
    final var client =
        factory.createClient(
            CLIENT_ID, createMcpClientConfiguration(McpClientType.STDIO, stdioConfig, null, null));

    assertClientIsOfCorrectType(client);
    verifyNoInteractions(httpClientProxyConfigurator);
  }

  @Test
  void createsStdioMcpClientWithoutArguments() {
    final var stdioConfig = createStdioMcpClientTransportConfiguration(List.of());
    final var client =
        factory.createClient(
            CLIENT_ID, createMcpClientConfiguration(McpClientType.STDIO, stdioConfig, null, null));

    assertClientIsOfCorrectType(client);
  }

  @Test
  void createsStreamableHttpMcpClient() {
    final var streamableHttpTransportConfig = createStreamableHttpMcpClientTransportConfiguration();
    when(headersSupplierFactory.createHttpHeadersSupplier(streamableHttpTransportConfig))
        .thenReturn(() -> EXPECTED_HEADERS);

    final var client =
        factory.createClient(
            CLIENT_ID,
            createMcpClientConfiguration(
                McpClientType.HTTP, null, streamableHttpTransportConfig, null));

    assertClientIsOfCorrectType(client);
    verify(httpClientProxyConfigurator).configure(any());
  }

  @Test
  void createsSseHttpMcpClient() {
    final var sseConfig = createSseHttpMcpClientTransportConfiguration();
    when(headersSupplierFactory.createHttpHeadersSupplier(sseConfig))
        .thenReturn(() -> EXPECTED_HEADERS);

    final var client =
        factory.createClient(
            CLIENT_ID, createMcpClientConfiguration(McpClientType.SSE, null, null, sseConfig));

    assertClientIsOfCorrectType(client);
    verify(httpClientProxyConfigurator).configure(any());
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
        Duration.ofSeconds(1),
        Duration.ofSeconds(2),
        Duration.ofSeconds(3));
  }

  private static StdioMcpClientTransportConfiguration createStdioMcpClientTransportConfiguration(
      List<String> args) {
    return new StdioMcpClientTransportConfiguration("command", args, Map.of("ENV_VAR", "value"));
  }

  private static StreamableHttpMcpClientTransportConfiguration
      createStreamableHttpMcpClientTransportConfiguration() {
    return new StreamableHttpMcpClientTransportConfiguration(
        "http://localhost:123456/mcp",
        Map.of("X-Dummy", "Test"),
        BEARER_AUTHENTICATION,
        Duration.ofSeconds(15));
  }

  private static SseHttpMcpClientTransportConfiguration
      createSseHttpMcpClientTransportConfiguration() {
    return new SseHttpMcpClientTransportConfiguration(
        "http://localhost:123456/sse",
        Map.of("X-Dummy", "Test"),
        BEARER_AUTHENTICATION,
        Duration.ofSeconds(15));
  }
}
