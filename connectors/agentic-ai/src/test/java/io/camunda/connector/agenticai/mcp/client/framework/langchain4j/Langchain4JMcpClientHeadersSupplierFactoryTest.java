/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.AuthenticationConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.AuthenticationConfiguration.AuthenticationType;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientHttpTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.auth.BasicAuthHeadersSupplier;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.auth.BearerAuthHeadersSupplier;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.auth.OAuthHeadersSupplier;
import io.camunda.connector.agenticai.mcp.client.model.auth.BasicAuthentication;
import io.camunda.connector.agenticai.mcp.client.model.auth.BearerAuthentication;
import io.camunda.connector.agenticai.mcp.client.model.auth.OAuthAuthentication;
import io.camunda.connector.agenticai.mcp.client.model.auth.OAuthAuthentication.ClientAuthenticationMethod;
import io.camunda.connector.http.client.authentication.OAuthService;
import io.camunda.connector.http.client.client.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Langchain4JMcpClientHeadersSupplierFactoryTest {

  private static final Map<String, String> STATIC_HEADERS =
      Map.of("X-Static-Header", "static-value");

  @Mock private OAuthService oAuthService;
  @Mock private HttpClient httpClient;
  @Mock private ObjectMapper objectMapper;

  private Langchain4JMcpClientHeadersSupplierFactory factory;

  @BeforeEach
  void setUp() {
    factory =
        spy(new Langchain4JMcpClientHeadersSupplierFactory(oAuthService, httpClient, objectMapper));
  }

  @Nested
  class NoAuthentication {

    private static final AuthenticationConfiguration NO_AUTH_CONFIG =
        new AuthenticationConfiguration(AuthenticationType.NONE, null, null, null);

    @Test
    void shouldReturnStaticHeadersOnly() {
      final var transport = createTransport(STATIC_HEADERS, NO_AUTH_CONFIG);

      final var headers = factory.createHttpHeadersSupplier(transport).get();
      assertThat(headers).containsExactlyEntriesOf(STATIC_HEADERS);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldReturnEmptyMapWhenNoStaticHeaders(Map<String, String> staticHeaders) {
      final var transport = createTransport(staticHeaders, NO_AUTH_CONFIG);

      final var headers = factory.createHttpHeadersSupplier(transport).get();
      assertThat(headers).isEmpty();
    }
  }

  @Nested
  class BasicAuth {

    private final AuthenticationConfiguration BASIC_AUTH_CONFIG =
        new AuthenticationConfiguration(
            AuthenticationType.BASIC,
            new BasicAuthentication("test-username", "test-password"),
            null,
            null);

    @Test
    void shouldCombineStaticAndBasicAuthHeaders() {
      withMockedBasicAuthHeadersSupplier(
          () -> {
            final var transport = createTransport(STATIC_HEADERS, BASIC_AUTH_CONFIG);
            final var headers = factory.createHttpHeadersSupplier(transport).get();

            LinkedHashMap<String, String> expectedHeaders = new LinkedHashMap<>(STATIC_HEADERS);
            expectedHeaders.put("Authorization", "Basic constructed");

            assertThat(headers).containsExactlyEntriesOf(expectedHeaders);
          });
    }

    @Test
    void shouldOverwriteStaticAuthorizationHeader() {
      withMockedBasicAuthHeadersSupplier(
          () -> {
            final var transport =
                createTransport(Map.of("Authorization", "Static"), BASIC_AUTH_CONFIG);
            final var headers = factory.createHttpHeadersSupplier(transport).get();

            assertThat(headers)
                .containsExactlyEntriesOf(Map.of("Authorization", "Basic constructed"));
          });
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldReturnOnlyBasicAuthHeadersWhenNoStaticHeaders(Map<String, String> staticHeaders) {
      withMockedBasicAuthHeadersSupplier(
          () -> {
            final var transport = createTransport(staticHeaders, BASIC_AUTH_CONFIG);
            final var headers = factory.createHttpHeadersSupplier(transport).get();

            assertThat(headers)
                .containsExactlyEntriesOf(Map.of("Authorization", "Basic constructed"));
          });
    }

    private void withMockedBasicAuthHeadersSupplier(Runnable testCode) {
      try (MockedConstruction<BasicAuthHeadersSupplier> mocked =
          mockConstruction(
              BasicAuthHeadersSupplier.class,
              (constructed, context) -> {
                assertThat(context.arguments().getFirst()).isEqualTo(BASIC_AUTH_CONFIG.basic());
                when(constructed.get()).thenReturn(Map.of("Authorization", "Basic constructed"));
              })) {
        testCode.run();
        assertThat(mocked.constructed()).hasSize(1);
      }
    }
  }

  @Nested
  class BearerAuth {

    private final AuthenticationConfiguration BEARER_AUTH_CONFIG =
        new AuthenticationConfiguration(
            AuthenticationType.BEARER, null, new BearerAuthentication("test-token"), null);

    @Test
    void shouldCombineStaticAndBearerAuthHeaders() {
      withMockedBearerAuthHeadersSupplier(
          () -> {
            final var transport = createTransport(STATIC_HEADERS, BEARER_AUTH_CONFIG);
            final var headers = factory.createHttpHeadersSupplier(transport).get();

            LinkedHashMap<String, String> expectedHeaders = new LinkedHashMap<>(STATIC_HEADERS);
            expectedHeaders.put("Authorization", "Bearer constructed");

            assertThat(headers).containsExactlyEntriesOf(expectedHeaders);
          });
    }

    @Test
    void shouldOverwriteStaticAuthorizationHeader() {
      withMockedBearerAuthHeadersSupplier(
          () -> {
            final var transport =
                createTransport(Map.of("Authorization", "Static"), BEARER_AUTH_CONFIG);
            final var headers = factory.createHttpHeadersSupplier(transport).get();

            assertThat(headers)
                .containsExactlyEntriesOf(Map.of("Authorization", "Bearer constructed"));
          });
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldReturnOnlyBearerAuthHeadersWhenNoStaticHeaders(Map<String, String> staticHeaders) {
      withMockedBearerAuthHeadersSupplier(
          () -> {
            final var transport = createTransport(staticHeaders, BEARER_AUTH_CONFIG);
            final var headers = factory.createHttpHeadersSupplier(transport).get();

            assertThat(headers)
                .containsExactlyEntriesOf(Map.of("Authorization", "Bearer constructed"));
          });
    }

    private void withMockedBearerAuthHeadersSupplier(Runnable testCode) {
      try (MockedConstruction<BearerAuthHeadersSupplier> mocked =
          mockConstruction(
              BearerAuthHeadersSupplier.class,
              (constructed, context) -> {
                assertThat(context.arguments().getFirst()).isEqualTo(BEARER_AUTH_CONFIG.bearer());
                when(constructed.get()).thenReturn(Map.of("Authorization", "Bearer constructed"));
              })) {
        testCode.run();
        assertThat(mocked.constructed()).hasSize(1);
      }
    }
  }

  @Nested
  class OAuth {

    private static final AuthenticationConfiguration OAUTH_CONFIG =
        new AuthenticationConfiguration(
            AuthenticationType.OAUTH,
            null,
            null,
            new OAuthAuthentication(
                "http://auth.example.com/token",
                "my-client-id",
                "my-client-secret",
                "https://api.example.com",
                ClientAuthenticationMethod.BASIC_AUTH_HEADER,
                "openid my-scope"));

    @Test
    void shouldCombineStaticAndOAuthHeaders() {
      withMockedOAuthHeadersSupplier(
          () -> {
            final var transport = createTransport(STATIC_HEADERS, OAUTH_CONFIG);
            final var headers = factory.createHttpHeadersSupplier(transport).get();

            LinkedHashMap<String, String> expectedHeaders = new LinkedHashMap<>(STATIC_HEADERS);
            expectedHeaders.put("Authorization", "Bearer oauth");

            assertThat(headers).containsExactlyEntriesOf(expectedHeaders);
          });
    }

    @Test
    void shouldOverwriteStaticAuthorizationHeader() {
      withMockedOAuthHeadersSupplier(
          () -> {
            final var transport = createTransport(Map.of("Authorization", "Static"), OAUTH_CONFIG);
            final var headers = factory.createHttpHeadersSupplier(transport).get();

            assertThat(headers).containsExactlyEntriesOf(Map.of("Authorization", "Bearer oauth"));
          });
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldReturnOnlyBearerAuthHeadersWhenNoStaticHeaders(Map<String, String> staticHeaders) {
      withMockedOAuthHeadersSupplier(
          () -> {
            final var transport = createTransport(staticHeaders, OAUTH_CONFIG);
            final var headers = factory.createHttpHeadersSupplier(transport).get();

            assertThat(headers).containsExactlyEntriesOf(Map.of("Authorization", "Bearer oauth"));
          });
    }

    private void withMockedOAuthHeadersSupplier(Runnable testCode) {
      try (MockedConstruction<OAuthHeadersSupplier> mocked =
          mockConstruction(
              OAuthHeadersSupplier.class,
              (constructed, context) -> {
                assertThat(context.arguments().get(0)).isEqualTo(oAuthService);
                assertThat(context.arguments().get(1)).isEqualTo(httpClient);
                assertThat(context.arguments().get(2)).isEqualTo(objectMapper);
                assertThat(context.arguments().get(3)).isEqualTo(OAUTH_CONFIG.oauth());
                when(constructed.get()).thenReturn(Map.of("Authorization", "Bearer oauth"));
              })) {
        testCode.run();
        assertThat(mocked.constructed()).hasSize(1);
      }
    }
  }

  private McpHttpTransportStub createTransport(
      Map<String, String> headers, AuthenticationConfiguration authConfig) {
    return new McpHttpTransportStub(headers, authConfig);
  }

  private record McpHttpTransportStub(
      Map<String, String> headers, AuthenticationConfiguration authentication)
      implements McpClientHttpTransportConfiguration {
    @Override
    public String url() {
      return "http://api.example.com";
    }

    @Override
    public Duration timeout() {
      return Duration.ofSeconds(1);
    }
  }
}
