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

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.util.Map;
import java.util.Optional;

public enum McpTestServerAuthentication {
  BASIC("auth-basic") {
    @Override
    public void applySpringConfigProperties(
        Map<String, String> properties, String configPrefix, KeycloakContainer keycloak) {
      properties.put(configPrefix + ".authentication.type", "basic");
      properties.put(configPrefix + ".authentication.basic.username", TEST_USERNAME);
      properties.put(configPrefix + ".authentication.basic.password", TEST_PASSWORD);
    }

    @Override
    public void applyRemoteConnectorInputMappings(
        Map<String, String> inputMappings, String configPrefix, KeycloakContainer keycloak) {
      inputMappings.put(configPrefix + ".authentication.type", "basic");
      inputMappings.put(configPrefix + ".authentication.username", TEST_USERNAME);
      inputMappings.put(configPrefix + ".authentication.password", TEST_PASSWORD);
    }
  },

  API_KEY("auth-api-key") {
    @Override
    public void applySpringConfigProperties(
        Map<String, String> properties, String configPrefix, KeycloakContainer keycloak) {
      properties.put(configPrefix + ".headers.X-Api-Key", TEST_API_KEY);
    }

    @Override
    public void applyRemoteConnectorInputMappings(
        Map<String, String> inputMappings, String configPrefix, KeycloakContainer keycloak) {
      inputMappings.put(
          configPrefix + ".headers", "={ \"X-Api-Key\": \"%s\" }".formatted(TEST_API_KEY));
    }
  },

  OAUTH2("auth-oauth2") {
    @Override
    public void applySpringConfigProperties(
        Map<String, String> properties, String configPrefix, KeycloakContainer keycloak) {
      assertKeycloakPresent(keycloak);
      properties.put(configPrefix + ".authentication.type", "oauth");
      properties.put(
          configPrefix + ".authentication.oauth.oauth-token-endpoint",
          oauthTokenEndpoint(keycloak));
      properties.put(configPrefix + ".authentication.oauth.client-id", TEST_CLIENT_ID);
      properties.put(configPrefix + ".authentication.oauth.client-secret", TEST_CLIENT_SECRET);
    }

    @Override
    public void applyRemoteConnectorInputMappings(
        Map<String, String> inputMappings, String configPrefix, KeycloakContainer keycloak) {
      assertKeycloakPresent(keycloak);
      inputMappings.put(configPrefix + ".authentication.type", "oauth-client-credentials-flow");
      inputMappings.put(
          configPrefix + ".authentication.oauthTokenEndpoint", oauthTokenEndpoint(keycloak));
      inputMappings.put(configPrefix + ".authentication.clientId", TEST_CLIENT_ID);
      inputMappings.put(configPrefix + ".authentication.clientSecret", TEST_CLIENT_SECRET);
    }

    private static void assertKeycloakPresent(KeycloakContainer keycloak) {
      assertThat(keycloak)
          .describedAs("Keycloak container is required with oauth2 authentication")
          .isNotNull();
    }

    private static String oauthTokenEndpoint(KeycloakContainer keycloak) {
      return keycloak.getAuthServerUrl()
          + "/realms/"
          + TEST_REALM
          + "/protocol/openid-connect/token";
    }
  };

  private static final String TEST_USERNAME = "test-user";
  private static final String TEST_PASSWORD = "test-password";
  private static final String TEST_API_KEY = "test.test-key";
  private static final String TEST_REALM = "testme";
  private static final String TEST_CLIENT_ID = "testme-client-authorized";
  private static final String TEST_CLIENT_SECRET = "testme-secret";

  private final String testServerProfile;

  McpTestServerAuthentication(String testServerProfile) {
    this.testServerProfile = testServerProfile;
  }

  public Optional<String> getTestServerProfile() {
    return Optional.ofNullable(testServerProfile);
  }

  public abstract void applySpringConfigProperties(
      Map<String, String> properties, String configPrefix, KeycloakContainer keycloak);

  public abstract void applyRemoteConnectorInputMappings(
      Map<String, String> inputMappings, String configPrefix, KeycloakContainer keycloak);
}
