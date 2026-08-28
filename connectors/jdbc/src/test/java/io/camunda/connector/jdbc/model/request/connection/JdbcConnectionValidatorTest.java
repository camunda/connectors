/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.request.connection;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidationResult.Status;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import java.sql.SQLException;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JdbcConnectionValidatorTest {

  private static final String SENSITIVE = "SENSITIVE-DETAIL";

  private static final JdbcConnectionConfiguration VALID =
      new JdbcConnectionConfiguration(
          SupportedDatabase.POSTGRESQL,
          "db.example.com",
          "5432",
          "orders",
          "the-login",
          "the-secret");

  @Test
  void successWhenTheConnectionOpens() {
    var validator = new JdbcConnectionValidator(configuration -> {});

    assertThat(validator.validate(VALID).status()).isEqualTo(Status.SUCCESS);
  }

  @Test
  void rejectsAMissingDatabaseWithoutConnecting() {
    var connected = new boolean[] {false};
    var validator = new JdbcConnectionValidator(configuration -> connected[0] = true);

    var result =
        validator.validate(
            new JdbcConnectionConfiguration(
                null, "db.example.com", "5432", "orders", "the-login", "the-secret"));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("INVALID_INPUT");
    assertThat(connected[0]).isFalse();
  }

  /** SQL state class 28 is "invalid authorization specification"; subclass varies. */
  @ParameterizedTest
  @ValueSource(strings = {"28000", "28P01", "28501"})
  void unauthorizedOnAnInvalidAuthorizationSqlState(String sqlState) {
    var result =
        validateWith(
            configuration -> {
              throw new SQLException("login failed " + SENSITIVE, sqlState);
            });

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("UNAUTHORIZED");
    assertThat(result.message()).doesNotContain(SENSITIVE);
  }

  /** Oracle ORA-01017 and SQL Server 18456: a rejected login under an unrelated SQL state. */
  @ParameterizedTest
  @ValueSource(ints = {1017, 18456})
  void unauthorizedOnAVendorLoginErrorCode(int vendorCode) {
    var result =
        validateWith(
            configuration -> {
              throw new SQLException("login failed " + SENSITIVE, "72000", vendorCode);
            });

    assertThat(result.code()).isEqualTo("UNAUTHORIZED");
    assertThat(result.message()).doesNotContain(SENSITIVE);
  }

  @Test
  void errorWhenTheDatabaseIsUnreachable() {
    // Class 08, "connection exception" — the host is wrong or down, the credential may be fine.
    var result =
        validateWith(
            configuration -> {
              throw new SQLException("connection refused " + SENSITIVE, "08001");
            });

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("ERROR");
    assertThat(result.message()).doesNotContain(SENSITIVE);
  }

  @Test
  void errorWhenTheSqlStateIsAbsent() {
    var result =
        validateWith(
            configuration -> {
              throw new SQLException("no state " + SENSITIVE);
            });

    assertThat(result.code()).isEqualTo("ERROR");
    assertThat(result.message()).doesNotContain(SENSITIVE);
  }

  @Test
  void errorWhenTheDriverIsNotOnTheClasspath() {
    var result =
        validateWith(
            configuration -> {
              throw new ClassNotFoundException("oracle.jdbc.OracleDriver " + SENSITIVE);
            });

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("ERROR");
    assertThat(result.message())
        .isEqualTo(JdbcConnectionValidator.DRIVER_MISSING_MESSAGE)
        .doesNotContain(SENSITIVE);
  }

  @Test
  void errorOnAnyOtherException() {
    var result =
        validateWith(
            configuration -> {
              throw new RuntimeException("boom " + SENSITIVE);
            });

    assertThat(result.code()).isEqualTo("ERROR");
    assertThat(result.message()).doesNotContain(SENSITIVE);
  }

  @Test
  void keepsTheLoginOutOfToString() {
    // The database is not a secret and stays visible; the login and password do not.
    assertThat(VALID.toString())
        .contains("POSTGRESQL", "db.example.com", "orders")
        .doesNotContain("the-login", "the-secret");
  }

  @Test
  @SuppressWarnings("rawtypes")
  void isDiscoverableViaTheServiceLoader() {
    // A missing META-INF/services entry silently leaves the credential unvalidatable.
    assertThat(
            ServiceLoader.load(ConfigurationValidator.class).stream()
                .map(ServiceLoader.Provider::type))
        .contains(JdbcConnectionValidator.class);
  }

  private ConfigurationValidationResult validateWith(
      JdbcConnectionValidator.ConnectionCheck check) {
    return new JdbcConnectionValidator(check).validate(VALID);
  }
}
