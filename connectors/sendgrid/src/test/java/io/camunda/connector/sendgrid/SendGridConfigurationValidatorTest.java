/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sendgrid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.sendgrid.model.SendGridApiKeyConfiguration;
import java.io.IOException;
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
class SendGridConfigurationValidatorTest {

  private static final SendGridApiKeyConfiguration CREDENTIAL =
      new SendGridApiKeyConfiguration("SG.key");

  @Mock private SendGridClientSupplier sendGridSupplier;
  @Mock private SendGrid sendGrid;
  private SendGridConfigurationValidator validator;

  @BeforeEach
  void setUp() {
    validator = new SendGridConfigurationValidator(sendGridSupplier);
  }

  @Test
  void returnsSuccess_whenSendGridAcceptsKey() throws IOException {
    when(sendGridSupplier.sendGrid("SG.key")).thenReturn(sendGrid);
    var captor = ArgumentCaptor.forClass(Request.class);
    when(sendGrid.api(captor.capture())).thenReturn(new Response(200, "{}", Map.of()));

    assertThat(validator.validate(CREDENTIAL)).isEqualTo(ConfigurationValidationResult.success());
    assertThat(captor.getValue().getMethod()).isEqualTo(Method.GET);
    assertThat(captor.getValue().getEndpoint()).isEqualTo("scopes");
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403})
  void returnsUnauthorized_whenSendGridRejectsKey(int status) throws IOException {
    when(sendGridSupplier.sendGrid(any())).thenReturn(sendGrid);
    when(sendGrid.api(any())).thenReturn(new Response(status, "{}", Map.of()));

    assertThat(validator.validate(CREDENTIAL))
        .isEqualTo(
            ConfigurationValidationResult.failure(
                ErrorCode.UNAUTHORIZED, SendGridConfigurationValidator.UNAUTHORIZED_MESSAGE));
  }

  @Test
  void returnsError_onOtherHttpFailure() throws IOException {
    when(sendGridSupplier.sendGrid(any())).thenReturn(sendGrid);
    when(sendGrid.api(any())).thenReturn(new Response(503, "{}", Map.of()));

    assertThat(validator.validate(CREDENTIAL))
        .isEqualTo(
            ConfigurationValidationResult.failure(
                ErrorCode.ERROR, SendGridConfigurationValidator.GENERIC_MESSAGE));
  }

  @Test
  void returnsError_onConnectionFailure() throws IOException {
    when(sendGridSupplier.sendGrid(any())).thenReturn(sendGrid);
    when(sendGrid.api(any())).thenThrow(new IOException("refused"));

    assertThat(validator.validate(CREDENTIAL))
        .isEqualTo(
            ConfigurationValidationResult.failure(
                ErrorCode.ERROR, SendGridConfigurationValidator.GENERIC_MESSAGE));
  }

  @Test
  void returnsInvalidInput_whenKeyMissing() {
    var expected =
        ConfigurationValidationResult.failure(
            ErrorCode.INVALID_INPUT, SendGridConfigurationValidator.MISSING_INPUT_MESSAGE);

    assertThat(validator.validate(new SendGridApiKeyConfiguration(null))).isEqualTo(expected);
    assertThat(validator.validate(new SendGridApiKeyConfiguration(" "))).isEqualTo(expected);
    verifyNoInteractions(sendGridSupplier);
  }

  @Test
  void isDiscoverableViaTheServiceLoader() {
    assertThat(
            ServiceLoader.load(ConfigurationValidator.class).stream()
                .filter(provider -> provider.type().equals(SendGridConfigurationValidator.class))
                .map(ServiceLoader.Provider::get))
        .singleElement()
        .isInstanceOf(SendGridConfigurationValidator.class);
  }
}
