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
package io.camunda.connector.e2e.agenticai.mcp.authentication;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(McpTestMatrixProperties.class)
class McpTestMatrixTestConfiguration {

  private static final String MCP_TEST_SERVER_VERSION = "1.1.0";

  @Bean
  Network mcpNetwork() {
    return Network.newNetwork();
  }

  @Bean
  GenericContainer<?> mcpTestServer(
      McpTestMatrixProperties matrix,
      Network mcpNetwork,
      ObjectProvider<KeycloakContainer> keycloak) {

    final var profiles = new ArrayList<String>();
    matrix.transport().getProfile().ifPresent(profiles::add);
    matrix.auth().getProfile().ifPresent(profiles::add);

    GenericContainer<?> container =
        new GenericContainer<>(
                "registry.camunda.cloud/mcp/mcp-test-server:" + MCP_TEST_SERVER_VERSION)
            .withNetwork(mcpNetwork)
            .withExposedPorts(12001)
            .withEnv("SPRING_PROFILES_ACTIVE", String.join(",", profiles))
            .withEnv(
                "SPRING_AI_MCP_SERVER_NAME",
                "MCP Test Server(%s)".formatted(String.join(",", profiles)));

    if (matrix.auth() == McpTestServerAuthentication.OAUTH2) {
      // must match keycloak network alias
      container.withEnv("MCP_SERVER_AUTH_OAUTH2_ISSUERURL", "http://keycloak:8080/realms/testme");
      // force bean creation so keycloak is part of the context for oauth cases
      keycloak.getObject();
    }

    return container;
  }

  @Bean
  @ConditionalOnProperty(name = "mcp.test.auth", havingValue = "OAUTH2")
  KeycloakContainer keycloakContainer(Network mcpNetwork) {
    return new KeycloakContainer("quay.io/keycloak/keycloak:26.5")
        .withNetwork(mcpNetwork)
        .withNetworkAliases("keycloak")
        .withRealmImportFile("/keycloak/testme-realm.json");
  }

  @Bean
  DynamicPropertyRegistrar mcpStandaloneClientPropertiesRegistrar(
      McpTestMatrixProperties matrix,
      @Qualifier("mcpTestServer") ObjectProvider<GenericContainer<?>> mcpServer,
      ObjectProvider<KeycloakContainer> keycloak) {

    return (DynamicPropertyRegistry registry) -> {
      KeycloakContainer kc =
          matrix.auth() == McpTestServerAuthentication.OAUTH2
              ? ensureStarted(keycloak.getObject())
              : null;
      GenericContainer<?> server = ensureStarted(mcpServer.getObject());

      final var baseUrl = mcpServerBaseUrl(server);

      Map<String, String> props = new LinkedHashMap<>();
      final var standalonePrefix = matrix.transport().standaloneConfigPrefix("a-mcp-client");

      matrix.transport().applyStandalone(props, "a-mcp-client", baseUrl);
      matrix.auth().applyStandalone(props, standalonePrefix, kc);

      props.forEach((k, v) -> registry.add(k, () -> v));
    };
  }

  @Bean
  RemoteMcpClientPropertiesProvider remoteMcpClientPropertiesProvider(
      McpTestMatrixProperties matrix,
      @Qualifier("mcpTestServer") ObjectProvider<GenericContainer<?>> mcpServer,
      ObjectProvider<KeycloakContainer> keycloak) {

    return additionalProperties -> {
      KeycloakContainer kc =
          matrix.auth() == McpTestServerAuthentication.OAUTH2
              ? ensureStarted(keycloak.getObject())
              : null;
      GenericContainer<?> server = ensureStarted(mcpServer.getObject());

      final var baseUrl = mcpServerBaseUrl(server);

      Map<String, String> properties = new LinkedHashMap<>();
      properties.putAll(additionalProperties);

      final var remotePrefix = matrix.transport().remoteConfigPrefix();
      matrix.transport().applyRemote(properties, baseUrl);
      matrix.auth().applyRemote(properties, remotePrefix, kc);

      return properties;
    };
  }

  private static GenericContainer<?> ensureStarted(GenericContainer<?> container) {
    if (!container.isRunning()) {
      container.start();
    }
    return container;
  }

  private static KeycloakContainer ensureStarted(KeycloakContainer container) {
    if (!container.isRunning()) {
      container.start();
    }
    return container;
  }

  private static String mcpServerBaseUrl(GenericContainer<?> mcpServer) {
    return "http://%s:%s".formatted(mcpServer.getHost(), mcpServer.getMappedPort(12001));
  }
}
