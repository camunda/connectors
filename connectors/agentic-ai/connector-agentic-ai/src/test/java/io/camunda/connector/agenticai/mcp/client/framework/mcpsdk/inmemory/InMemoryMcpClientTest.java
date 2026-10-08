/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.inmemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.InMemoryMcpClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientConfiguration.McpClientType;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.framework.bootstrap.McpClientHeadersSupplierFactory;
import io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.McpSdkClientFactory;
import io.camunda.connector.agenticai.mcp.client.model.content.McpTextContent;
import io.camunda.connector.http.client.client.jdk.proxy.JdkHttpClientProxyConfigurator;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class InMemoryMcpClientTest {

  private final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
  private McpSdkClientFactory factory;
  private AutoCloseable client;

  @BeforeEach
  void setUp() {
    beanFactory.registerSingleton("echoServer", new EchoServer());
    factory =
        new McpSdkClientFactory(
            new ObjectMapper(),
            mock(JdkHttpClientProxyConfigurator.class),
            mock(McpClientHeadersSupplierFactory.class),
            new InMemoryMcpServerResolver(beanFactory));
  }

  @AfterEach
  void tearDown() throws Exception {
    if (client != null) {
      client.close();
    }
  }

  @Test
  void listsAndCallsToolsOfInMemoryServer() {
    final var delegate = factory.createClient("echo", inMemoryConfig("echoServer"));
    client = delegate;

    final var tools = delegate.listTools(AllowDenyList.allowingEverything(), Map.of());
    assertThat(tools.toolDefinitions()).extracting("name").containsExactly("echo");

    final var result =
        delegate.callTool(
            Map.of("name", "echo", "arguments", Map.of("text", "hello")),
            AllowDenyList.allowingEverything(),
            Map.of());
    assertThat(result.isError()).isFalse();
    assertThat(result.content())
        .singleElement()
        .isInstanceOfSatisfying(
            McpTextContent.class, text -> assertThat(text.text()).isEqualTo("echo: hello"));
  }

  @Test
  void enforcesToolExecutionTimeoutForBlockedHandler() {
    final var release = new CountDownLatch(1);
    beanFactory.registerSingleton("blockingServer", new BlockingServer(release));
    final var config =
        new McpClientConfiguration(
            true,
            McpClientType.IN_MEMORY,
            null,
            null,
            null,
            new InMemoryMcpClientTransportConfiguration("blockingServer", null),
            Duration.ofSeconds(5),
            Duration.ofMillis(300),
            null);
    final var delegate = factory.createClient("blocking", config);
    client = delegate;

    try {
      final var start = System.nanoTime();
      final var result =
          delegate.callTool(
              Map.of("name", "block", "arguments", Map.of()),
              AllowDenyList.allowingEverything(),
              Map.of());

      assertThat(result.isError()).isTrue();
      assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
      assertThat(release.getCount()).isEqualTo(1);
    } finally {
      release.countDown();
    }
  }

  @Test
  void validatesServerReferenceEagerly() {
    assertThatThrownBy(() -> factory.validate("echo", inMemoryConfig("missing")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("server-bean-name");
  }

  @Test
  void failsWithoutResolver() {
    final var withoutResolver =
        new McpSdkClientFactory(
            new ObjectMapper(),
            mock(JdkHttpClientProxyConfigurator.class),
            mock(McpClientHeadersSupplierFactory.class));

    assertThatThrownBy(() -> withoutResolver.createClient("echo", inMemoryConfig("echoServer")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("IN_MEMORY");
  }

  private static McpClientConfiguration inMemoryConfig(String beanName) {
    return new McpClientConfiguration(
        true,
        McpClientType.IN_MEMORY,
        null,
        null,
        null,
        new InMemoryMcpClientTransportConfiguration(beanName, null),
        Duration.ofSeconds(5),
        Duration.ofSeconds(5),
        null);
  }

  static class EchoServer implements InMemoryMcpServer {
    @Override
    public List<SyncToolSpecification> tools() {
      final var tool =
          McpSchema.Tool.builder(
                  "echo",
                  McpJsonDefaults.getMapper(),
                  "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}}}")
              .description("Echoes the text")
              .build();
      return List.of(
          SyncToolSpecification.builder()
              .tool(tool)
              .callHandler(
                  (exchange, request) ->
                      McpSchema.CallToolResult.builder()
                          .addTextContent("echo: " + request.arguments().get("text"))
                          .build())
              .build());
    }
  }

  static class BlockingServer implements InMemoryMcpServer {
    private final CountDownLatch release;

    BlockingServer(CountDownLatch release) {
      this.release = release;
    }

    @Override
    public List<SyncToolSpecification> tools() {
      final var tool =
          McpSchema.Tool.builder("block", McpJsonDefaults.getMapper(), "{\"type\":\"object\"}")
              .description("Blocks until released")
              .build();
      return List.of(
          SyncToolSpecification.builder()
              .tool(tool)
              .callHandler(
                  (exchange, request) -> {
                    try {
                      release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                    return McpSchema.CallToolResult.builder().addTextContent("released").build();
                  })
              .build());
    }
  }
}
