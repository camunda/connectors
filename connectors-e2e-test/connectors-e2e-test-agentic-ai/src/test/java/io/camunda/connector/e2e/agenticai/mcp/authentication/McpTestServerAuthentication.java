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
import java.util.Map;
import java.util.Optional;

public enum McpTestServerAuthentication {
  NONE(null) {
    @Override
    public void applyStandalone(
        Map<String, String> properties, String configPrefix, KeycloakContainer keycloak) {}

    @Override
    public void applyRemote(
        Map<String, String> properties, String configPrefix, KeycloakContainer keycloak) {}
  },

  API_KEY("auth-api-key") {
    @Override
    public void applyStandalone(
        Map<String, String> properties, String configPrefix, KeycloakContainer keycloak) {
      properties.put(configPrefix + ".headers.X-Api-Key", TEST_API_KEY);
    }

    @Override
    public void applyRemote(
        Map<String, String> properties, String configPrefix, KeycloakContainer keycloak) {
      properties.put(
          configPrefix + ".headers", "={ \"X-Api-Key\": \"%s\" }".formatted(TEST_API_KEY));
    }
  },

  BASIC("auth-basic") {
    @Override
    public void applyStandalone(
        Map<String, String> properties, String configPrefix, KeycloakContainer keycloak) {
      properties.put(configPrefix + ".authentication.type", "basic");
      properties.put(configPrefix + ".authentication.basic.username", TEST_USERNAME);
      properties.put(configPrefix + ".authentication.basic.password", TEST_PASSWORD);
    }

    @Override
    public void applyRemote(
        Map<String, String> properties, String configPrefix, KeycloakContainer keycloak) {
      properties.put(configPrefix + ".authentication.type", "basic");
      properties.put(configPrefix + ".authentication.username", TEST_USERNAME);
      properties.put(configPrefix + ".authentication.password", TEST_PASSWORD);
    }
  },

  OAUTH2("auth-oauth2") {
    @Override
    public void applyStandalone(
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
    public void applyRemote(
        Map<String, String> properties, String configPrefix, KeycloakContainer keycloak) {
      assertKeycloakPresent(keycloak);
      properties.put(configPrefix + ".authentication.type", "oauth-client-credentials-flow");
      properties.put(
          configPrefix + ".authentication.oauthTokenEndpoint", oauthTokenEndpoint(keycloak));
      properties.put(configPrefix + ".authentication.clientId", TEST_CLIENT_ID);
      properties.put(configPrefix + ".authentication.clientSecret", TEST_CLIENT_SECRET);
    }
  };

  private static final String TEST_USERNAME = "test-user";
  private static final String TEST_PASSWORD = "test-password";
  private static final String TEST_API_KEY = "test.test-key";
  private static final String TEST_CLIENT_ID = "testme-client-authorized";
  private static final String TEST_CLIENT_SECRET = "testme-secret";

  private final String profile;

  McpTestServerAuthentication(String profile) {
    this.profile = profile;
  }

  public Optional<String> getProfile() {
    return Optional.ofNullable(profile);
  }

  public abstract void applyStandalone(
      Map<String, String> properties, String configPrefix, KeycloakContainer keycloak);

  public abstract void applyRemote(
      Map<String, String> properties, String configPrefix, KeycloakContainer keycloak);

  private static void assertKeycloakPresent(KeycloakContainer keycloak) {
    if (keycloak == null) {
      throw new IllegalStateException("Keycloak container is required with oauth2 authentication");
    }
  }

  private static String oauthTokenEndpoint(KeycloakContainer keycloak) {
    return keycloak.getAuthServerUrl() + "/realms/testme/protocol/openid-connect/token";
  }
}
