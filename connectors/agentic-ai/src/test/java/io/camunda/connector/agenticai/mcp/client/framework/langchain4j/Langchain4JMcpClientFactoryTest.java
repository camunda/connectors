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

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.HttpMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.StdioMcpClientTransportConfiguration;
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

  @Mock private DefaultMcpClient mcpClient;
  @Mock private StdioMcpTransport stdioMcpTransport;
  @Mock private HttpMcpTransport httpMcpTransport;

  private final Langchain4JMcpClientFactory factory = new Langchain4JMcpClientFactory();

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
                factory.createClient(CLIENT_ID, createMcpClientConfiguration(stdioConfig, null));

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
                factory.createClient(CLIENT_ID, createMcpClientConfiguration(stdioConfig, null));

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
  void createsHttpMcpClient(boolean logRequests, boolean logResponses) {
    withMockedMcpClientBuilder(
        mockedMcpClientConstruction -> {
          try (MockedConstruction<HttpMcpTransport.Builder> mockedTransportBuilder =
              mockConstruction(
                  HttpMcpTransport.Builder.class,
                  withSettings().defaultAnswer(CALLS_REAL_METHODS),
                  (mock, context) -> doReturn(httpMcpTransport).when(mock).build())) {
            final var httpConfig =
                createHttpMcpClientTransportConfiguration(logRequests, logResponses);
            final var client =
                factory.createClient(CLIENT_ID, createMcpClientConfiguration(null, httpConfig));

            assertThat(client).isEqualTo(mcpClient);

            final var transportBuilder = mockedTransportBuilder.constructed().getFirst();
            verify(transportBuilder).sseUrl(httpConfig.sseUrl());
            verify(transportBuilder).timeout(httpConfig.timeout());
            verify(transportBuilder).logRequests(httpConfig.logRequests());
            verify(transportBuilder).logResponses(httpConfig.logResponses());

            verifyMcpClientBuilder(
                mockedMcpClientConstruction.constructed().getFirst(), httpMcpTransport);
          }
        });
  }

  @Test
  void prefersStdioOverHttp() {
    withMockedMcpClientBuilder(
        mockedMcpClientConstruction -> {
          try (MockedConstruction<StdioMcpTransport.Builder> mockedStdioTransportBuilder =
                  mockConstruction(
                      StdioMcpTransport.Builder.class,
                      withSettings().defaultAnswer(CALLS_REAL_METHODS),
                      (mock, context) -> doReturn(stdioMcpTransport).when(mock).build());
              MockedConstruction<HttpMcpTransport.Builder> mockedHttpTransportBuilder =
                  mockConstruction(
                      HttpMcpTransport.Builder.class,
                      withSettings().defaultAnswer(CALLS_REAL_METHODS),
                      (mock, context) -> doReturn(httpMcpTransport).when(mock).build())) {
            final var stdioConfig = createStdioMcpClientTransportConfiguration(List.of(), true);
            final var httpConfig = createHttpMcpClientTransportConfiguration(true, true);
            final var client =
                factory.createClient(
                    CLIENT_ID, createMcpClientConfiguration(stdioConfig, httpConfig));

            assertThat(client).isEqualTo(mcpClient);

            verifyMcpClientBuilder(
                mockedMcpClientConstruction.constructed().getFirst(), stdioMcpTransport);

            assertThat(mockedStdioTransportBuilder.constructed()).hasSize(1);
            assertThat(mockedHttpTransportBuilder.constructed()).isEmpty();
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
                    new McpClientConfiguration(true, stdioConfig, null, null, null, null));

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

  private McpClientConfiguration createMcpClientConfiguration(
      StdioMcpClientTransportConfiguration stdioConfig,
      HttpMcpClientTransportConfiguration httpConfig) {
    return new McpClientConfiguration(
        true,
        stdioConfig,
        httpConfig,
        Duration.ofSeconds(1),
        Duration.ofSeconds(2),
        Duration.ofSeconds(3));
  }

  private StdioMcpClientTransportConfiguration createStdioMcpClientTransportConfiguration(
      List<String> args, boolean logEvents) {
    return new StdioMcpClientTransportConfiguration(
        "command", args, Map.of("ENV_VAR", "value"), logEvents);
  }

  private HttpMcpClientTransportConfiguration createHttpMcpClientTransportConfiguration(
      boolean logRequests, boolean logResponses) {
    return new HttpMcpClientTransportConfiguration(
        "http://localhost:123456/sse",
        Map.of("Authorization", "Bearer token"),
        Duration.ofSeconds(15),
        logRequests,
        logResponses);
  }
}
