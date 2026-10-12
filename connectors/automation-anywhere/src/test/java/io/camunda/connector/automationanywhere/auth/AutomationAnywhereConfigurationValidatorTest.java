/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.error.ConnectorExceptionBuilder;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.automationanywhere.model.request.AutomationAnywhereConfiguration;
import io.camunda.connector.automationanywhere.model.request.auth.ApiKeyAuthentication;
import io.camunda.connector.automationanywhere.model.request.auth.PasswordBasedAuthentication;
import io.camunda.connector.automationanywhere.model.request.auth.TokenBasedAuthentication;
import io.camunda.connector.http.base.HttpService;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.net.ConnectException;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AutomationAnywhereConfigurationValidatorTest {

  private static final String URL = "https://control-room.example.com";
  private static final AutomationAnywhereConfiguration PASSWORD_CREDENTIAL =
      new AutomationAnywhereConfiguration(
          URL, new PasswordBasedAuthentication("jane", "pw", false));

  @Mock private HttpService httpService;
  private AutomationAnywhereConfigurationValidator validator;

  @BeforeEach
  void setUp() {
    validator =
        new AutomationAnywhereConfigurationValidator(
            httpService, ConnectorsObjectMapperSupplier.getCopy());
  }

  @Test
  void returnsSuccess_whenControlRoomIssuesToken() {
    var captor = ArgumentCaptor.forClass(HttpCommonRequest.class);
    when(httpService.executeConnectorRequest(captor.capture()))
        .thenReturn(new HttpCommonResult(200, Map.of(), Map.of("token", "abc")));

    var result =
        validator.validate(
            new AutomationAnywhereConfiguration(URL, new ApiKeyAuthentication("bot", "key")));

    assertThat(result).isEqualTo(ConfigurationValidationResult.success());
    assertThat(captor.getValue().getUrl()).isEqualTo(URL + "/v1/authentication");
    assertThat(captor.getValue().getBody()).isEqualTo(Map.of("username", "bot", "apiKey", "key"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"401", "403"})
  void returnsUnauthorized_whenControlRoomRejectsLogin(String status) {
    when(httpService.executeConnectorRequest(any()))
        .thenThrow(new ConnectorExceptionBuilder().errorCode(status).message("nope").build());

    assertThat(validator.validate(PASSWORD_CREDENTIAL))
        .isEqualTo(
            ConfigurationValidationResult.failure(
                ErrorCode.UNAUTHORIZED,
                AutomationAnywhereConfigurationValidator.UNAUTHORIZED_MESSAGE));
  }

  @Test
  void returnsError_onOtherHttpFailure() {
    when(httpService.executeConnectorRequest(any()))
        .thenThrow(new ConnectorExceptionBuilder().errorCode("503").message("down").build());

    assertThat(validator.validate(PASSWORD_CREDENTIAL))
        .isEqualTo(
            ConfigurationValidationResult.failure(
                ErrorCode.ERROR, AutomationAnywhereConfigurationValidator.GENERIC_MESSAGE));
  }

  @Test
  void returnsError_onConnectionFailure() {
    when(httpService.executeConnectorRequest(any()))
        .thenAnswer(
            invocation -> {
              throw new ConnectException("refused");
            });

    assertThat(validator.validate(PASSWORD_CREDENTIAL))
        .isEqualTo(
            ConfigurationValidationResult.failure(
                ErrorCode.ERROR, AutomationAnywhereConfigurationValidator.GENERIC_MESSAGE));
  }

  @Test
  void returnsError_whenResponseCarriesNoToken() {
    when(httpService.executeConnectorRequest(any()))
        .thenReturn(new HttpCommonResult(200, Map.of(), Map.of()));

    assertThat(validator.validate(PASSWORD_CREDENTIAL))
        .isEqualTo(
            ConfigurationValidationResult.failure(
                ErrorCode.ERROR, AutomationAnywhereConfigurationValidator.GENERIC_MESSAGE));
  }

  @Test
  void returnsUnsupported_forTokenCredential() {
    assertThat(
            validator.validate(
                new AutomationAnywhereConfiguration(URL, new TokenBasedAuthentication("t"))))
        .isEqualTo(ConfigurationValidationResult.unsupported());
    verifyNoInteractions(httpService);
  }

  @Test
  void returnsInvalidInput_whenUrlOrAuthenticationMissing() {
    var expected =
        ConfigurationValidationResult.failure(
            ErrorCode.INVALID_INPUT,
            AutomationAnywhereConfigurationValidator.MISSING_INPUT_MESSAGE);

    assertThat(validator.validate(new AutomationAnywhereConfiguration(URL, null)))
        .isEqualTo(expected);
    assertThat(
            validator.validate(
                new AutomationAnywhereConfiguration(" ", new TokenBasedAuthentication("t"))))
        .isEqualTo(expected);
    verifyNoInteractions(httpService);
  }

  @Test
  void isDiscoverableViaTheServiceLoader() {
    assertThat(
            ServiceLoader.load(ConfigurationValidator.class).stream()
                .filter(
                    provider ->
                        provider.type().equals(AutomationAnywhereConfigurationValidator.class))
                .map(ServiceLoader.Provider::get))
        .singleElement()
        .isInstanceOf(AutomationAnywhereConfigurationValidator.class);
  }
}
