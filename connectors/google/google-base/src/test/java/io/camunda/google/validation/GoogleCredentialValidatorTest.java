/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.google.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import io.camunda.connector.api.validation.ConfigurationValidationResult.Status;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.google.model.Authentication;
import io.camunda.google.model.AuthenticationType;
import io.camunda.google.model.GoogleCredentialConfiguration;
import java.io.IOException;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GoogleCredentialValidatorTest {

  private static final Authentication BEARER =
      new Authentication(AuthenticationType.BEARER, "token", null, null, null);
  private static final Authentication REFRESH =
      new Authentication(AuthenticationType.REFRESH, null, "client", "secret", "refresh");

  private static GoogleCredentialConfiguration config(Authentication authentication) {
    return new GoogleCredentialConfiguration(authentication);
  }

  private static HttpResponseException httpError(int statusCode) {
    return new HttpResponseException.Builder(statusCode, "status", new HttpHeaders())
        .setMessage("SENSITIVE-DETAIL")
        .build();
  }

  @Test
  void successWhenTokenCheckPasses() {
    var validator = new GoogleCredentialValidator(authentication -> {});

    var result = validator.validate(config(BEARER));

    assertThat(result.status()).isEqualTo(Status.SUCCESS);
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403})
  void unauthorizedWhenGoogleRejectsAndDoesNotLeakDetail(int statusCode) {
    var validator =
        new GoogleCredentialValidator(
            authentication -> {
              throw httpError(statusCode);
            });

    var result = validator.validate(config(REFRESH));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("UNAUTHORIZED");
    assertThat(result.message()).doesNotContain("SENSITIVE-DETAIL");
  }

  @Test
  void unauthorizedWhenRejectionIsWrapped() {
    var validator =
        new GoogleCredentialValidator(
            authentication -> {
              throw new IOException("wrapped", httpError(401));
            });

    var result = validator.validate(config(REFRESH));

    assertThat(result.code()).isEqualTo("UNAUTHORIZED");
  }

  @ParameterizedTest
  @ValueSource(ints = {408, 429, 500, 503})
  void errorWhenFailureIsNotACredentialRejection(int statusCode) {
    var validator =
        new GoogleCredentialValidator(
            authentication -> {
              throw httpError(statusCode);
            });

    var result = validator.validate(config(BEARER));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("ERROR");
  }

  @Test
  void errorWhenTransientNonHttpFailure() {
    var validator =
        new GoogleCredentialValidator(
            authentication -> {
              throw new IOException("connection reset SENSITIVE-DETAIL");
            });

    var result = validator.validate(config(REFRESH));

    assertThat(result.code()).isEqualTo("ERROR");
    assertThat(result.message()).doesNotContain("SENSITIVE-DETAIL");
  }

  @Test
  void invalidInputWhenAuthenticationMissing() {
    var validator = new GoogleCredentialValidator(authentication -> {});

    var result = validator.validate(config(null));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("INVALID_INPUT");
  }

  @Test
  void unsupportedWhenAuthTypeIsServiceAccount() {
    var serviceAccount =
        new Authentication(AuthenticationType.SERVICE_ACCOUNT, null, null, null, null);
    var validator = new GoogleCredentialValidator(authentication -> {});

    var result = validator.validate(config(serviceAccount));

    assertThat(result.status()).isEqualTo(Status.UNSUPPORTED);
  }

  @Test
  void discoveredByServiceLoader() {
    boolean found =
        ServiceLoader.load(ConfigurationValidator.class).stream()
            .anyMatch(p -> p.type().equals(GoogleCredentialValidator.class));

    assertThat(found).isTrue();
  }
}
