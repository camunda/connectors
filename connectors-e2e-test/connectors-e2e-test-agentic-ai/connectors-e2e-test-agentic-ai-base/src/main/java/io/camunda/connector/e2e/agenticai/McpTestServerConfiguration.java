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
import static io.camunda.connector.e2e.agenticai.McpTestServerTransport.IMAGE;
import static io.camunda.connector.e2e.agenticai.McpTestServerTransport.SSE;
import static io.camunda.connector.e2e.agenticai.McpTestServerTransport.STDIO;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;

/**
 * Starts one mcp-test-server for Streamable HTTP (default profile) and one for SSE (profile {@code
 * transport-sse}), and configures one runtime MCP client per {@link McpTestServerTransport}. The
 * client ids come from {@link McpTestServers#clientId}.
 *
 * <p>The stdio client starts the server with the docker CLI when it handles its first job. The
 * configuration fails when the docker CLI does not work, and pulls the image for it. A server that
 * ignores the closed stdin keeps its container running, so a JVM shutdown hook removes the stdio
 * containers of this JVM. The hook runs at JVM exit and not at context close, because all contexts
 * of a JVM share the label. A killed JVM skips the hook, so the configuration also removes the
 * stdio containers of JVMs that no longer run. Containers of parallel runs stay untouched.
 */
@TestConfiguration(proxyBeanMethods = false)
public class McpTestServerConfiguration {

  private static final int PORT = 12001;
  private static final Duration DOCKER_COMMAND_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration DOCKER_PULL_TIMEOUT = Duration.ofMinutes(10);

  static {
    Runtime.getRuntime()
        .addShutdownHook(new Thread(McpTestServerConfiguration::removeStdioContainers));
  }

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
    removeStaleStdioContainers();
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

  private static void removeStdioContainers() {
    removeStdioContainers(label -> label.equals(McpTestServerTransport.STDIO_CONTAINER_LABEL));
  }

  private static void removeStaleStdioContainers() {
    removeStdioContainers(label -> {
      final var pid = label.substring(0, Math.max(label.indexOf('-'), 0));
      return pid.matches("\\d+") && ProcessHandle.of(Long.parseLong(pid)).isEmpty();
    });
  }

  private static void removeStdioContainers(Predicate<String> labelValueMatches) {
    final var list =
        docker(
            "ps",
            "-a",
            "--filter",
            "label=" + McpTestServerTransport.STDIO_CONTAINER_LABEL_KEY,
            "--format",
            "{{.ID}} {{.Label \"" + McpTestServerTransport.STDIO_CONTAINER_LABEL_KEY + "\"}}");
    if (list.exitCode() != 0) {
      return;
    }
    final var args = new ArrayList<>(List.of("rm", "-f"));
    list.output()
        .lines()
        .map(line -> line.trim().split("\\s+", 2))
        .filter(parts -> parts.length == 2 && labelValueMatches.test(parts[1]))
        .forEach(parts -> args.add(parts[0]));
    if (args.size() > 2) {
      docker(args.toArray(String[]::new));
    }
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
      final var pull = docker(DOCKER_PULL_TIMEOUT, "pull", IMAGE);
      if (pull.exitCode() != 0) {
        throw new IllegalStateException(
            "Failed to pull %s with the docker CLI:%n%s".formatted(IMAGE, pull.output()));
      }
    }
  }

  private static DockerResult docker(String... args) {
    return docker(DOCKER_COMMAND_TIMEOUT, args);
  }

  private static DockerResult docker(Duration timeout, String... args) {
    final var command = new ArrayList<String>();
    command.add("docker");
    command.addAll(List.of(args));
    Process process = null;
    try {
      process = new ProcessBuilder(command).redirectErrorStream(true).start();
      final var processOutput = process.getInputStream();
      final var output =
          CompletableFuture.supplyAsync(() -> {
            try {
              return new String(processOutput.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
              return e.toString();
            }
          });
      if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return new DockerResult(-1, "'%s' timed out after %s".formatted(command, timeout));
      }
      return new DockerResult(
          process.exitValue(), output.get(timeout.toSeconds(), TimeUnit.SECONDS));
    } catch (IOException | ExecutionException | TimeoutException e) {
      return new DockerResult(-1, e.toString());
    } catch (InterruptedException e) {
      if (process != null) {
        process.destroyForcibly();
      }
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
     * Input mappings of a remote MCP client task for the given transport.
     *
     * @throws IllegalArgumentException for {@code STDIO}, which the remote client connector does
     *     not support
     */
    public Map<String, String> remoteClientInputMappings(McpTestServerTransport transport) {
      final var baseUrl =
          switch (transport) {
            case HTTP -> httpBaseUrl;
            case SSE -> sseBaseUrl;
            case STDIO ->
                throw new IllegalArgumentException("The remote MCP client has no stdio transport");
          };
      final var inputMappings = new LinkedHashMap<String, String>();
      transport.applyRemoteConnectorInputMappings(inputMappings, baseUrl);
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
