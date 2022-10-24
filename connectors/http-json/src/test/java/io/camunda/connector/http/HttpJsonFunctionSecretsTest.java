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
package io.camunda.connector.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.google.gson.JsonObject;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.http.auth.Authentication;
import io.camunda.connector.http.auth.BasicAuthentication;
import io.camunda.connector.http.auth.BearerAuthentication;
import io.camunda.connector.http.auth.NoAuthentication;
import io.camunda.connector.http.model.HttpJsonRequest;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class HttpJsonFunctionSecretsTest extends BaseTest {
  private static final String SUCCESS_REPLACE_SECRETS_CASES_PATH =
      "src/test/resources/requests/success-cases-replace-secrets.json";

  private OutboundConnectorContext context;

  protected static Stream<String> successReplaceSecretsCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_REPLACE_SECRETS_CASES_PATH);
  }

  @ParameterizedTest(name = "Should replace request secrets")
  @MethodSource("successReplaceSecretsCases")
  void replaceSecrets_shouldReplaceRequestSecrets(String input) {
    // Given request with secrets
    HttpJsonRequest httpJsonRequest = gson.fromJson(input, HttpJsonRequest.class);
    context = getContextBuilderWithSecrets().variables(httpJsonRequest).build();
    // When
    context.replaceSecrets(httpJsonRequest);
    // Then should replace secrets
    assertThat(httpJsonRequest.getUrl()).isEqualTo(ActualValue.URL);
    assertThat(httpJsonRequest.getMethod()).isEqualTo(ActualValue.METHOD);
    assertThat(httpJsonRequest.getConnectionTimeoutInSeconds())
        .isEqualTo(ActualValue.CONNECT_TIMEOUT);
  }

  @ParameterizedTest(name = "Should replace auth secrets")
  @MethodSource("successReplaceSecretsCases")
  void replaceSecrets_shouldReplaceAuthSecrets(String input) {
    // Given request with secrets
    HttpJsonRequest httpJsonRequest = gson.fromJson(input, HttpJsonRequest.class);
    context = getContextBuilderWithSecrets().variables(httpJsonRequest).build();
    // When
    context.replaceSecrets(httpJsonRequest);
    // Then should replace secrets
    Authentication authentication = httpJsonRequest.getAuthentication();
    if (authentication instanceof NoAuthentication) {
      // nothing check in this case
    } else if (authentication instanceof BearerAuthentication) {
      BearerAuthentication bearerAuth = (BearerAuthentication) authentication;
      assertThat(bearerAuth.getToken()).isEqualTo(ActualValue.Authentication.TOKEN);
    } else if (authentication instanceof BasicAuthentication) {
      BasicAuthentication basicAuth = (BasicAuthentication) authentication;
      assertThat(basicAuth.getPassword()).isEqualTo(ActualValue.Authentication.PASSWORD);
      assertThat(basicAuth.getUsername()).isEqualTo(ActualValue.Authentication.USERNAME);
    } else {
      fail("unknown authentication type");
    }
  }

  @ParameterizedTest(name = "Should replace QueryParameters secrets")
  @MethodSource("successReplaceSecretsCases")
  void replaceSecrets_shouldReplaceQueryParametersSecrets(String input) {
    // Given request with secrets
    HttpJsonRequest httpJsonRequest = gson.fromJson(input, HttpJsonRequest.class);
    context = getContextBuilderWithSecrets().variables(httpJsonRequest).build();
    // When
    context.replaceSecrets(httpJsonRequest);
    // Then should replace secrets
    JsonObject queryParams =
        gson.toJsonTree(httpJsonRequest.getQueryParameters()).getAsJsonObject();

    assertThat(queryParams.get(JsonKeys.QUERY).getAsString())
        .isEqualTo(ActualValue.QueryParameters.QUEUE);
    assertThat(queryParams.get(JsonKeys.PRIORITY).getAsString())
        .isEqualTo(ActualValue.QueryParameters.PRIORITY);
  }

  @ParameterizedTest(name = "Should replace headers secrets")
  @MethodSource("successReplaceSecretsCases")
  void replaceSecrets_shouldReplaceHeadersSecrets(String input) {
    // Given request with secrets
    HttpJsonRequest httpJsonRequest = gson.fromJson(input, HttpJsonRequest.class);
    context = getContextBuilderWithSecrets().variables(httpJsonRequest).build();
    // When
    context.replaceSecrets(httpJsonRequest);
    // Then should replace secrets
    JsonObject headers = gson.toJsonTree(httpJsonRequest.getHeaders()).getAsJsonObject();

    assertThat(headers.get(JsonKeys.CLUSTER_ID).getAsString())
        .isEqualTo(ActualValue.Headers.CLUSTER_ID);
    assertThat(headers.get(JsonKeys.USER_AGENT).getAsString())
        .isEqualTo(ActualValue.Headers.USER_AGENT);
  }
}
