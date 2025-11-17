/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.AuthenticationConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.AuthenticationConfiguration.AuthenticationType;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration.McpClientType;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.SseHttpMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.StdioMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.StreamableHttpMcpClientTransportConfiguration;
import io.camunda.connector.http.client.authentication.OAuthService;
import io.camunda.connector.http.client.client.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Langchain4JMcpClientFactoryTest {

  private static final String CLIENT_ID = "test-client-id";

  private static final AuthenticationConfiguration NO_AUTHENTICATION =
      AuthenticationConfiguration.builder().type(AuthenticationType.NONE).build();

  @Mock private DefaultMcpClient mcpClient;
  @Mock private StdioMcpTransport stdioMcpTransport;
  @Mock private StreamableHttpMcpTransport streamableHttpMcpTransport;
  @Mock private HttpMcpTransport sseMcpTransport;

  @Mock private OAuthService oAuthService;
  @Mock private HttpClient httpClient;

  private final Langchain4JMcpClientFactory factory =
      new Langchain4JMcpClientFactory(
          new Langchain4JMcpClientHeadersSupplierFactory(
              oAuthService, httpClient, new ObjectMapper()));

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void createsStdioMcpClient(boolean logEvents) {
    withMockedMcpClientBuilder(
        mockedMcpClientConstruction -> {
          try (MockedConstruction<StdioMcpTransport.Builder> mockedTransportBuilder =
              mockConstruction(
                  StdioMcpTransport.Builder.class,
                  withSettings().defaultAnswer(CALLS_REAL_METHODS),
                  (mock, context) -> doReturn(stdioMcpTransport).when(mock).build())) {
            final var stdioConfig =
                createStdioMcpClientTransportConfiguration(List.of("arg1", "arg2"), logEvents);
            final var client =
                factory.createClient(
                    CLIENT_ID,
                    createMcpClientConfiguration(McpClientType.STDIO, stdioConfig, null, null));

            assertThat(client).isEqualTo(mcpClient);

            final var transportBuilder = mockedTransportBuilder.constructed().getFirst();
            verify(transportBuilder).command(List.of("command", "arg1", "arg2"));
            verify(transportBuilder).environment(stdioConfig.env());
            verify(transportBuilder).logEvents(stdioConfig.logEvents());

            verifyMcpClientBuilder(
                mockedMcpClientConstruction.constructed().getFirst(), stdioMcpTransport);
          }
        });
  }

  @Test
  void createsStdioMcpClientWithoutArguments() {
    withMockedMcpClientBuilder(
        mockedMcpClientConstruction -> {
          try (MockedConstruction<StdioMcpTransport.Builder> mockedTransportBuilder =
              mockConstruction(
                  StdioMcpTransport.Builder.class,
                  withSettings().defaultAnswer(CALLS_REAL_METHODS),
                  (mock, context) -> doReturn(stdioMcpTransport).when(mock).build())) {
            final var stdioConfig = createStdioMcpClientTransportConfiguration(List.of(), false);
            final var client =
                factory.createClient(
                    CLIENT_ID,
                    createMcpClientConfiguration(McpClientType.STDIO, stdioConfig, null, null));

            assertThat(client).isEqualTo(mcpClient);

            final var transportBuilder = mockedTransportBuilder.constructed().getFirst();
            verify(transportBuilder).command(List.of("command"));
            verify(transportBuilder).environment(stdioConfig.env());
            verify(transportBuilder).logEvents(stdioConfig.logEvents());

            verifyMcpClientBuilder(
                mockedMcpClientConstruction.constructed().getFirst(), stdioMcpTransport);
          }
        });
  }

  @ParameterizedTest
  @CsvSource({"true,true", "true,false", "false,true", "false,false"})
  void createsStreamableHttpMcpClient(boolean logRequests, boolean logResponses) {
    withMockedMcpClientBuilder(
        mockedMcpClientConstruction -> {
          try (MockedConstruction<StreamableHttpMcpTransport.Builder> mockedTransportBuilder =
              mockConstruction(
                  StreamableHttpMcpTransport.Builder.class,
                  withSettings().defaultAnswer(CALLS_REAL_METHODS),
                  (mock, context) -> doReturn(streamableHttpMcpTransport).when(mock).build())) {
            final var streamableHttpTransportConfig =
                createStreamableHttpMcpClientTransportConfiguration(logRequests, logResponses);
            final var client =
                factory.createClient(
                    CLIENT_ID,
                    createMcpClientConfiguration(
                        McpClientType.HTTP, null, streamableHttpTransportConfig, null));

            assertThat(client).isEqualTo(mcpClient);

            final var transportBuilder = mockedTransportBuilder.constructed().getFirst();
            verify(transportBuilder).url(streamableHttpTransportConfig.url());
            verify(transportBuilder).timeout(streamableHttpTransportConfig.timeout());
            verify(transportBuilder).customHeaders(streamableHttpTransportConfig.headers());
            verify(transportBuilder).logRequests(streamableHttpTransportConfig.logRequests());
            verify(transportBuilder).logResponses(streamableHttpTransportConfig.logResponses());

            verifyMcpClientBuilder(
                mockedMcpClientConstruction.constructed().getFirst(), streamableHttpMcpTransport);
          }
        });
  }

  @ParameterizedTest
  @CsvSource({"true,true", "true,false", "false,true", "false,false"})
  void createsSseHttpMcpClient(boolean logRequests, boolean logResponses) {
    withMockedMcpClientBuilder(
        mockedMcpClientConstruction -> {
          try (MockedConstruction<HttpMcpTransport.Builder> mockedTransportBuilder =
              mockConstruction(
                  HttpMcpTransport.Builder.class,
                  withSettings().defaultAnswer(CALLS_REAL_METHODS),
                  (mock, context) -> doReturn(sseMcpTransport).when(mock).build())) {
            final var sseConfig =
                createSseHttpMcpClientTransportConfiguration(logRequests, logResponses);
            final var client =
                factory.createClient(
                    CLIENT_ID,
                    createMcpClientConfiguration(McpClientType.SSE, null, null, sseConfig));

            assertThat(client).isEqualTo(mcpClient);

            final var transportBuilder = mockedTransportBuilder.constructed().getFirst();
            verify(transportBuilder).sseUrl(sseConfig.url());
            verify(transportBuilder).timeout(sseConfig.timeout());
            verify(transportBuilder).customHeaders(sseConfig.headers());
            verify(transportBuilder).logRequests(sseConfig.logRequests());
            verify(transportBuilder).logResponses(sseConfig.logResponses());

            verifyMcpClientBuilder(
                mockedMcpClientConstruction.constructed().getFirst(), sseMcpTransport);
          }
        });
  }

  @Test
  void doesNotApplyTimeoutsAndReconnectIntervalIfNull() {
    withMockedMcpClientBuilder(
        mockedMcpClientConstruction -> {
          try (MockedConstruction<StdioMcpTransport.Builder> mockedTransportBuilder =
              mockConstruction(
                  StdioMcpTransport.Builder.class,
                  withSettings().defaultAnswer(CALLS_REAL_METHODS),
                  (mock, context) -> doReturn(stdioMcpTransport).when(mock).build())) {
            final var stdioConfig = createStdioMcpClientTransportConfiguration(List.of(), true);
            final var client =
                factory.createClient(
                    CLIENT_ID,
                    new McpClientConfiguration(
                        true, McpClientType.STDIO, stdioConfig, null, null, null, null, null));

            assertThat(client).isEqualTo(mcpClient);

            final var mcpClientBuilder = mockedMcpClientConstruction.constructed().getFirst();
            verify(mcpClientBuilder).key(CLIENT_ID);
            verify(mcpClientBuilder).transport(any(McpTransport.class));
            verify(mcpClientBuilder, never()).initializationTimeout(any());
            verify(mcpClientBuilder, never()).toolExecutionTimeout(any());
            verify(mcpClientBuilder, never()).reconnectInterval(any());
          }
        });
  }

  private void withMockedMcpClientBuilder(
      ThrowingConsumer<MockedConstruction<DefaultMcpClient.Builder>> testLogic) {
    try (MockedConstruction<DefaultMcpClient.Builder> mockedBuilder =
        mockConstruction(
            DefaultMcpClient.Builder.class,
            (mock, context) -> {
              when(mock.key(any())).thenReturn(mock);
              when(mock.transport(any())).thenReturn(mock);
              when(mock.build()).thenReturn(mcpClient);
            })) {
      testLogic.accept(mockedBuilder);
    }
  }

  private void verifyMcpClientBuilder(
      DefaultMcpClient.Builder mcpClientBuilder, McpTransport expectedTransport) {
    verify(mcpClientBuilder).key(CLIENT_ID);
    verify(mcpClientBuilder).transport(expectedTransport);
    verify(mcpClientBuilder).initializationTimeout(Duration.ofSeconds(1));
    verify(mcpClientBuilder).toolExecutionTimeout(Duration.ofSeconds(2));
    verify(mcpClientBuilder).reconnectInterval(Duration.ofSeconds(3));
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
      List<String> args, boolean logEvents) {
    return new StdioMcpClientTransportConfiguration(
        "command", args, Map.of("ENV_VAR", "value"), logEvents);
  }

  private static StreamableHttpMcpClientTransportConfiguration
      createStreamableHttpMcpClientTransportConfiguration(
          boolean logRequests, boolean logResponses) {
    return new StreamableHttpMcpClientTransportConfiguration(
        "http://localhost:123456/mcp",
        Map.of("Authorization", "Bearer token"),
        NO_AUTHENTICATION,
        Duration.ofSeconds(15),
        logRequests,
        logResponses);
  }

  private static SseHttpMcpClientTransportConfiguration
      createSseHttpMcpClientTransportConfiguration(boolean logRequests, boolean logResponses) {
    return new SseHttpMcpClientTransportConfiguration(
        "http://localhost:123456/sse",
        Map.of("Authorization", "Bearer token"),
        NO_AUTHENTICATION,
        Duration.ofSeconds(15),
        logRequests,
        logResponses);
  }
}
