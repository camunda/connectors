/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.common.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.azure.core.exception.ClientAuthenticationException;
import com.azure.core.http.HttpResponse;
import com.azure.identity.CredentialUnavailableException;
import com.microsoft.aad.msal4j.MsalServiceException;
import io.camunda.connector.api.validation.ConfigurationValidationResult.Status;
import io.camunda.connector.api.validation.ConfigurationValidator;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MicrosoftEntraCredentialValidatorTest {

  private static final ClientCredentialsAuthentication VALID =
      new ClientCredentialsAuthentication("client-id", "tenant-id", "client-secret");

  @Test
  void successWhenTokenCheckPasses() {
    var validator = new MicrosoftEntraCredentialValidator(authentication -> {});

    var result = validator.validate(new MicrosoftEntraConfiguration(VALID));

    assertThat(result.status()).isEqualTo(Status.SUCCESS);
  }

  @Test
  void unauthorizedWhenEntraRejectsWithClientAuthenticationExceptionAndDoesNotLeakDetail() {
    var response = mock(HttpResponse.class);
    when(response.getStatusCode()).thenReturn(401);
    var validator =
        new MicrosoftEntraCredentialValidator(
            authentication -> {
              throw new ClientAuthenticationException(
                  "AADSTS7000215: Invalid client secret SENSITIVE-DETAIL", response);
            });

    var result = validator.validate(new MicrosoftEntraConfiguration(VALID));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("UNAUTHORIZED");
    assertThat(result.message()).doesNotContain("SENSITIVE-DETAIL");
  }

  @Test
  void errorWhenClientAuthenticationExceptionCarriesNoResponse() {
    var validator =
        new MicrosoftEntraCredentialValidator(
            authentication -> {
              throw new ClientAuthenticationException("no response", (HttpResponse) null);
            });

    var result = validator.validate(new MicrosoftEntraConfiguration(VALID));

    assertThat(result.code()).isEqualTo("ERROR");
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403})
  void unauthorizedWhenEntraRejectsWithMsalServiceExceptionWrappedByTheSdk(int statusCode) {
    var validator =
        new MicrosoftEntraCredentialValidator(
            authentication -> {
              throw new RuntimeException(
                  new ExecutionException(
                      msal("AADSTS7000215: Invalid client secret SENSITIVE-DETAIL", statusCode)));
            });

    var result = validator.validate(new MicrosoftEntraConfiguration(VALID));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("UNAUTHORIZED");
    assertThat(result.message()).doesNotContain("SENSITIVE-DETAIL");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {408, 429, 500, 503})
  void errorWhenMsalFailureIsNotACredentialRejection(Integer statusCode) {
    var validator =
        new MicrosoftEntraCredentialValidator(
            authentication -> {
              throw new RuntimeException(
                  new ExecutionException(msal("connect timed out SENSITIVE-DETAIL", statusCode)));
            });

    var result = validator.validate(new MicrosoftEntraConfiguration(VALID));

    assertThat(result.code()).isEqualTo("ERROR");
    assertThat(result.message()).doesNotContain("SENSITIVE-DETAIL");
  }

  @Test
  void errorWhenCredentialIsUnavailable() {
    var validator =
        new MicrosoftEntraCredentialValidator(
            authentication -> {
              throw new CredentialUnavailableException("unavailable SENSITIVE-DETAIL");
            });

    var result = validator.validate(new MicrosoftEntraConfiguration(VALID));

    assertThat(result.code()).isEqualTo("ERROR");
    assertThat(result.message()).doesNotContain("SENSITIVE-DETAIL");
  }

  @Test
  void errorOnOtherFailuresAndDoesNotLeakMessage() {
    var validator =
        new MicrosoftEntraCredentialValidator(
            authentication -> {
              throw new RuntimeException("boom SENSITIVE-DETAIL");
            });

    var result = validator.validate(new MicrosoftEntraConfiguration(VALID));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("ERROR");
    assertThat(result.message()).doesNotContain("SENSITIVE-DETAIL");
  }

  @Test
  void rejectsMissingAuthenticationWithoutCallingEntra() {
    var called = new boolean[] {false};
    var validator = new MicrosoftEntraCredentialValidator(authentication -> called[0] = true);

    var result = validator.validate(new MicrosoftEntraConfiguration(null));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("INVALID_INPUT");
    assertThat(called[0]).isFalse();
  }

  @Test
  void unsupportedForBearerTokenAuthenticationWithoutCallingEntra() {
    var called = new boolean[] {false};
    var validator = new MicrosoftEntraCredentialValidator(authentication -> called[0] = true);

    var result =
        validator.validate(new MicrosoftEntraConfiguration(new BearerAuthentication("some-token")));

    assertThat(result.status()).isEqualTo(Status.UNSUPPORTED);
    assertThat(called[0]).isFalse();
  }

  @Test
  void unsupportedForRefreshTokenAuthenticationWithoutCallingEntra() {
    var called = new boolean[] {false};
    var validator = new MicrosoftEntraCredentialValidator(authentication -> called[0] = true);

    var result =
        validator.validate(
            new MicrosoftEntraConfiguration(
                new RefreshTokenAuthentication("refresh-token", "client-id", "tenant-id", null)));

    assertThat(result.status()).isEqualTo(Status.UNSUPPORTED);
    assertThat(called[0]).isFalse();
  }

  @Test
  void isDiscoverableThroughTheServiceLoader() {
    assertThat(
            ServiceLoader.load(ConfigurationValidator.class).stream()
                .filter(provider -> provider.type().equals(MicrosoftEntraCredentialValidator.class))
                .map(ServiceLoader.Provider::get))
        .singleElement()
        .isInstanceOf(MicrosoftEntraCredentialValidator.class);
  }

  private static MsalServiceException msal(String message, Integer statusCode) {
    return new MsalServiceException(message, "invalid_client") {
      @Override
      public Integer statusCode() {
        return statusCode;
      }
    };
  }
}
