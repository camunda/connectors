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
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.common.auth.Authentication;
import io.camunda.common.auth.Product;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

public class ConsoleSecretProviderTest {

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Test
  void testSuccessfulSecretsHandling() {
    // Mock authentication
    var auth = Mockito.mock(Authentication.class);
    Map.Entry<String, String> authToken =
        Collections.singletonMap("Authorization", "Bearer XXX").entrySet().iterator().next();
    when(auth.getTokenHeader(Product.CONSOLE)).thenReturn(authToken);

    // Mock response
    var secretsResponse = Collections.singletonMap("secretKey", "secretValue");
    wm.stubFor(
        get(urlPathMatching("/secrets"))
            .withHeader("Authorization", matching(authToken.getValue()))
            .willReturn(ResponseDefinitionBuilder.okForJson(secretsResponse)));

    // Test the client
    ConsoleSecretApiClient client = new ConsoleSecretApiClient(wm.baseUrl() + "/secrets", auth);
    var secrets = client.getSecrets();
    assertThat(secrets).isEqualTo(secretsResponse);

    // Test the provider
    var consoleSecretProvider = new ConsoleSecretProvider(client, Duration.ofSeconds(10));
    assertThat(consoleSecretProvider.getSecret("secretKey")).isEqualTo("secretValue");
  }
}
