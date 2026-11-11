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
package io.camunda.connector.http.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.http.base.model.auth.ApiKeyAuthentication;
import io.camunda.connector.http.base.model.auth.BasicAuthentication;
import io.camunda.connector.http.base.model.auth.BearerAuthentication;
import io.camunda.connector.http.base.model.auth.OAuthAuthentication;
import io.camunda.connector.http.rest.model.HttpJsonRequest;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the credential bound by connectors that wrap {@code http-json} for one known service
 * (Asana, OpenAI, EasyPost, Google Maps Platform, ...). Their element templates save the credential
 * under {@code serviceAuthenticationConfiguration}, with the authentication mechanism pinned by
 * hidden constants in the credential type.
 */
@WireMockTest
class HttpJsonFunctionServiceCredentialTest {

  private final HttpJsonFunction function = new HttpJsonFunction();

  @BeforeEach
  void stubEndpoint() {
    stubFor(any(urlPathEqualTo("/service")).willReturn(aResponse().withStatus(200)));
  }

  private static OutboundConnectorContext context(String variables) {
    return OutboundConnectorContextBuilder.create()
        .includeAllValidators()
        .variables(variables)
        .build();
  }

  @Test
  void bearerCredentialIsSentWithoutAnyInlineAuthentication(WireMockRuntimeInfo wiremock) {
    var variables =
        """
        {
          "method": "get",
          "url": "%s/service",
          "serviceAuthenticationConfiguration": {
            "authentication": { "type": "bearer", "token": "service-token" }
          }
        }
        """
            .formatted(wiremock.getHttpBaseUrl());

    function.execute(context(variables));

    verify(
        anyRequestedFor(urlPathEqualTo("/service"))
            .withHeader("Authorization", equalTo("Bearer service-token")));
  }

  @Test
  void basicCredentialWithTheEmptyPasswordMarkerSendsAnEmptyPassword(WireMockRuntimeInfo wiremock) {
    var variables =
        """
        {
          "method": "get",
          "url": "%s/service",
          "serviceAuthenticationConfiguration": {
            "authentication": {
              "type": "basic",
              "username": "api-key",
              "password": "SPEC_PASSWORD_EMPTY_PATTERN"
            }
          }
        }
        """
            .formatted(wiremock.getHttpBaseUrl());

    function.execute(context(variables));

    var expected =
        "Basic " + Base64.getEncoder().encodeToString("api-key:".getBytes(StandardCharsets.UTF_8));
    verify(
        anyRequestedFor(urlPathEqualTo("/service")).withHeader("Authorization", equalTo(expected)));
  }

  @Test
  void apiKeyCredentialInTheQueryIsAppendedToAPostWithABody(WireMockRuntimeInfo wiremock) {
    var variables =
        """
        {
          "method": "post",
          "url": "%s/service",
          "body": { "address": "somewhere" },
          "serviceAuthenticationConfiguration": {
            "authentication": {
              "type": "apiKey",
              "apiKeyLocation": "query",
              "name": "key",
              "value": "maps-key"
            }
          }
        }
        """
            .formatted(wiremock.getHttpBaseUrl());

    function.execute(context(variables));

    verify(
        anyRequestedFor(urlPathEqualTo("/service"))
            .withQueryParam("key", equalTo("maps-key"))
            .withRequestBody(equalToJson("{ \"address\": \"somewhere\" }")));
  }

  @Test
  void apiKeyCredentialInTheQueryJoinsTheTaskQueryParameters(WireMockRuntimeInfo wiremock) {
    var variables =
        """
        {
          "method": "get",
          "url": "%s/service",
          "queryParameters": { "input": "somewhere", "language": "en" },
          "serviceAuthenticationConfiguration": {
            "authentication": {
              "type": "apiKey",
              "apiKeyLocation": "query",
              "name": "key",
              "value": "maps-key"
            }
          }
        }
        """
            .formatted(wiremock.getHttpBaseUrl());

    function.execute(context(variables));

    verify(
        anyRequestedFor(urlPathEqualTo("/service"))
            .withQueryParam("key", equalTo("maps-key"))
            .withQueryParam("input", equalTo("somewhere"))
            .withQueryParam("language", equalTo("en")));
  }

  @Test
  void credentialAuthenticationTakesPrecedenceOverInlineAuthentication() {
    var variables =
        """
        {
          "method": "get",
          "url": "http://localhost:8080/service",
          "authentication": { "type": "basic", "username": "inline", "password": "inline" },
          "serviceAuthenticationConfiguration": {
            "authentication": { "type": "bearer", "token": "service-token" }
          }
        }
        """;

    var request = context(variables).bindVariables(HttpJsonRequest.class);

    assertThat(request.getAuthentication()).isInstanceOf(BearerAuthentication.class);
  }

  @Test
  void inlineAuthenticationIsTheFallbackWhenNoCredentialIsBound() {
    var variables =
        """
        {
          "method": "get",
          "url": "http://localhost:8080/service",
          "authentication": { "type": "basic", "username": "inline", "password": "inline" }
        }
        """;

    var request = context(variables).bindVariables(HttpJsonRequest.class);

    assertThat(request.getAuthentication()).isInstanceOf(BasicAuthentication.class);
  }

  @Test
  void apiKeyCredentialBindsAsAnApiKeyAuthentication() {
    var variables =
        """
        {
          "method": "get",
          "url": "http://localhost:8080/service",
          "serviceAuthenticationConfiguration": {
            "authentication": {
              "type": "apiKey",
              "apiKeyLocation": "query",
              "name": "key",
              "value": "maps-key"
            }
          }
        }
        """;

    var request = context(variables).bindVariables(HttpJsonRequest.class);

    assertThat(request.getAuthentication()).isInstanceOf(ApiKeyAuthentication.class);
  }

  /**
   * Guards the {@code @Valid} cascade through {@code serviceAuthenticationConfiguration ->
   * authentication}: a credential saved with a blank secret is rejected the way the same value
   * would be inline.
   */
  @Test
  void credentialWithBlankTokenIsRejected() {
    var variables =
        """
        {
          "method": "get",
          "url": "http://localhost:8080/service",
          "serviceAuthenticationConfiguration": {
            "authentication": { "type": "bearer", "token": "" }
          }
        }
        """;

    assertThatThrownBy(() -> context(variables).bindVariables(HttpJsonRequest.class))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("token");
  }

  @Test
  void credentialWithoutAuthenticationIsRejected() {
    var variables =
        """
        {
          "method": "get",
          "url": "http://localhost:8080/service",
          "serviceAuthenticationConfiguration": {}
        }
        """;

    assertThatThrownBy(() -> context(variables).bindVariables(HttpJsonRequest.class))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("A credential must specify an authentication mechanism");
  }

  @Test
  void credentialWithNoAuthenticationIsRejected() {
    var variables =
        """
        {
          "method": "get",
          "url": "http://localhost:8080/service",
          "serviceAuthenticationConfiguration": {
            "authentication": { "type": "noAuth" }
          }
        }
        """;

    assertThatThrownBy(() -> context(variables).bindVariables(HttpJsonRequest.class))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("A credential must specify an authentication mechanism");
  }

  @Test
  void baseUrlOfTheCredentialIsJoinedWithTheRelativeUrlOfTheTask(WireMockRuntimeInfo wiremock) {
    var variables =
        """
        {
          "method": "get",
          "relativeUrl": "/api/now/table/incident",
          "serviceAuthenticationConfiguration": {
            "baseUrl": "%s",
            "authentication": { "type": "bearer", "token": "service-token" }
          }
        }
        """
            .formatted(wiremock.getHttpBaseUrl());
    stubFor(any(urlPathEqualTo("/api/now/table/incident")).willReturn(aResponse().withStatus(200)));

    function.execute(context(variables));

    verify(
        anyRequestedFor(urlPathEqualTo("/api/now/table/incident"))
            .withHeader("Authorization", equalTo("Bearer service-token")));
  }

  @Test
  void trailingSlashesOfTheCredentialBaseUrlAreRemovedBeforeJoining() {
    var variables =
        """
        {
          "method": "get",
          "relativeUrl": "/api/v4/issues",
          "serviceAuthenticationConfiguration": {
            "baseUrl": "http://localhost:8080//",
            "authentication": { "type": "bearer", "token": "service-token" }
          }
        }
        """;

    var request = context(variables).bindVariables(HttpJsonRequest.class);

    assertThat(request.getUrl()).isEqualTo("http://localhost:8080/api/v4/issues");
  }

  @Test
  void whitespaceAroundTheCredentialBaseUrlIsRemovedBeforeJoining() {
    var variables =
        """
        {
          "method": "get",
          "relativeUrl": "/api/v4/issues",
          "serviceAuthenticationConfiguration": {
            "baseUrl": "http://localhost:8080/ ",
            "authentication": { "type": "bearer", "token": "service-token" }
          }
        }
        """;

    var request = context(variables).bindVariables(HttpJsonRequest.class);

    assertThat(request.getUrl()).isEqualTo("http://localhost:8080/api/v4/issues");
  }

  @Test
  void inlineUrlWinsOverTheCredentialBaseUrl() {
    var variables =
        """
        {
          "method": "get",
          "url": "http://localhost:8081/x",
          "relativeUrl": "/api",
          "serviceAuthenticationConfiguration": {
            "baseUrl": "http://localhost:8080",
            "authentication": { "type": "bearer", "token": "service-token" }
          }
        }
        """;

    var request = context(variables).bindVariables(HttpJsonRequest.class);

    assertThat(request.getUrl()).isEqualTo("http://localhost:8081/x");
  }

  @Test
  void restCredentialWinsOverServiceCredentialForTheUrlAsForTheAuthentication() {
    var variables =
        """
        {
          "method": "get",
          "relativeUrl": "/api",
          "authenticationConfiguration": {
            "authentication": { "type": "bearer", "token": "rest-token" },
            "url": "http://localhost:8081/rest"
          },
          "serviceAuthenticationConfiguration": {
            "baseUrl": "http://localhost:8082",
            "authentication": { "type": "bearer", "token": "service-token" }
          }
        }
        """;

    var request = context(variables).bindVariables(HttpJsonRequest.class);

    assertThat(request.getUrl()).isEqualTo("http://localhost:8081/rest");
    assertThat(((BearerAuthentication) request.getAuthentication()).token())
        .isEqualTo("rest-token");
  }

  @Test
  void relativeUrlWithoutACredentialBaseUrlIsNotAUrl() {
    var variables =
        """
        {
          "method": "get",
          "relativeUrl": "/api",
          "serviceAuthenticationConfiguration": {
            "authentication": { "type": "bearer", "token": "service-token" }
          }
        }
        """;

    assertThatThrownBy(() -> context(variables).bindVariables(HttpJsonRequest.class))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("No URL provided");
  }

  @Test
  void relativeUrlThatDoesNotStartWithASlashIsRejected() {
    var variables =
        """
        {
          "method": "get",
          "relativeUrl": "-x:1080/steal",
          "serviceAuthenticationConfiguration": {
            "baseUrl": "http://localhost",
            "authentication": { "type": "bearer", "token": "service-token" }
          }
        }
        """;

    assertThatThrownBy(() -> context(variables).bindVariables(HttpJsonRequest.class))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("Must start with '/'");
  }

  @Test
  void placeholdersInTheBaseUrlAreFilledFromTheParameters() {
    var variables =
        """
        {
          "method": "get",
          "relativeUrl": "/api/now/table/incident",
          "serviceAuthenticationConfiguration": {
            "baseUrl": "http://localhost:${port}",
            "parameters": { "port": "8080" },
            "authentication": { "type": "basic", "username": "admin", "password": "secret" }
          }
        }
        """;

    var request = context(variables).bindVariables(HttpJsonRequest.class);

    assertThat(request.getUrl()).isEqualTo("http://localhost:8080/api/now/table/incident");
  }

  @Test
  void placeholdersAreFilledInTheOAuthTokenEndpointButNotInTheSecret() {
    var variables =
        """
        {
          "method": "get",
          "url": "http://localhost:8080/x",
          "serviceAuthenticationConfiguration": {
            "parameters": { "host": "localhost:8080" },
            "authentication": {
              "type": "oauth-client-credentials-flow",
              "oauthTokenEndpoint": "http://${host}/oidc/v1/token",
              "clientId": "id",
              "clientSecret": "${host}",
              "clientAuthentication": "basicAuthHeader"
            }
          }
        }
        """;

    var request = context(variables).bindVariables(HttpJsonRequest.class);

    var oauth = (OAuthAuthentication) request.getAuthentication();
    assertThat(oauth.oauthTokenEndpoint()).isEqualTo("http://localhost:8080/oidc/v1/token");
    assertThat(oauth.clientSecret()).isEqualTo("${host}");
  }

  @Test
  void basicUsernameCanBeDerivedFromAParameter() {
    var variables =
        """
        {
          "method": "get",
          "url": "https://api.twilio.com/x",
          "serviceAuthenticationConfiguration": {
            "parameters": { "accountSid": "AC123" },
            "authentication": { "type": "basic", "username": "${accountSid}", "password": "token" }
          }
        }
        """;

    var request = context(variables).bindVariables(HttpJsonRequest.class);

    assertThat(((BasicAuthentication) request.getAuthentication()).username()).isEqualTo("AC123");
  }

  @Test
  void aParameterValueThatLooksLikeAPlaceholderIsKeptAsText() {
    var variables =
        """
        {
          "method": "get",
          "relativeUrl": "/api",
          "serviceAuthenticationConfiguration": {
            "baseUrl": "http://localhost:8080",
            "parameters": { "account": "${other}", "other": "x" },
            "authentication": { "type": "basic", "username": "${account}", "password": "secret" }
          }
        }
        """;

    var request = context(variables).bindVariables(HttpJsonRequest.class);

    assertThat(((BasicAuthentication) request.getAuthentication()).username())
        .isEqualTo("${other}");
  }

  @Test
  void parameterValuesInUrlsAreCleanedButTheUsernameIsKeptExact() {
    var variables =
        """
        {
          "method": "get",
          "relativeUrl": "/api",
          "serviceAuthenticationConfiguration": {
            "baseUrl": "http://${host}",
            "parameters": { "host": " localhost:8080/ ", "account": "ab/ " },
            "authentication": {
              "type": "basic",
              "username": "${account}",
              "password": "secret"
            }
          }
        }
        """;

    var request = context(variables).bindVariables(HttpJsonRequest.class);

    assertThat(request.getUrl()).isEqualTo("http://localhost:8080/api");
    assertThat(((BasicAuthentication) request.getAuthentication()).username()).isEqualTo("ab/ ");
  }

  @Test
  void tokenEndpointBuiltFromAHostWithATrailingSlashHasASingleSlash() {
    var variables =
        """
        {
          "method": "get",
          "relativeUrl": "/api",
          "serviceAuthenticationConfiguration": {
            "baseUrl": "http://localhost:8080",
            "parameters": { "host": "http://localhost:8080/" },
            "authentication": {
              "type": "oauth-client-credentials-flow",
              "oauthTokenEndpoint": "${host}/oidc/v1/token",
              "clientId": "id",
              "clientSecret": "secret",
              "clientAuthentication": "credentialsBody"
            }
          }
        }
        """;

    var request = context(variables).bindVariables(HttpJsonRequest.class);

    assertThat(((OAuthAuthentication) request.getAuthentication()).oauthTokenEndpoint())
        .isEqualTo("http://localhost:8080/oidc/v1/token");
  }

  @Test
  void unresolvedPlaceholderInTheBasicUsernameIsRejected() {
    var variables =
        """
        {
          "method": "get",
          "relativeUrl": "/api",
          "serviceAuthenticationConfiguration": {
            "baseUrl": "http://localhost:8080",
            "parameters": { "other": "x" },
            "authentication": { "type": "basic", "username": "${account}", "password": "secret" }
          }
        }
        """;

    assertThatThrownBy(() -> context(variables).bindVariables(HttpJsonRequest.class))
        .hasMessageContaining("parameter 'account', which has no value");
  }

  @Test
  void unresolvedPlaceholderInTheOAuthTokenEndpointIsRejected() {
    var variables =
        """
        {
          "method": "get",
          "relativeUrl": "/api",
          "serviceAuthenticationConfiguration": {
            "baseUrl": "http://localhost:8080",
            "parameters": { "other": "x" },
            "authentication": {
              "type": "oauth-client-credentials-flow",
              "oauthTokenEndpoint": "http://localhost:8080/${tenant}/token",
              "clientId": "id",
              "clientSecret": "secret"
            }
          }
        }
        """;

    assertThatThrownBy(() -> context(variables).bindVariables(HttpJsonRequest.class))
        .hasMessageContaining("parameter 'tenant', which has no value");
  }

  @Test
  void baseUrlWithAParameterThatIsNull() {
    var variables =
        """
        {
          "method": "get",
          "relativeUrl": "/api",
          "serviceAuthenticationConfiguration": {
            "baseUrl": "http://localhost:${port}",
            "parameters": { "port": null },
            "authentication": { "type": "bearer", "token": "service-token" }
          }
        }
        """;

    assertThatThrownBy(() -> context(variables).bindVariables(HttpJsonRequest.class))
        .hasMessageContaining("which has no value");
  }

  @Test
  void baseUrlWithAParameterThatIsEmpty() {
    var variables =
        """
        {
          "method": "get",
          "relativeUrl": "/api",
          "serviceAuthenticationConfiguration": {
            "baseUrl": "http://localhost:${port}",
            "parameters": { "port": "" },
            "authentication": { "type": "bearer", "token": "service-token" }
          }
        }
        """;

    assertThatThrownBy(() -> context(variables).bindVariables(HttpJsonRequest.class))
        .hasMessageContaining("which has no value");
  }

  @Test
  void baseUrlWithAParameterThatHasNoValueIsRejected() {
    var variables =
        """
        {
          "method": "get",
          "relativeUrl": "/api",
          "serviceAuthenticationConfiguration": {
            "baseUrl": "http://localhost:${instance}",
            "parameters": { "other": "x" },
            "authentication": { "type": "bearer", "token": "service-token" }
          }
        }
        """;

    assertThatThrownBy(() -> context(variables).bindVariables(HttpJsonRequest.class))
        .hasMessageContaining("which has no value");
  }
}
