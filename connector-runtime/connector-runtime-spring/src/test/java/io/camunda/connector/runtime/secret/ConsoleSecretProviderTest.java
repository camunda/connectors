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
package io.camunda.connector.runtime.secret;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.connector.runtime.secret.console.Authentication;
import io.camunda.connector.runtime.secret.console.ConsoleSecretApiClient;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

public class ConsoleSecretProviderTest {

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static Authentication auth;

  static Map<String, String> authToken;

  static ConsoleSecretApiClient client;

  @BeforeAll
  static void beforeAll() {
    // Mock authentication
    auth = Mockito.mock(Authentication.class);
    authToken = Map.of("Authorization", "Bearer XXX");
    when(auth.getTokenHeader()).thenReturn(authToken);

    client = new ConsoleSecretApiClient(wm.baseUrl() + "/secrets", auth);
  }

  @Test
  void testSuccessfulSecretsHandling() {
    // Mock successful response
    var secretsResponse = Collections.singletonMap("secretKey", "secretValue");
    wm.stubFor(
        get(urlPathMatching("/secrets"))
            .withHeader("Authorization", matching("Bearer XXX"))
            .willReturn(ResponseDefinitionBuilder.okForJson(secretsResponse)));

    // Test the client
    var secrets = client.getSecrets();
    assertThat(secrets).isEqualTo(secretsResponse);

    // Test the provider
    var consoleSecretProvider = new ConsoleSecretProvider(client, Duration.ofSeconds(1));
    assertThat(consoleSecretProvider.getSecret("secretKey")).isEqualTo("secretValue");
  }

  @Test
  void testFailureOnInitialLoad() {
    // Mock failing response
    wm.stubFor(
        get(urlPathMatching("/secrets"))
            .withHeader("Authorization", matching("Bearer XXX"))
            .willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(500)));

    // Test the client
    assertThrows(RuntimeException.class, client::getSecrets);
  }

  @Test
  void testSuccessfulSecretResolvingInCaseOfFailure() throws InterruptedException {
    // Mock response
    var secretsResponse = Collections.singletonMap("secretKey", "secretValue");
    wm.stubFor(
        get(urlPathMatching("/secrets"))
            .withHeader("Authorization", matching("Bearer XXX"))
            .willReturn(ResponseDefinitionBuilder.okForJson(secretsResponse)));

    var consoleSecretProvider = new ConsoleSecretProvider(client, Duration.ofMillis(1));
    assertThat(consoleSecretProvider.getSecret("secretKey")).isEqualTo("secretValue");

    // Sleep so cache requires a new refresh
    Thread.sleep(10);

    // Mock failing response
    wm.stubFor(
        get(urlPathMatching("/secrets"))
            .withHeader("Authorization", matching("Bearer XXX"))
            .willReturn(ResponseDefinitionBuilder.responseDefinition().withStatus(500)));

    // Previously cached secret should still be resolved
    assertThat(consoleSecretProvider.getSecret("secretKey")).isEqualTo("secretValue");

    // Sleep so cache requires a new refresh
    Thread.sleep(10);

    // Successful response with a new value
    secretsResponse = Collections.singletonMap("secretKey", "newSecretValue");
    wm.stubFor(
        get(urlPathMatching("/secrets"))
            .withHeader("Authorization", matching("Bearer XXX"))
            .willReturn(ResponseDefinitionBuilder.okForJson(secretsResponse)));

    // New secrets should be resolved
    assertThat(consoleSecretProvider.getSecret("secretKey")).isEqualTo("newSecretValue");
  }
}
