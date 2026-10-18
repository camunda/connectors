/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.validation.ConfigurationValidationResult.ErrorCode;
import io.camunda.connector.api.validation.ConfigurationValidationResult.Status;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.email.client.jakarta.inbound.EmailInboundAccountValidator;
import io.camunda.connector.email.config.CryptographicProtocol;
import io.camunda.connector.email.config.EmailInboundAccountConfiguration;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class EmailInboundAccountValidatorTest extends BaseEmailTest {

  private static final String USERNAME = "test@camunda.com";
  private static final String PASSWORD = "password";

  private final EmailInboundAccountValidator validator = new EmailInboundAccountValidator();

  @Test
  void succeedsForAValidAccount() {
    var result = validator.validate(account(PASSWORD));

    assertThat(result.status()).isEqualTo(Status.SUCCESS);
  }

  @Test
  void reportsUnauthorizedForAWrongPassword() {
    var result = validator.validate(account("wrong-password"));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo(ErrorCode.UNAUTHORIZED.name());
    assertThat(result.message()).contains("IMAP");
  }

  /** A port nothing listens on: the connection fails, which is not an authentication verdict. */
  @Test
  void reportsAnErrorForAnUnreachableServer() {
    var result =
        validator.validate(
            new EmailInboundAccountConfiguration(
                USERNAME, PASSWORD, LOCALHOST, 1, CryptographicProtocol.NONE));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo(ErrorCode.ERROR.name());
    assertThat(result.message()).contains("IMAP");
  }

  /**
   * A missing {@code META-INF/services} entry fails silently - the runtime simply never finds the
   * validator - and nothing else in the build catches it.
   */
  @Test
  void isDiscoverableThroughTheServiceLoader() {
    var validators =
        Stream.of(ServiceLoader.load(ConfigurationValidator.class))
            .flatMap(loader -> loader.stream().map(ServiceLoader.Provider::type))
            .toList();

    assertThat(validators).contains(EmailInboundAccountValidator.class);
  }

  private EmailInboundAccountConfiguration account(String password) {
    return new EmailInboundAccountConfiguration(
        USERNAME,
        password,
        LOCALHOST,
        Integer.valueOf(getUnsecureImapPort()),
        CryptographicProtocol.NONE);
  }
}
