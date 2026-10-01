/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.google.gcs.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import io.camunda.connector.api.validation.ConfigurationValidationResult.Status;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.google.gcs.model.request.Authentication;
import io.camunda.connector.google.gcs.model.request.GcsCredentialConfiguration;
import java.io.IOException;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GcsCredentialValidatorTest {

  private static GcsCredentialConfiguration config(String jsonKey) {
    Authentication authentication = new Authentication();
    authentication.setJsonKey(jsonKey);
    return new GcsCredentialConfiguration(authentication);
  }

  private static HttpResponseException httpError(int statusCode) {
    return new HttpResponseException.Builder(statusCode, "status", new HttpHeaders())
        .setMessage("SENSITIVE-DETAIL")
        .build();
  }

  @Test
  void successWhenCredentialCheckPasses() {
    var validator = new GcsCredentialValidator(jsonKey -> {});

    var result = validator.validate(config("{\"type\":\"service_account\"}"));

    assertThat(result.status()).isEqualTo(Status.SUCCESS);
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403})
  void unauthorizedWhenGoogleRejectsAndDoesNotLeakDetail(int statusCode) {
    var validator =
        new GcsCredentialValidator(
            jsonKey -> {
              throw new IOException("wrapped", httpError(statusCode));
            });

    var result = validator.validate(config("{\"type\":\"service_account\"}"));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("UNAUTHORIZED");
    assertThat(result.message()).doesNotContain("SENSITIVE-DETAIL");
  }

  @ParameterizedTest
  @ValueSource(ints = {429, 500, 503})
  void errorWhenFailureIsNotACredentialRejection(int statusCode) {
    var validator =
        new GcsCredentialValidator(
            jsonKey -> {
              throw httpError(statusCode);
            });

    var result = validator.validate(config("{\"type\":\"service_account\"}"));

    assertThat(result.code()).isEqualTo("ERROR");
  }

  @Test
  void errorWhenKeyMalformed() {
    var validator = new GcsCredentialValidator();

    var result = validator.validate(config("not-a-json-key"));

    assertThat(result.code()).isEqualTo("ERROR");
  }

  @Test
  void invalidInputWhenKeyBlank() {
    var validator = new GcsCredentialValidator(jsonKey -> {});

    var result = validator.validate(config("  "));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("INVALID_INPUT");
  }

  @Test
  void discoveredByServiceLoader() {
    boolean found =
        ServiceLoader.load(ConfigurationValidator.class).stream()
            .anyMatch(p -> p.type().equals(GcsCredentialValidator.class));

    assertThat(found).isTrue();
  }
}
