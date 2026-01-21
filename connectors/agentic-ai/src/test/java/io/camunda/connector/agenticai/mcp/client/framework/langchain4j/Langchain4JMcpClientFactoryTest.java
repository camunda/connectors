///*
// * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
// * under one or more contributor license agreements. Licensed under a proprietary license.
// * See the License.txt file for more information. You may not use this file
// * except in compliance with the proprietary license.
// */
//package io.camunda.connector.agenticai.mcp.client.framework.langchain4j;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.CALLS_REAL_METHODS;
//import static org.mockito.Mockito.doReturn;
//import static org.mockito.Mockito.mockConstruction;
//import static org.mockito.Mockito.never;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//import static org.mockito.Mockito.withSettings;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import dev.langchain4j.mcp.client.DefaultMcpClient;
//import dev.langchain4j.mcp.client.transport.McpTransport;
//import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
//import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
//import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
//import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
//import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
//import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverterImpl;
//import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.AuthenticationConfiguration;
//import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.AuthenticationConfiguration.AuthenticationType;
//import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
//import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration.McpClientType;
//import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.SseHttpMcpClientTransportConfiguration;
//import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.StdioMcpClientTransportConfiguration;
//import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.StreamableHttpMcpClientTransportConfiguration;
//import io.camunda.connector.agenticai.mcp.client.model.auth.BearerAuthentication;
//import java.time.Duration;
//import java.util.List;
//import java.util.Map;
//import java.util.function.Supplier;
//import org.assertj.core.api.ThrowingConsumer;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.Captor;
//import org.mockito.Mock;
//import org.mockito.MockedConstruction;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//@ExtendWith(MockitoExtension.class)
//class Langchain4JMcpClientFactoryTest {
//
//  private static final String CLIENT_ID = "test-client-id";
//
//  private static final AuthenticationConfiguration BEARER_AUTHENTICATION =
//      AuthenticationConfiguration.builder()
//          .type(AuthenticationType.BEARER)
//          .bearer(new BearerAuthentication("test-token"))
//          .build();
//
//  private static final Map<String, String> EXPECTED_HEADERS =
//      Map.of(
//          "X-Dummy", "Test",
//          "Authorization", "Bearer test-token");
//
//  @Mock private DefaultMcpClient mcpClient;
//
//  @Mock private StdioMcpTransport stdioMcpTransport;
//  @Mock private StreamableHttpMcpTransport streamableHttpMcpTransport;
//  @Mock private HttpMcpTransport sseMcpTransport;
//
//  @Mock private Langchain4JMcpClientHeadersSupplierFactory headersSupplierFactory;
//
//  @Captor private ArgumentCaptor<Supplier<Map<String, String>>> headersSupplierCaptor;
//
//  private ObjectMapper objectMapper = new ObjectMapper();
//  private ToolSpecificationConverter toolSpecificationConverter =
//      new ToolSpecificationConverterImpl(new JsonSchemaConverter(objectMapper));
//  private Langchain4JMcpClientLoggingResolver loggingResolver;
//  private Langchain4JMcpClientFactory factory;
//
//  @BeforeEach
//  void setUp() {
//    loggingResolver = new Langchain4JMcpClientLoggingResolver();
//    factory =
//        new Langchain4JMcpClientFactory(
//            loggingResolver, headersSupplierFactory, objectMapper, toolSpecificationConverter);
//  }
//
//  @Test
//  void createsStdioMcpClient() {
//    withMockedMcpClientBuilder(
//        mockedMcpClientConstruction -> {
//          try (MockedConstruction<StdioMcpTransport.Builder> mockedTransportBuilder =
//              mockConstruction(
//                  StdioMcpTransport.Builder.class,
//                  withSettings().defaultAnswer(CALLS_REAL_METHODS),
//                  (mock, context) -> doReturn(stdioMcpTransport).when(mock).build())) {
//            final var stdioConfig =
//                createStdioMcpClientTransportConfiguration(List.of("arg1", "arg2"));
//            final var client =
//                factory.createClient(
//                    CLIENT_ID,
//                    createMcpClientConfiguration(McpClientType.STDIO, stdioConfig, null, null));
//
//            assertThat(client).isEqualTo(mcpClient);
//
//            final var transportBuilder = mockedTransportBuilder.constructed().getFirst();
//            verify(transportBuilder).command(List.of("command", "arg1", "arg2"));
//            verify(transportBuilder).environment(stdioConfig.env());
//            verify(transportBuilder).logEvents(false);
//
//            verifyMcpClientBuilder(
//                mockedMcpClientConstruction.constructed().getFirst(), stdioMcpTransport);
//          }
//        });
//  }
//
//  @Test
//  void configuresClientSpecificStdioLogging() {
//    loggingResolver.setLogStdioEvents(((clientId, config) -> !clientId.equals(CLIENT_ID)));
//
//    withMockedMcpClientBuilder(
//        mockedMcpClientConstruction -> {
//          try (MockedConstruction<StdioMcpTransport.Builder> mockedTransportBuilder =
//              mockConstruction(
//                  StdioMcpTransport.Builder.class,
//                  withSettings().defaultAnswer(CALLS_REAL_METHODS),
//                  (mock, context) -> doReturn(stdioMcpTransport).when(mock).build())) {
//            final var stdioConfig =
//                createStdioMcpClientTransportConfiguration(List.of("arg1", "arg2"));
//
//            factory.createClient(
//                CLIENT_ID,
//                createMcpClientConfiguration(McpClientType.STDIO, stdioConfig, null, null));
//            factory.createClient(
//                CLIENT_ID + "-2",
//                createMcpClientConfiguration(McpClientType.STDIO, stdioConfig, null, null));
//
//            final var transportBuilder1 = mockedTransportBuilder.constructed().get(0);
//            verify(transportBuilder1).logEvents(false);
//
//            final var transportBuilder2 = mockedTransportBuilder.constructed().get(1);
//            verify(transportBuilder2).logEvents(true);
//          }
//        });
//  }
//
//  @Test
//  void createsStdioMcpClientWithoutArguments() {
//    withMockedMcpClientBuilder(
//        mockedMcpClientConstruction -> {
//          try (MockedConstruction<StdioMcpTransport.Builder> mockedTransportBuilder =
//              mockConstruction(
//                  StdioMcpTransport.Builder.class,
//                  withSettings().defaultAnswer(CALLS_REAL_METHODS),
//                  (mock, context) -> doReturn(stdioMcpTransport).when(mock).build())) {
//            final var stdioConfig = createStdioMcpClientTransportConfiguration(List.of());
//            final var client =
//                factory.createClient(
//                    CLIENT_ID,
//                    createMcpClientConfiguration(McpClientType.STDIO, stdioConfig, null, null));
//
//            assertThat(client).isEqualTo(mcpClient);
//
//            final var transportBuilder = mockedTransportBuilder.constructed().getFirst();
//            verify(transportBuilder).command(List.of("command"));
//            verify(transportBuilder).environment(stdioConfig.env());
//            verify(transportBuilder).logEvents(false);
//
//            verifyMcpClientBuilder(
//                mockedMcpClientConstruction.constructed().getFirst(), stdioMcpTransport);
//          }
//        });
//  }
//
//  @Test
//  void createsStreamableHttpMcpClient() {
//    withMockedMcpClientBuilder(
//        mockedMcpClientConstruction -> {
//          try (MockedConstruction<StreamableHttpMcpTransport.Builder> mockedTransportBuilder =
//              mockConstruction(
//                  StreamableHttpMcpTransport.Builder.class,
//                  withSettings().defaultAnswer(CALLS_REAL_METHODS),
//                  (mock, context) -> doReturn(streamableHttpMcpTransport).when(mock).build())) {
//            final var streamableHttpTransportConfig =
//                createStreamableHttpMcpClientTransportConfiguration();
//            when(headersSupplierFactory.createHttpHeadersSupplier(streamableHttpTransportConfig))
//                .thenReturn(() -> EXPECTED_HEADERS);
//
//            final var client =
//                factory.createClient(
//                    CLIENT_ID,
//                    createMcpClientConfiguration(
//                        McpClientType.HTTP, null, streamableHttpTransportConfig, null));
//
//            assertThat(client).isEqualTo(mcpClient);
//
//            final var transportBuilder = mockedTransportBuilder.constructed().getFirst();
//            verify(transportBuilder).url(streamableHttpTransportConfig.url());
//            verify(transportBuilder).timeout(streamableHttpTransportConfig.timeout());
//            verify(transportBuilder).logRequests(false);
//            verify(transportBuilder).logResponses(false);
//
//            verify(transportBuilder).customHeaders(headersSupplierCaptor.capture());
//            assertThat(headersSupplierCaptor.getValue().get()).isEqualTo(EXPECTED_HEADERS);
//
//            verifyMcpClientBuilder(
//                mockedMcpClientConstruction.constructed().getFirst(), streamableHttpMcpTransport);
//          }
//        });
//  }
//
//  @Test
//  void configuresClientSpecificStreamableHttpMcpClientLogging() {
//    loggingResolver.setLogHttpRequests(((clientId, config) -> clientId.equals(CLIENT_ID)));
//    loggingResolver.setLogHttpResponses(((clientId, config) -> !clientId.equals(CLIENT_ID)));
//
//    withMockedMcpClientBuilder(
//        mockedMcpClientConstruction -> {
//          try (MockedConstruction<StreamableHttpMcpTransport.Builder> mockedTransportBuilder =
//              mockConstruction(
//                  StreamableHttpMcpTransport.Builder.class,
//                  withSettings().defaultAnswer(CALLS_REAL_METHODS),
//                  (mock, context) -> doReturn(streamableHttpMcpTransport).when(mock).build())) {
//            final var streamableHttpTransportConfig =
//                createStreamableHttpMcpClientTransportConfiguration();
//            when(headersSupplierFactory.createHttpHeadersSupplier(streamableHttpTransportConfig))
//                .thenReturn(() -> EXPECTED_HEADERS);
//
//            factory.createClient(
//                CLIENT_ID,
//                createMcpClientConfiguration(
//                    McpClientType.HTTP, null, streamableHttpTransportConfig, null));
//            factory.createClient(
//                CLIENT_ID + "-2",
//                createMcpClientConfiguration(
//                    McpClientType.HTTP, null, streamableHttpTransportConfig, null));
//
//            final var transportBuilder1 = mockedTransportBuilder.constructed().get(0);
//            verify(transportBuilder1).logRequests(true);
//            verify(transportBuilder1).logResponses(false);
//
//            final var transportBuilder2 = mockedTransportBuilder.constructed().get(1);
//            verify(transportBuilder2).logRequests(false);
//            verify(transportBuilder2).logResponses(true);
//          }
//        });
//  }
//
//  @Test
//  void createsSseHttpMcpClient() {
//    withMockedMcpClientBuilder(
//        mockedMcpClientConstruction -> {
//          try (MockedConstruction<HttpMcpTransport.Builder> mockedTransportBuilder =
//              mockConstruction(
//                  HttpMcpTransport.Builder.class,
//                  withSettings().defaultAnswer(CALLS_REAL_METHODS),
//                  (mock, context) -> doReturn(sseMcpTransport).when(mock).build())) {
//            final var sseConfig = createSseHttpMcpClientTransportConfiguration();
//            when(headersSupplierFactory.createHttpHeadersSupplier(sseConfig))
//                .thenReturn(() -> EXPECTED_HEADERS);
//            final var client =
//                factory.createClient(
//                    CLIENT_ID,
//                    createMcpClientConfiguration(McpClientType.SSE, null, null, sseConfig));
//
//            assertThat(client).isEqualTo(mcpClient);
//
//            final var transportBuilder = mockedTransportBuilder.constructed().getFirst();
//            verify(transportBuilder).sseUrl(sseConfig.url());
//            verify(transportBuilder).timeout(sseConfig.timeout());
//            verify(transportBuilder).logRequests(false);
//            verify(transportBuilder).logResponses(false);
//
//            verify(transportBuilder).customHeaders(headersSupplierCaptor.capture());
//            assertThat(headersSupplierCaptor.getValue().get()).isEqualTo(EXPECTED_HEADERS);
//
//            verifyMcpClientBuilder(
//                mockedMcpClientConstruction.constructed().getFirst(), sseMcpTransport);
//          }
//        });
//  }
//
//  @Test
//  void configuresClientSpecificSseHttpMcpClientLogging() {
//    loggingResolver.setLogHttpRequests(((clientId, config) -> clientId.equals(CLIENT_ID)));
//    loggingResolver.setLogHttpResponses(((clientId, config) -> !clientId.equals(CLIENT_ID)));
//
//    withMockedMcpClientBuilder(
//        mockedMcpClientConstruction -> {
//          try (MockedConstruction<HttpMcpTransport.Builder> mockedTransportBuilder =
//              mockConstruction(
//                  HttpMcpTransport.Builder.class,
//                  withSettings().defaultAnswer(CALLS_REAL_METHODS),
//                  (mock, context) -> doReturn(sseMcpTransport).when(mock).build())) {
//            final var sseConfig = createSseHttpMcpClientTransportConfiguration();
//            when(headersSupplierFactory.createHttpHeadersSupplier(sseConfig))
//                .thenReturn(() -> EXPECTED_HEADERS);
//
//            factory.createClient(
//                CLIENT_ID, createMcpClientConfiguration(McpClientType.SSE, null, null, sseConfig));
//            factory.createClient(
//                CLIENT_ID + "-2",
//                createMcpClientConfiguration(McpClientType.SSE, null, null, sseConfig));
//
//            final var transportBuilder1 = mockedTransportBuilder.constructed().get(0);
//            verify(transportBuilder1).logRequests(true);
//            verify(transportBuilder1).logResponses(false);
//
//            final var transportBuilder2 = mockedTransportBuilder.constructed().get(1);
//            verify(transportBuilder2).logRequests(false);
//            verify(transportBuilder2).logResponses(true);
//          }
//        });
//  }
//
//  @Test
//  void doesNotApplyTimeoutsAndReconnectIntervalIfNull() {
//    withMockedMcpClientBuilder(
//        mockedMcpClientConstruction -> {
//          try (MockedConstruction<StdioMcpTransport.Builder> mockedTransportBuilder =
//              mockConstruction(
//                  StdioMcpTransport.Builder.class,
//                  withSettings().defaultAnswer(CALLS_REAL_METHODS),
//                  (mock, context) -> doReturn(stdioMcpTransport).when(mock).build())) {
//            final var stdioConfig = createStdioMcpClientTransportConfiguration(List.of());
//            final var client =
//                factory.createClient(
//                    CLIENT_ID,
//                    new McpClientConfiguration(
//                        true, McpClientType.STDIO, stdioConfig, null, null, null, null, null));
//
//            assertThat(client).isEqualTo(mcpClient);
//
//            final var mcpClientBuilder = mockedMcpClientConstruction.constructed().getFirst();
//            verify(mcpClientBuilder).key(CLIENT_ID);
//            verify(mcpClientBuilder).transport(any(McpTransport.class));
//            verify(mcpClientBuilder, never()).initializationTimeout(any());
//            verify(mcpClientBuilder, never()).toolExecutionTimeout(any());
//            verify(mcpClientBuilder, never()).reconnectInterval(any());
//          }
//        });
//  }
//
//  private void withMockedMcpClientBuilder(
//      ThrowingConsumer<MockedConstruction<DefaultMcpClient.Builder>> testLogic) {
//    try (MockedConstruction<DefaultMcpClient.Builder> mockedBuilder =
//        mockConstruction(
//            DefaultMcpClient.Builder.class,
//            (mock, context) -> {
//              when(mock.key(any())).thenReturn(mock);
//              when(mock.transport(any())).thenReturn(mock);
//              when(mock.build()).thenReturn(mcpClient);
//            })) {
//      testLogic.accept(mockedBuilder);
//    }
//  }
//
//  private void verifyMcpClientBuilder(
//      DefaultMcpClient.Builder mcpClientBuilder, McpTransport expectedTransport) {
//    verify(mcpClientBuilder).key(CLIENT_ID);
//    verify(mcpClientBuilder).transport(expectedTransport);
//    verify(mcpClientBuilder).initializationTimeout(Duration.ofSeconds(1));
//    verify(mcpClientBuilder).toolExecutionTimeout(Duration.ofSeconds(2));
//    verify(mcpClientBuilder).reconnectInterval(Duration.ofSeconds(3));
//  }
//
//  private static McpClientConfiguration createMcpClientConfiguration(
//      McpClientType type,
//      StdioMcpClientTransportConfiguration stdioConfig,
//      StreamableHttpMcpClientTransportConfiguration httpConfig,
//      SseHttpMcpClientTransportConfiguration sseConfig) {
//    return new McpClientConfiguration(
//        true,
//        type,
//        stdioConfig,
//        httpConfig,
//        sseConfig,
//        Duration.ofSeconds(1),
//        Duration.ofSeconds(2),
//        Duration.ofSeconds(3));
//  }
//
//  private static StdioMcpClientTransportConfiguration createStdioMcpClientTransportConfiguration(
//      List<String> args) {
//    return new StdioMcpClientTransportConfiguration("command", args, Map.of("ENV_VAR", "value"));
//  }
//
//  private static StreamableHttpMcpClientTransportConfiguration
//      createStreamableHttpMcpClientTransportConfiguration() {
//    return new StreamableHttpMcpClientTransportConfiguration(
//        "http://localhost:123456/mcp",
//        Map.of("X-Dummy", "Test"),
//        BEARER_AUTHENTICATION,
//        Duration.ofSeconds(15));
//  }
//
//  private static SseHttpMcpClientTransportConfiguration
//      createSseHttpMcpClientTransportConfiguration() {
//    return new SseHttpMcpClientTransportConfiguration(
//        "http://localhost:123456/sse",
//        Map.of("X-Dummy", "Test"),
//        BEARER_AUTHENTICATION,
//        Duration.ofSeconds(15));
//  }
//}
