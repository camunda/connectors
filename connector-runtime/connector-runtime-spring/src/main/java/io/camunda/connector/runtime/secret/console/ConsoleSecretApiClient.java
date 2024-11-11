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
package io.camunda.connector.runtime.secret.console;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.secret.console.TokenResponseMapper.JacksonTokenResponseMapper;
import java.io.IOException;
import java.util.Map;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsoleSecretApiClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleSecretApiClient.class);

  private final String secretsEndpoint;

  private final Authentication authentication;

  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER;

  private static final TypeReference<Map<String, String>> mapTypeReference =
      new TypeReference<>() {};

  public ConsoleSecretApiClient(String secretsEndpoint, JwtCredential jwt) {
    TokenResponseMapper tokenResponseMapper =
        new JacksonTokenResponseMapper(ConnectorsObjectMapperSupplier.DEFAULT_MAPPER);
    this.authentication = new JwtAuthentication(jwt, tokenResponseMapper);
    this.secretsEndpoint = secretsEndpoint;
  }

  public ConsoleSecretApiClient(String secretsEndpoint, Authentication authentication) {
    this.secretsEndpoint = secretsEndpoint;
    this.authentication = authentication;
  }

  public Map<String, String> getSecrets() {
    LOGGER.debug("Loading secrets from {}", secretsEndpoint);
    try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
      var request = new HttpGet(secretsEndpoint);
      var authHeader = authentication.getTokenHeader();
      authHeader.forEach(request::addHeader);
      return httpClient.execute(request, this::handleSecretsResponse);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Map<String, String> handleSecretsResponse(ClassicHttpResponse response)
      throws IOException {
    return switch (response.getCode()) {
      case 200 -> objectMapper.readValue(response.getEntity().getContent(), mapTypeReference);
      case 401, 403 -> {
        authentication.resetToken();
        throw new RuntimeException("Authentication failed: " + response.getCode());
      }
      default ->
          throw new RuntimeException(
              "Unable to handle response from Console secrets: " + response.getCode());
    };
  }
}
