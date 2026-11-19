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
}
