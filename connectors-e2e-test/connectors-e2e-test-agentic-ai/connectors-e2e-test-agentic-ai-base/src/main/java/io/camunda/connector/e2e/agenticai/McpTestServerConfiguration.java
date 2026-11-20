/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai;

import static io.camunda.connector.e2e.agenticai.McpTestServerTransport.HTTP;
import static io.camunda.connector.e2e.agenticai.McpTestServerTransport.SSE;
import static io.camunda.connector.e2e.agenticai.McpTestServerTransport.STDIO;

import io.camunda.connector.test.utils.DockerImages;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;

/**
 * Starts mcp-test-server with the HTTP and the SSE profile, and configures one runtime MCP client
 * per {@link McpTestServerTransport}. The client ids come from {@link McpTestServers#clientId}.
 *
 * <p>The stdio client starts the server with the docker CLI when it handles its first job. The
 * configuration fails when the docker CLI does not work, and pulls the image for it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class McpTestServerConfiguration {

  private static final String IMAGE = DockerImages.get("mcp-test-server");
  private static final int PORT = 12001;

  @Bean
  public GenericContainer<?> mcpTestServerHttp() {
    return startServer(HTTP);
  }

  @Bean
  public GenericContainer<?> mcpTestServerSse() {
    return startServer(SSE);
  }

  @Bean
  public McpTestServers mcpTestServers(
      @Qualifier("mcpTestServerHttp") GenericContainer<?> http,
      @Qualifier("mcpTestServerSse") GenericContainer<?> sse) {
    return new McpTestServers(baseUrl(http), baseUrl(sse));
  }

  @Bean
  public DynamicPropertyRegistrar mcpTestServerClientsRegistrar(McpTestServers servers) {
    pullImageWithDockerCli();
    return registry ->
        servers.clientProperties().forEach((key, value) -> registry.add(key, () -> value));
  }

  private static GenericContainer<?> startServer(McpTestServerTransport transport) {
    final var container = new GenericContainer<>(IMAGE).withExposedPorts(PORT);
    transport
        .getTestServerProfile()
        .ifPresent(profile -> container.withEnv("SPRING_PROFILES_ACTIVE", profile));
    container.start();
    return container;
  }

  private static String baseUrl(GenericContainer<?> container) {
    return "http://%s:%s".formatted(container.getHost(), container.getMappedPort(PORT));
  }

  /**
   * The stdio client runs the image with {@code --pull=never}, so the image MUST exist for the
   * docker CLI before the first job.
   */
  private static void pullImageWithDockerCli() {
    final var version = docker("version");
    if (version.exitCode() != 0) {
      throw new IllegalStateException(
          "The stdio MCP client starts mcp-test-server with the docker CLI. Install the docker CLI"
              + " on PATH and start Docker. 'docker version' failed:\n"
              + version.output());
    }

    if (docker("image", "inspect", IMAGE).exitCode() != 0) {
      final var pull = docker("pull", IMAGE);
      if (pull.exitCode() != 0) {
        throw new IllegalStateException(
            "Failed to pull %s with the docker CLI:%n%s".formatted(IMAGE, pull.output()));
      }
    }
  }

  private static DockerResult docker(String... args) {
    final var command = new ArrayList<String>();
    command.add("docker");
    command.addAll(List.of(args));
    try {
      final var process = new ProcessBuilder(command).redirectErrorStream(true).start();
      final var output =
          new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      return new DockerResult(process.waitFor(), output);
    } catch (IOException e) {
      return new DockerResult(-1, e.toString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private record DockerResult(int exitCode, String output) {}

  /** Connection details of the running servers for the runtime clients and the remote client. */
  public record McpTestServers(String httpBaseUrl, String sseBaseUrl) {

    /** The id of the runtime MCP client that reaches the server over the given transport. */
    public static String clientId(McpTestServerTransport transport) {
      return "a-mcp-client-" + transport.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Input mappings of a remote MCP client task for the given transport. The remote client
     * connector has no stdio transport, so {@code STDIO} uses the HTTP server.
     */
    public Map<String, String> remoteClientInputMappings(McpTestServerTransport transport) {
      final var inputMappings = new LinkedHashMap<String, String>();
      switch (transport) {
        case HTTP, STDIO -> HTTP.applyRemoteConnectorInputMappings(inputMappings, httpBaseUrl);
        case SSE -> SSE.applyRemoteConnectorInputMappings(inputMappings, sseBaseUrl);
      }
      return inputMappings;
    }

    Map<String, String> clientProperties() {
      final var properties = new LinkedHashMap<String, String>();
      HTTP.applySpringConfigProperties(properties, clientId(HTTP), httpBaseUrl);
      SSE.applySpringConfigProperties(properties, clientId(SSE), sseBaseUrl);
      STDIO.applySpringConfigProperties(properties, clientId(STDIO), null);
      return properties;
    }
  }
}
