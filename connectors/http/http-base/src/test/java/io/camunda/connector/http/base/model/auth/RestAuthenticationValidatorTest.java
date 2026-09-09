/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorExceptionBuilder;
import io.camunda.connector.api.validation.ConfigurationValidationResult.Status;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.http.client.authentication.OAuthConstants;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RestAuthenticationValidatorTest {

  private static final String SENSITIVE = "SENSITIVE-DETAIL";

  private static RestAuthenticationConfiguration configuration(Authentication authentication) {
    return new RestAuthenticationConfiguration(authentication, null);
  }

  private static OAuthAuthentication clientCredentials(String tokenEndpoint) {
    return new OAuthAuthentication(
        tokenEndpoint, "client-id", "client-secret", null, OAuthConstants.CREDENTIALS_BODY, null);
  }

  private static OAuthRefreshTokenAuthentication refreshToken(String tokenEndpoint) {
    return new OAuthRefreshTokenAuthentication(
        tokenEndpoint, "client-id", "client-secret", "refresh-token", null);
  }

  @Test
  @SuppressWarnings("rawtypes")
  void isDiscoverableViaTheServiceLoader() {
    assertThat(
            ServiceLoader.load(ConfigurationValidator.class).stream()
                .map(ServiceLoader.Provider::type))
        .contains(RestAuthenticationValidator.class);
  }

  @Nested
  class VariantDispatch {

    private final RestAuthenticationValidator validator = new RestAuthenticationValidator();

    @Test
    void noAuthenticationIsRejected() {
      var result = validator.validate(configuration(new NoAuthentication()));

      assertThat(result.status()).isEqualTo(Status.FAILURE);
      assertThat(result.code()).isEqualTo("INVALID_INPUT");
    }

    @Test
    void missingAuthenticationIsRejected() {
      var result = validator.validate(configuration(null));

      assertThat(result.status()).isEqualTo(Status.FAILURE);
      assertThat(result.code()).isEqualTo("INVALID_INPUT");
    }
  }

  @Nested
  class FailureClassification {

    @Test
    void unauthorizedOn401() {
      assertUnauthorized(httpFailure(401, Map.of()));
    }

    @Test
    void unauthorizedOn403() {
      assertUnauthorized(httpFailure(403, Map.of()));
    }

    @Test
    void unauthorizedOnInvalidClientReportedAs400() {
      assertUnauthorized(httpFailure(400, Map.of(OAuthConstants.ERROR, "invalid_client")));
    }

    @Test
    void unauthorizedOnInvalidGrantReportedAs400() {
      assertUnauthorized(httpFailure(400, Map.of(OAuthConstants.ERROR, "invalid_grant")));
    }

    @Test
    void unauthorizedOnUnauthorizedClientReportedAs400() {
      assertUnauthorized(httpFailure(400, Map.of(OAuthConstants.ERROR, "unauthorized_client")));
    }

    @Test
    void unauthorizedOnExpiredRefreshToken() {
      assertUnauthorized(
          new ConnectorException("OAUTH_REFRESH_TOKEN_EXPIRED", "expired " + SENSITIVE));
    }

    @Test
    void unauthorizedWhenTheProviderRequiresInteraction() {
      assertUnauthorized(
          new ConnectorException("OAUTH_INTERACTION_REQUIRED", "interaction " + SENSITIVE));
    }

    @Test
    void errorOnAnUnrelatedOAuthError() {
      assertError(httpFailure(400, Map.of(OAuthConstants.ERROR, "invalid_request")));
    }

    @Test
    void errorWhenTheTokenEndpointIsBroken() {
      assertError(httpFailure(500, Map.of()));
    }

    @Test
    void errorWhenTheResponseCarriesNoToken() {
      assertError(new ConnectorException("OAUTH_TOKEN_ERROR", "no access_token " + SENSITIVE));
    }

    @Test
    void errorOnAnyOtherException() {
      assertError(new RuntimeException("boom " + SENSITIVE));
    }

    @Test
    void errorWhenTheErrorVariablesDoNotCarryABody() {
      assertError(
          new ConnectorExceptionBuilder()
              .errorCode("400")
              .message("bad request")
              .errorVariables(Map.of("response", "not-a-map"))
              .build());
    }

    private void assertUnauthorized(Exception thrown) {
      var result = RestAuthenticationValidator.classifyFailure(thrown);

      assertThat(result.status()).isEqualTo(Status.FAILURE);
      assertThat(result.code()).isEqualTo("UNAUTHORIZED");
      assertThat(result.message())
          .isEqualTo(RestAuthenticationValidator.UNAUTHORIZED_MESSAGE)
          .doesNotContain(SENSITIVE);
    }

    private void assertError(Exception thrown) {
      var result = RestAuthenticationValidator.classifyFailure(thrown);

      assertThat(result.status()).isEqualTo(Status.FAILURE);
      assertThat(result.code()).isEqualTo("ERROR");
      assertThat(result.message())
          .isEqualTo(RestAuthenticationValidator.GENERIC_MESSAGE)
          .doesNotContain(SENSITIVE);
    }

    private ConnectorException httpFailure(int status, Map<String, String> body) {
      return new ConnectorExceptionBuilder()
          .errorCode(String.valueOf(status))
          .message("Unauthorized")
          .errorVariables(
              Map.of(
                  "response",
                  Map.of(
                      "headers",
                      Map.of(),
                      "body",
                      body.isEmpty() ? Map.of(OAuthConstants.ERROR_DESCRIPTION, SENSITIVE) : body)))
          .build();
    }
  }

  @Nested
  @WireMockTest
  class AgainstARealTokenEndpoint {

    private final RestAuthenticationValidator validator = new RestAuthenticationValidator();

    private String tokenEndpoint(WireMockRuntimeInfo wireMock) {
      return "http://localhost:" + wireMock.getHttpPort() + "/token";
    }

    @Test
    void clientCredentialsSucceedWhenATokenIsIssued(WireMockRuntimeInfo wireMock) {
      WireMock.stubFor(
          WireMock.post("/token")
              .willReturn(WireMock.okJson("{\"access_token\":\"a-token\",\"expires_in\":3600}")));

      var result = validator.validate(configuration(clientCredentials(tokenEndpoint(wireMock))));

      assertThat(result.status()).isEqualTo(Status.SUCCESS);
      WireMock.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/token"))
              .withRequestBody(WireMock.containing("grant_type=client_credentials")));
    }

    @Test
    void clientCredentialsAreUnauthorizedWhenTheSecretIsRejected(WireMockRuntimeInfo wireMock) {
      WireMock.stubFor(
          WireMock.post("/token")
              .willReturn(
                  WireMock.jsonResponse(
                      "{\"error\":\"invalid_client\",\"error_description\":\"" + SENSITIVE + "\"}",
                      401)));

      var result = validator.validate(configuration(clientCredentials(tokenEndpoint(wireMock))));

      assertThat(result.status()).isEqualTo(Status.FAILURE);
      assertThat(result.code()).isEqualTo("UNAUTHORIZED");
      assertThat(result.message()).doesNotContain(SENSITIVE);
    }

    @Test
    void clientCredentialsAreUnauthorizedWhenTheProviderAnswers400(WireMockRuntimeInfo wireMock) {
      WireMock.stubFor(
          WireMock.post("/token")
              .willReturn(
                  WireMock.jsonResponse(
                      "{\"error\":\"invalid_client\",\"error_description\":\"" + SENSITIVE + "\"}",
                      400)));

      var result = validator.validate(configuration(clientCredentials(tokenEndpoint(wireMock))));

      assertThat(result.code()).isEqualTo("UNAUTHORIZED");
      assertThat(result.message()).doesNotContain(SENSITIVE);
    }

    @Test
    void clientCredentialsErrorWhenTheProviderIsBroken(WireMockRuntimeInfo wireMock) {
      WireMock.stubFor(
          WireMock.post("/token")
              .willReturn(WireMock.serverError().withBody("stack trace with " + SENSITIVE)));

      var result = validator.validate(configuration(clientCredentials(tokenEndpoint(wireMock))));

      assertThat(result.code()).isEqualTo("ERROR");
      assertThat(result.message()).doesNotContain(SENSITIVE);
    }

    @Test
    void clientCredentialsErrorWhenTheResponseCarriesNoToken(WireMockRuntimeInfo wireMock) {
      WireMock.stubFor(WireMock.post("/token").willReturn(WireMock.okJson("{\"nothing\":true}")));

      var result = validator.validate(configuration(clientCredentials(tokenEndpoint(wireMock))));

      assertThat(result.code()).isEqualTo("ERROR");
    }

    @Test
    void refreshTokenIsUnsupportedBecauseTheProviderMayRotateIt() {
      var result = validator.validate(configuration(refreshToken("http://example.com/token")));

      assertThat(result.status()).isEqualTo(Status.UNSUPPORTED);
    }

    @Test
    void errorWhenTheTokenEndpointIsUnreachable() {
      var result = validator.validate(configuration(clientCredentials("http://localhost:1/token")));

      assertThat(result.status()).isEqualTo(Status.FAILURE);
      assertThat(result.code()).isEqualTo("ERROR");
    }
  }

  @Nested
  @WireMockTest
  class AgainstARealEndpoint {

    private final RestAuthenticationValidator validator = new RestAuthenticationValidator();

    private RestAuthenticationConfiguration boundTo(
        Authentication authentication, WireMockRuntimeInfo wireMock) {
      return new RestAuthenticationConfiguration(
          authentication, "http://localhost:" + wireMock.getHttpPort() + "/api");
    }

    /** The stub only answers when the credential arrived, so a success proves it was presented. */
    @Test
    void basicSucceedsWhenTheEndpointAcceptsIt(WireMockRuntimeInfo wireMock) {
      WireMock.stubFor(
          WireMock.get("/api").withBasicAuth("user", "password").willReturn(WireMock.ok()));

      var result =
          validator.validate(boundTo(new BasicAuthentication("user", "password"), wireMock));

      assertThat(result.status()).isEqualTo(Status.SUCCESS);
    }

    @Test
    void apiKeySucceedsWhenTheEndpointAcceptsIt(WireMockRuntimeInfo wireMock) {
      WireMock.stubFor(
          WireMock.get("/api")
              .withHeader("X-Api-Key", WireMock.equalTo("secret"))
              .willReturn(WireMock.ok()));

      var result =
          validator.validate(
              boundTo(
                  new ApiKeyAuthentication(ApiKeyLocation.HEADERS, "X-Api-Key", "secret"),
                  wireMock));

      assertThat(result.status()).isEqualTo(Status.SUCCESS);
    }

    @Test
    void bearerIsUnauthorizedWhenTheEndpointRejectsIt(WireMockRuntimeInfo wireMock) {
      WireMock.stubFor(
          WireMock.get("/api")
              .willReturn(WireMock.unauthorized().withBody("denied because " + SENSITIVE)));

      var result = validator.validate(boundTo(new BearerAuthentication("token"), wireMock));

      assertThat(result.status()).isEqualTo(Status.FAILURE);
      assertThat(result.code()).isEqualTo("UNAUTHORIZED");
      assertThat(result.message()).doesNotContain(SENSITIVE);
    }

    @Test
    void errorWhenTheEndpointIsUnreachable() {
      var result =
          validator.validate(
              new RestAuthenticationConfiguration(
                  new BearerAuthentication("token"), "http://localhost:1/api"));

      assertThat(result.status()).isEqualTo(Status.FAILURE);
      assertThat(result.code()).isEqualTo("ERROR");
    }
  }
}
