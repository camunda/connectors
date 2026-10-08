/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.mcpsdk.inmemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.InMemoryMcpClientTransportConfiguration;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class InMemoryMcpServerResolverTest {

  private static final String PREFIX = "camunda.connector.agenticai.mcp.client.clients.my-client";

  private final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
  private final InMemoryMcpServerResolver resolver = new InMemoryMcpServerResolver(beanFactory);

  @Test
  void resolvesByBeanName() {
    final var server = new TestServer();
    beanFactory.registerSingleton("myServer", server);

    assertThat(
            resolver.resolve(
                "my-client", new InMemoryMcpClientTransportConfiguration("myServer", null)))
        .isSameAs(server);
  }

  @Test
  void failsForMissingBeanName() {
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    "my-client", new InMemoryMcpClientTransportConfiguration("missing", null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(PREFIX + ".in-memory.server-bean-name")
        .hasMessageContaining("'missing'");
  }

  @Test
  void failsForBeanOfWrongType() {
    beanFactory.registerSingleton("notAServer", "a string");

    assertThatThrownBy(
            () ->
                resolver.resolve(
                    "my-client", new InMemoryMcpClientTransportConfiguration("notAServer", null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(PREFIX + ".in-memory.server-bean-name");
  }

  @Test
  void resolvesByClassName() {
    final var server = new TestServer();
    beanFactory.registerSingleton("anyName", server);

    assertThat(
            resolver.resolve(
                "my-client",
                new InMemoryMcpClientTransportConfiguration(null, TestServer.class.getName())))
        .isSameAs(server);
  }

  @Test
  void failsForUnknownClass() {
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    "my-client",
                    new InMemoryMcpClientTransportConfiguration(null, "com.example.Unknown")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(PREFIX + ".in-memory.server-class-name")
        .hasMessageContaining("cannot be loaded");
  }

  @Test
  void failsForClassNotImplementingSpi() {
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    "my-client",
                    new InMemoryMcpClientTransportConfiguration(null, String.class.getName())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(PREFIX + ".in-memory.server-class-name")
        .hasMessageContaining("does not implement");
  }

  @Test
  void failsForClassWithoutBean() {
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    "my-client",
                    new InMemoryMcpClientTransportConfiguration(null, TestServer.class.getName())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(PREFIX + ".in-memory.server-class-name")
        .hasMessageContaining("found 0");
  }

  @Test
  void failsForClassWithSeveralBeansAndListsCandidates() {
    beanFactory.registerSingleton("first", new TestServer());
    beanFactory.registerSingleton("second", new TestServer());

    assertThatThrownBy(
            () ->
                resolver.resolve(
                    "my-client",
                    new InMemoryMcpClientTransportConfiguration(null, TestServer.class.getName())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("found 2")
        .hasMessageContaining("first")
        .hasMessageContaining("second");
  }

  static class TestServer implements InMemoryMcpServer {
    @Override
    public List<SyncToolSpecification> tools() {
      return List.of();
    }
  }
}
