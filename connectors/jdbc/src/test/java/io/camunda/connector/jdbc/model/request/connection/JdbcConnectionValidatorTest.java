/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.request.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.camunda.connector.api.validation.ConfigurationValidationResult.Status;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JdbcConnectionValidatorTest {

  private static final JdbcConnectionConfiguration VALID =
      new JdbcConnectionConfiguration(
          SupportedDatabase.POSTGRESQL,
          "db.example.com",
          "5432",
          "orders",
          "the-login",
          "the-secret");

  @Test
  void rejectsAMissingDatabase() {
    var result =
        new JdbcConnectionValidator()
            .validate(
                new JdbcConnectionConfiguration(
                    null, "db.example.com", "5432", "orders", "the-login", "the-secret"));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("INVALID_INPUT");
  }

  @ParameterizedTest
  @ValueSource(strings = {"28000", "28P01", "28501"})
  void unauthorizedOnAnInvalidAuthorizationSqlState(String sqlState) {
    var result =
        JdbcConnectionValidator.classifyFailure(new SQLException("login failed", sqlState));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("UNAUTHORIZED");
  }

  @ParameterizedTest
  @ValueSource(ints = {1017, 18456})
  void unauthorizedOnAVendorLoginErrorCode(int vendorCode) {
    var result =
        JdbcConnectionValidator.classifyFailure(
            new SQLException("login failed", "72000", vendorCode));

    assertThat(result.code()).isEqualTo("UNAUTHORIZED");
  }

  @Test
  void errorWhenTheDatabaseIsUnreachable() {
    var result =
        JdbcConnectionValidator.classifyFailure(new SQLException("connection refused", "08001"));

    assertThat(result.code()).isEqualTo("ERROR");
  }

  @Test
  void errorWhenTheSqlStateIsAbsent() {
    var result = JdbcConnectionValidator.classifyFailure(new SQLException("no state"));

    assertThat(result.code()).isEqualTo("ERROR");
  }

  @Test
  void errorWhenTheDriverIsNotOnTheClasspath() {
    var result =
        JdbcConnectionValidator.classifyFailure(
            new ClassNotFoundException("oracle.jdbc.OracleDriver"));

    assertThat(result.code()).isEqualTo("ERROR");
    assertThat(result.message()).isEqualTo(JdbcConnectionValidator.DRIVER_MISSING_MESSAGE);
  }

  @Test
  void errorOnAnyOtherException() {
    var result = JdbcConnectionValidator.classifyFailure(new RuntimeException("boom"));

    assertThat(result.code()).isEqualTo("ERROR");
  }

  @Test
  void neverSurfacesDetail() {
    assertThat(
            JdbcConnectionValidator.classifyFailure(new SQLException("secret", "28000")).message())
        .isEqualTo(JdbcConnectionValidator.UNAUTHORIZED_MESSAGE);
    assertThat(
            JdbcConnectionValidator.classifyFailure(new SQLException("secret", "08001")).message())
        .isEqualTo(JdbcConnectionValidator.GENERIC_MESSAGE);
  }

  @Test
  void keepsTheLoginOutOfToString() {
    assertThat(VALID.toString())
        .contains("POSTGRESQL", "db.example.com", "orders")
        .doesNotContain("the-login", "the-secret");
  }

  @Test
  void givesUpOnAHostThatAcceptsTheConnectionAndNeverAnswers() throws Exception {
    // Never accept()ed, so the TCP handshake completes and the driver then waits on a reply that
    // never comes -- with no login timeout the validator would hang here for good.
    try (ServerSocket blackHole = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      var configuration =
          new JdbcConnectionConfiguration(
              SupportedDatabase.POSTGRESQL,
              blackHole.getInetAddress().getHostAddress(),
              String.valueOf(blackHole.getLocalPort()),
              "orders",
              "the-login",
              "the-secret");
      var validator = new JdbcConnectionValidator(Duration.ofSeconds(1));

      var result =
          assertTimeoutPreemptively(
              Duration.ofSeconds(30), () -> validator.validate(configuration));

      assertThat(result.status()).isEqualTo(Status.FAILURE);
      assertThat(result.code()).isEqualTo("ERROR");
    }
  }

  @Test
  @SuppressWarnings("rawtypes")
  void isDiscoverableViaTheServiceLoader() {
    assertThat(
            ServiceLoader.load(ConfigurationValidator.class).stream()
                .map(ServiceLoader.Provider::type))
        .contains(JdbcConnectionValidator.class);
  }
}
