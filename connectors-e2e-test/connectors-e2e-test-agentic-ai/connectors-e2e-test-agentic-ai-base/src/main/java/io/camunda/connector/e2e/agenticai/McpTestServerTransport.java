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

import io.camunda.connector.test.utils.DockerImages;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public enum McpTestServerTransport {
  HTTP(null, "http", "/mcp"),
  SSE("transport-sse", "sse", "/sse"),

  /**
   * The client starts its own server container with the docker CLI, so {@link
   * #applySpringConfigProperties} ignores the server base URL. The remote client connector has no
   * stdio transport.
   */
  STDIO("transport-stdio", "stdio", null) {
    @Override
    public void applySpringConfigProperties(
        Map<String, String> properties, String clientName, String mcpServerBaseUrl) {
      final var clientPrefix = springConfigClientPrefix(clientName);
      properties.put(clientPrefix + ".type", "stdio");
      properties.put(clientPrefix + ".initialization-timeout", STDIO_TIMEOUT);
      properties.put(clientPrefix + ".tool-execution-timeout", STDIO_TIMEOUT);
      properties.put(clientPrefix + ".stdio.command", "docker");

      final var args =
          List.of(
              "run",
              "-i",
              "--rm",
              "--pull=never",
              "--no-healthcheck",
              "--label",
              STDIO_CONTAINER_LABEL,
              "-e",
              "SPRING_PROFILES_ACTIVE",
              IMAGE);
      for (int i = 0; i < args.size(); i++) {
        properties.put("%s.stdio.args[%d]".formatted(clientPrefix, i), args.get(i));
      }

      // The profile MUST reach the container through the client env, so that broken env
      // forwarding fails the stdio tests.
      properties.put(
          clientPrefix + ".stdio.env[SPRING_PROFILES_ACTIVE]",
          getTestServerProfile().orElseThrow());
    }
  };

  /** The image of every mcp-test-server container, including the ones the stdio client starts. */
  static final String IMAGE = DockerImages.get("mcp-test-server");

  /**
   * Marks the stdio containers of this JVM, so that the shutdown hook in {@link
   * McpTestServerConfiguration} can remove leftovers.
   */
  static final String STDIO_CONTAINER_LABEL = "mcp-test-server-stdio=" + UUID.randomUUID();

  private static final String SPRING_CONFIG_CLIENTS_PREFIX =
      "camunda.connector.agenticai.mcp.client.clients";
  private static final String MCP_CLIENT_TIMEOUT = "PT3S";
  // covers the container start and the start of the server in the container, also on a loaded
  // runner
  private static final String STDIO_TIMEOUT = "PT2M";

  private final String testServerProfile;
  private final String type;
  private final String urlPath;

  McpTestServerTransport(String testServerProfile, String type, String urlPath) {
    this.testServerProfile = testServerProfile;
    this.type = type;
    this.urlPath = urlPath;
  }

  public Optional<String> getTestServerProfile() {
    return Optional.ofNullable(testServerProfile);
  }

  public String springConfigPrefix(String clientName) {
    return springConfigClientPrefix(clientName) + "." + type;
  }

  private static String springConfigClientPrefix(String clientName) {
    return SPRING_CONFIG_CLIENTS_PREFIX + "." + clientName;
  }

  public String remoteConnectorInputMappingPrefix() {
    return "data.transport.%s".formatted(type);
  }

  public void applySpringConfigProperties(
      Map<String, String> properties, String clientName, String mcpServerBaseUrl) {
    properties.put("%s.type".formatted(springConfigClientPrefix(clientName)), type);
    properties.put(
        ("%s.initialization-timeout").formatted(springConfigClientPrefix(clientName)),
        MCP_CLIENT_TIMEOUT);
    properties.put(
        ("%s.tool-execution-timeout").formatted(springConfigClientPrefix(clientName)),
        MCP_CLIENT_TIMEOUT);
    properties.put("%s.url".formatted(springConfigPrefix(clientName)), mcpServerBaseUrl + urlPath);
    properties.put("%s.timeout".formatted(springConfigPrefix(clientName)), MCP_CLIENT_TIMEOUT);
  }

  public void applyRemoteConnectorInputMappings(
      Map<String, String> inputMappings, String mcpServerBaseUrl) {
    inputMappings.put("data.transport.type", type);
    inputMappings.put(
        "%s.url".formatted(remoteConnectorInputMappingPrefix()), mcpServerBaseUrl + urlPath);
    inputMappings.put(
        "%s.timeout".formatted(remoteConnectorInputMappingPrefix()), MCP_CLIENT_TIMEOUT);
  }
}
