/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.box.sdkgen.box.errors.BoxAPIError;
import com.box.sdkgen.box.errors.RequestInfo;
import com.box.sdkgen.box.errors.ResponseInfo;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.box.model.BoxCredentialConfiguration;
import io.camunda.connector.box.model.BoxRequest.Authentication;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BoxCredentialValidatorTest {

  private static final BoxCredentialConfiguration CREDENTIAL =
      new BoxCredentialConfiguration(
          new Authentication.ClientCredentialsEnterprise("client", "secret", "enterprise-1"));

  private static BoxAPIError apiError(int status) {
    return new BoxAPIError(
        "status " + status,
        new RequestInfo("POST", "https://api.box.com/oauth2/token", Map.of(), Map.of()),
        new ResponseInfo(status, Map.of()));
  }

  @Test
  void succeeds_whenBoxAcceptsTheCredential() {
    List<Authentication> checked = new ArrayList<>();

    var result = new BoxCredentialValidator(checked::add).validate(CREDENTIAL);

    assertThat(result).isEqualTo(ConfigurationValidationResult.success());
    assertThat(checked).containsExactly((Authentication) CREDENTIAL.authentication());
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403})
  void reportsUnauthorized_whenBoxRejectsTheCredential(int status) {
    var result =
        new BoxCredentialValidator(
                authentication -> {
                  throw new RuntimeException("wrapped", apiError(status));
                })
            .validate(CREDENTIAL);

    assertThat(result)
        .isEqualTo(
            ConfigurationValidationResult.failure(
                ErrorCode.UNAUTHORIZED, BoxCredentialValidator.UNAUTHORIZED_MESSAGE));
  }

  @Test
  void reportsError_onOtherStatus() {
    var result =
        new BoxCredentialValidator(
                authentication -> {
                  throw apiError(500);
                })
            .validate(CREDENTIAL);

    assertThat(result)
        .isEqualTo(
            ConfigurationValidationResult.failure(
                ErrorCode.ERROR, BoxCredentialValidator.GENERIC_MESSAGE));
  }

  @Test
  void reportsError_onNetworkFailure() {
    var result =
        new BoxCredentialValidator(
                authentication -> {
                  throw new RuntimeException("connection refused");
                })
            .validate(CREDENTIAL);

    assertThat(result)
        .isEqualTo(
            ConfigurationValidationResult.failure(
                ErrorCode.ERROR, BoxCredentialValidator.GENERIC_MESSAGE));
  }

  @Test
  void reportsInvalidInput_whenAuthenticationMissing() {
    var result =
        new BoxCredentialValidator(authentication -> {})
            .validate(new BoxCredentialConfiguration(null));

    assertThat(result)
        .isEqualTo(
            ConfigurationValidationResult.failure(
                ErrorCode.INVALID_INPUT, BoxCredentialValidator.MISSING_AUTH_MESSAGE));
  }

  @Test
  void isDiscoverableViaServiceLoader() {
    assertThat(ServiceLoader.load(ConfigurationValidator.class).stream())
        .anyMatch(provider -> provider.type() == BoxCredentialValidator.class);
  }
}
