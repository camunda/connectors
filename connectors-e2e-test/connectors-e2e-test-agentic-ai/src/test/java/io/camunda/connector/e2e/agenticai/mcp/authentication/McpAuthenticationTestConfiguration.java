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
import io.camunda.connector.test.utils.DockerImages;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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
@EnableConfigurationProperties(McpAuthenticationTestProperties.class)
class McpAuthenticationTestConfiguration {

  @Bean
  public Network mcpTestServerNetwork() {
    return Network.newNetwork();
  }

  @Bean
  @ConditionalOnProperty(name = "mcp.test.auth", havingValue = "OAUTH2")
  public KeycloakContainer keycloakContainer(
      @Qualifier("mcpTestServerNetwork") Network mcpTestServerNetwork) {
    final var keycloak =
        new KeycloakContainer(DockerImages.get("keycloak"))
            .withNetwork(mcpTestServerNetwork)
            .withNetworkAliases("keycloak")
            .withRealmImportFile("/keycloak/testme-realm.json");

    keycloak.start();

    return keycloak;
  }

  @Bean
  public GenericContainer<?> mcpTestServer(
      McpAuthenticationTestProperties testProperties,
      @Qualifier("mcpTestServerNetwork") Network mcpTestServerNetwork,
      Optional<KeycloakContainer> keycloak) {
    final var profiles = new ArrayList<String>();
    testProperties.transport().getTestServerProfile().ifPresent(profiles::add);
    testProperties.auth().getTestServerProfile().ifPresent(profiles::add);

    GenericContainer<?> mcpTestServer =
        new GenericContainer<>(DockerImages.get("mcp-test-server"))
            .withNetwork(mcpTestServerNetwork)
            .withExposedPorts(12001)
            .withEnv("SPRING_PROFILES_ACTIVE", String.join(",", profiles))
            .withEnv(
                "SPRING_AI_MCP_SERVER_NAME",
                "MCP Test Server(%s)".formatted(String.join(",", profiles)));

    if (testProperties.auth() == McpTestServerAuthentication.OAUTH2) {
      mcpTestServer
          .dependsOn(keycloak.get())
          .withEnv("MCP_SERVER_AUTH_OAUTH2_ISSUERURL", "http://keycloak:8080/realms/testme");
    }

    mcpTestServer.start();

    return mcpTestServer;
  }

  /**
   * Configures config properties (connection, authentication) for MCP clients defined as part of
   * the Spring configuration properties.
   */
  @Bean
  public DynamicPropertyRegistrar mcpStandaloneClientPropertiesRegistrar(
      McpAuthenticationTestProperties testProperties,
      @Qualifier("mcpTestServer") GenericContainer<?> mcpServer,
      Optional<KeycloakContainer> keycloak) {
    return (DynamicPropertyRegistry registry) -> {
      Map<String, String> properties = new LinkedHashMap<>();

      // a-mcp-client configures connection + authentication
      testProperties
          .transport()
          .applySpringConfigProperties(properties, "a-mcp-client", mcpServerBaseUrl(mcpServer));
      testProperties
          .auth()
          .applySpringConfigProperties(
              properties,
              testProperties.transport().springConfigPrefix("a-mcp-client"),
              keycloak.orElse(null));

      // an-unauthenticated-mcp-client configures only connection (no authentication)
      testProperties
          .transport()
          .applySpringConfigProperties(
              properties, "an-unauthenticated-mcp-client", mcpServerBaseUrl(mcpServer));

      properties.forEach((k, v) -> registry.add(k, () -> v));
    };
  }

  /**
   * Provides Remote MCP client connector input mappings for connection and authentication to the
   * mcp-test-server.
   */
  @Bean
  public McpRemoteClientInputMappingsProvider mcpRemoteClientInputMappingsProvider(
      McpAuthenticationTestProperties testProperties,
      @Qualifier("mcpTestServer") GenericContainer<?> mcpServer,
      Optional<KeycloakContainer> keycloak) {
    return (additionalInputMappings, includeAuth) -> {
      Map<String, String> inputMappings = new LinkedHashMap<>();
      inputMappings.putAll(additionalInputMappings);

      testProperties
          .transport()
          .applyRemoteConnectorInputMappings(inputMappings, mcpServerBaseUrl(mcpServer));

      if (includeAuth) {
        testProperties
            .auth()
            .applyRemoteConnectorInputMappings(
                inputMappings,
                testProperties.transport().remoteConnectorInputMappingPrefix(),
                keycloak.orElse(null));
      }

      return inputMappings;
    };
  }

  private static String mcpServerBaseUrl(GenericContainer<?> mcpServer) {
    return "http://%s:%s".formatted(mcpServer.getHost(), mcpServer.getMappedPort(12001));
  }

  interface McpRemoteClientInputMappingsProvider {
    default Map<String, String> mcpRemoteClientInputMappings(
        Map<String, String> additionalInputMappings) {
      return mcpRemoteClientInputMappings(additionalInputMappings, true);
    }

    Map<String, String> mcpRemoteClientInputMappings(
        Map<String, String> additionalInputMappings, boolean includeAuth);
  }
}
