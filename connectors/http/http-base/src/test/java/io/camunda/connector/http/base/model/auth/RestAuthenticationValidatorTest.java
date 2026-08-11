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

  /**
   * The credential's URL is mandatory only for the static-secret authentication types (see {@link
   * RestAuthenticationConfiguration#requiresUrl}), so it is supplied exactly where the record
   * requires it. The validator itself never reads it: what can be checked out-of-band depends on
   * the authentication variant, not on the endpoint the credential is bound to.
   */
  private static RestAuthenticationConfiguration configuration(Authentication authentication) {
    String url =
        authentication != null && RestAuthenticationConfiguration.requiresUrl(authentication)
            ? "https://example.com/api"
            : null;
    return new RestAuthenticationConfiguration(authentication, url);
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
    // The runtime finds validators through META-INF/services only. A missing or misspelled entry
    // leaves the credential silently unvalidatable, and nothing else in the build would catch it.
    assertThat(
            ServiceLoader.load(ConfigurationValidator.class).stream()
                .map(ServiceLoader.Provider::type))
        .contains(RestAuthenticationValidator.class);
  }

  @Nested
  class VariantsWithoutATokenEndpoint {

    /** Nothing may reach the network for a variant that carries no endpoint. */
    private final boolean[] tokenRequested = {false};

    private final RestAuthenticationValidator validator =
        new RestAuthenticationValidator(authentication -> tokenRequested[0] = true);

    @Test
    void noAuthenticationIsUsable() {
      var result = validator.validate(configuration(new NoAuthentication()));

      assertThat(result.status()).isEqualTo(Status.SUCCESS);
      assertThat(tokenRequested[0]).isFalse();
    }

    @Test
    void basicIsNotValidatable() {
      var result = validator.validate(configuration(new BasicAuthentication("user", "password")));

      assertThat(result.status()).isEqualTo(Status.UNSUPPORTED);
      assertThat(tokenRequested[0]).isFalse();
    }

    @Test
    void bearerIsNotValidatable() {
      var result = validator.validate(configuration(new BearerAuthentication("token")));

      assertThat(result.status()).isEqualTo(Status.UNSUPPORTED);
      assertThat(tokenRequested[0]).isFalse();
    }

    @Test
    void apiKeyIsNotValidatable() {
      var result =
          validator.validate(
              configuration(
                  new ApiKeyAuthentication(ApiKeyLocation.HEADERS, "X-Api-Key", "secret")));

      assertThat(result.status()).isEqualTo(Status.UNSUPPORTED);
      assertThat(tokenRequested[0]).isFalse();
    }

    @Test
    void missingAuthenticationIsRejected() {
      var result = validator.validate(configuration(null));

      assertThat(result.status()).isEqualTo(Status.FAILURE);
      assertThat(result.code()).isEqualTo("INVALID_INPUT");
      assertThat(tokenRequested[0]).isFalse();
    }
  }

  /**
   * Covers how a token request outcome becomes a result. Uses the injected seam so every branch is
   * reachable, including the ones a token endpoint reaches only in the field.
   */
  @Nested
  class TokenRequestOutcomeMapping {

    @Test
    void successWhenTheTokenRequestSucceeds() {
      var validator = new RestAuthenticationValidator(authentication -> {});

      var result = validator.validate(configuration(clientCredentials("https://idp/token")));

      assertThat(result.status()).isEqualTo(Status.SUCCESS);
    }

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
      // A malformed request is the client's fault, not the credential's — reporting it as
      // unauthorized would send an operator looking for the wrong problem.
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
      // Nothing guarantees the shape of a ConnectorException's error variables; an unexpected one
      // must not make the lookup throw and turn a clean failure into a crash.
      assertError(
          new ConnectorExceptionBuilder()
              .errorCode("400")
              .message("bad request")
              .errorVariables(Map.of("response", "not-a-map"))
              .build());
    }

    private void assertUnauthorized(RuntimeException thrown) {
      var result = validateThrowing(thrown);

      assertThat(result.status()).isEqualTo(Status.FAILURE);
      assertThat(result.code()).isEqualTo("UNAUTHORIZED");
      assertThat(result.message()).doesNotContain(SENSITIVE);
    }

    private void assertError(RuntimeException thrown) {
      var result = validateThrowing(thrown);

      assertThat(result.status()).isEqualTo(Status.FAILURE);
      assertThat(result.code()).isEqualTo("ERROR");
      assertThat(result.message()).doesNotContain(SENSITIVE);
    }

    private io.camunda.connector.api.validation.ConfigurationValidationResult validateThrowing(
        RuntimeException thrown) {
      var validator =
          new RestAuthenticationValidator(
              authentication -> {
                throw thrown;
              });
      return validator.validate(configuration(clientCredentials("https://idp/token")));
    }

    /** A failure shaped exactly as the HTTP client raises it for a non-2xx token response. */
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

  /**
   * Exercises the real token request against a stub endpoint, so the wiring between the validator,
   * the authentication mapping and the HTTP client is covered rather than stubbed out.
   */
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
    void refreshTokenSucceedsWhenATokenIsIssued(WireMockRuntimeInfo wireMock) {
      WireMock.stubFor(
          WireMock.post("/token").willReturn(WireMock.okJson("{\"access_token\":\"a-token\"}")));

      var result = validator.validate(configuration(refreshToken(tokenEndpoint(wireMock))));

      assertThat(result.status()).isEqualTo(Status.SUCCESS);
      WireMock.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/token"))
              .withRequestBody(WireMock.containing("grant_type=refresh_token")));
    }

    @Test
    void refreshTokenIsUnauthorizedWhenTheGrantIsRejectedWith200(WireMockRuntimeInfo wireMock) {
      // Some providers answer 200 with an error body; the token service turns that into a dedicated
      // error code, which must still read as "the credential is no good".
      WireMock.stubFor(
          WireMock.post("/token")
              .willReturn(
                  WireMock.okJson(
                      "{\"error\":\"invalid_grant\",\"error_description\":\""
                          + SENSITIVE
                          + "\"}")));

      var result = validator.validate(configuration(refreshToken(tokenEndpoint(wireMock))));

      assertThat(result.status()).isEqualTo(Status.FAILURE);
      assertThat(result.code()).isEqualTo("UNAUTHORIZED");
      assertThat(result.message()).doesNotContain(SENSITIVE);
    }

    @Test
    void errorWhenTheTokenEndpointIsUnreachable() {
      // Port 1 is reserved and never listening, so this exercises the transport failure path.
      var result = validator.validate(configuration(clientCredentials("http://localhost:1/token")));

      assertThat(result.status()).isEqualTo(Status.FAILURE);
      assertThat(result.code()).isEqualTo("ERROR");
    }
  }
}
