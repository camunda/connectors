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
import io.camunda.connector.email.config.EmailAccountConfiguration;
import io.camunda.connector.email.config.ImapAccountConfiguration;
import io.camunda.connector.email.config.Pop3AccountConfiguration;
import io.camunda.connector.email.config.SmtpAccountConfiguration;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class EmailAccountValidatorTest extends BaseEmailTest {

  private static final String USERNAME = "test@camunda.com";
  private static final String PASSWORD = "password";

  private final EmailAccountValidator validator = new EmailAccountValidator();

  @Test
  void succeedsForAnSmtpAccount() {
    var result = validator.validate(smtp(PASSWORD));

    assertThat(result.status()).isEqualTo(Status.SUCCESS);
  }

  @Test
  void succeedsForAnImapAccount() {
    var result = validator.validate(imap(PASSWORD));

    assertThat(result.status()).isEqualTo(Status.SUCCESS);
  }

  @Test
  void succeedsForAPop3Account() {
    var result = validator.validate(pop3(PASSWORD));

    assertThat(result.status()).isEqualTo(Status.SUCCESS);
  }

  @Test
  void reportsUnauthorizedForAWrongImapPassword() {
    var result = validator.validate(imap("wrong-password"));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo(ErrorCode.UNAUTHORIZED.name());
    assertThat(result.message()).contains("IMAP");
  }

  /**
   * SMTP logs in through {@code Transport}, a different code path from the {@code Store} the other
   * two protocols use, so its failure branches need their own coverage.
   */
  @Test
  void reportsUnauthorizedForAWrongSmtpPassword() {
    var result = validator.validate(smtp("wrong-password"));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo(ErrorCode.UNAUTHORIZED.name());
    assertThat(result.message()).contains("SMTP");
  }

  @Test
  void reportsUnauthorizedForAWrongPop3Password() {
    var result = validator.validate(pop3("wrong-password"));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo(ErrorCode.UNAUTHORIZED.name());
    assertThat(result.message()).contains("POP3");
  }

  @Test
  void reportsAnErrorForAnUnreachableSmtpServer() {
    var result =
        validator.validate(
            new SmtpAccountConfiguration(
                USERNAME, PASSWORD, LOCALHOST, 1, CryptographicProtocol.NONE));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo(ErrorCode.ERROR.name());
    assertThat(result.message()).contains("SMTP");
  }

  /** A port nothing listens on: the connection fails, which is not an authentication verdict. */
  @Test
  void reportsAnErrorForAnUnreachableImapServer() {
    var result =
        validator.validate(
            new ImapAccountConfiguration(
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

    assertThat(validators).contains(EmailAccountValidator.class);
  }

  private EmailAccountConfiguration smtp(String password) {
    return new SmtpAccountConfiguration(
        USERNAME,
        password,
        LOCALHOST,
        Integer.valueOf(getUnsecureSmtpPort()),
        CryptographicProtocol.NONE);
  }

  private EmailAccountConfiguration imap(String password) {
    return new ImapAccountConfiguration(
        USERNAME,
        password,
        LOCALHOST,
        Integer.valueOf(getUnsecureImapPort()),
        CryptographicProtocol.NONE);
  }

  private EmailAccountConfiguration pop3(String password) {
    return new Pop3AccountConfiguration(
        USERNAME,
        password,
        LOCALHOST,
        Integer.valueOf(getUnsecurePop3Port()),
        CryptographicProtocol.NONE);
  }
}
