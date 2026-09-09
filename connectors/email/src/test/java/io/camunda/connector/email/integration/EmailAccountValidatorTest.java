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
import io.camunda.connector.email.client.jakarta.outbound.EmailAccountValidator;
import io.camunda.connector.email.config.CryptographicProtocol;
import io.camunda.connector.email.outbound.model.EmailAccountConfiguration;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class EmailAccountValidatorTest extends BaseEmailTest {

  private static final String USERNAME = "test@camunda.com";
  private static final String PASSWORD = "password";

  private final EmailAccountValidator validator = new EmailAccountValidator();

  @Test
  void succeedsForAnSmtpOnlyAccount() {
    var result = validator.validate(smtp(PASSWORD));

    assertThat(result.status()).isEqualTo(Status.SUCCESS);
  }

  @Test
  void succeedsForAnImapOnlyAccount() {
    var result = validator.validate(imap(PASSWORD));

    assertThat(result.status()).isEqualTo(Status.SUCCESS);
  }

  @Test
  void succeedsForAPop3OnlyAccount() {
    var result = validator.validate(pop3(PASSWORD));

    assertThat(result.status()).isEqualTo(Status.SUCCESS);
  }

  @Test
  void succeedsForAnAccountThatSpeaksEveryProtocol() {
    var result =
        validator.validate(
            new EmailAccountConfiguration(
                USERNAME,
                PASSWORD,
                LOCALHOST,
                Integer.valueOf(getUnsecureSmtpPort()),
                CryptographicProtocol.NONE,
                LOCALHOST,
                Integer.valueOf(getUnsecureImapPort()),
                CryptographicProtocol.NONE,
                LOCALHOST,
                Integer.valueOf(getUnsecurePop3Port()),
                CryptographicProtocol.NONE));

    assertThat(result.status()).isEqualTo(Status.SUCCESS);
  }

  @Test
  void reportsUnauthorizedForAWrongImapPassword() {
    var result = validator.validate(imap("wrong-password"));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo(ErrorCode.UNAUTHORIZED.name());
    assertThat(result.message()).contains("IMAP");
  }

  @Test
  void reportsUnauthorizedForAWrongPop3Password() {
    var result = validator.validate(pop3("wrong-password"));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo(ErrorCode.UNAUTHORIZED.name());
    assertThat(result.message()).contains("POP3");
  }

  /** A port nothing listens on: the connection fails, which is not an authentication verdict. */
  @Test
  void reportsAnErrorForAnUnreachableServer() {
    var result =
        validator.validate(
            new EmailAccountConfiguration(
                USERNAME,
                PASSWORD,
                null,
                null,
                null,
                LOCALHOST,
                1,
                CryptographicProtocol.NONE,
                null,
                null,
                null));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo(ErrorCode.ERROR.name());
    assertThat(result.message()).contains("IMAP");
  }

  /** The first failing server decides, even when another one would have accepted the account. */
  @Test
  void failsWhenOnlyOneOfSeveralServersRejectsTheAccount() {
    var result =
        validator.validate(
            new EmailAccountConfiguration(
                USERNAME,
                PASSWORD,
                LOCALHOST,
                Integer.valueOf(getUnsecureSmtpPort()),
                CryptographicProtocol.NONE,
                LOCALHOST,
                1,
                CryptographicProtocol.NONE,
                null,
                null,
                null));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.message()).contains("IMAP");
  }

  @Test
  void reportsInvalidInputForAnAccountWithNoServer() {
    var result =
        validator.validate(
            new EmailAccountConfiguration(
                USERNAME, PASSWORD, null, null, null, null, null, null, null, null, null));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo(ErrorCode.INVALID_INPUT.name());
    assertThat(result.message()).contains("at least one of the SMTP, IMAP or POP3 servers");
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

    assertThat(validators).contains(EmailAccountValidator.class);
  }

  private EmailAccountConfiguration smtp(String password) {
    return new EmailAccountConfiguration(
        USERNAME,
        password,
        LOCALHOST,
        Integer.valueOf(getUnsecureSmtpPort()),
        CryptographicProtocol.NONE,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private EmailAccountConfiguration imap(String password) {
    return new EmailAccountConfiguration(
        USERNAME,
        password,
        null,
        null,
        null,
        LOCALHOST,
        Integer.valueOf(getUnsecureImapPort()),
        CryptographicProtocol.NONE,
        null,
        null,
        null);
  }

  private EmailAccountConfiguration pop3(String password) {
    return new EmailAccountConfiguration(
        USERNAME,
        password,
        null,
        null,
        null,
        null,
        null,
        null,
        LOCALHOST,
        Integer.valueOf(getUnsecurePop3Port()),
        CryptographicProtocol.NONE);
  }
}
