/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.request.connection;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.validation.ConfigurationValidationResult.Status;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import java.sql.SQLException;
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

  /** SQL state class 28 is "invalid authorization specification"; subclass varies. */
  @ParameterizedTest
  @ValueSource(strings = {"28000", "28P01", "28501"})
  void unauthorizedOnAnInvalidAuthorizationSqlState(String sqlState) {
    var result =
        JdbcConnectionValidator.classifyFailure(new SQLException("login failed", sqlState));

    assertThat(result.status()).isEqualTo(Status.FAILURE);
    assertThat(result.code()).isEqualTo("UNAUTHORIZED");
  }

  /** Oracle ORA-01017 and SQL Server 18456: a rejected login under an unrelated SQL state. */
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
    // Class 08, "connection exception" — the host is wrong or down, the credential may be fine.
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

  /** No returned message may carry a value from the credential or the driver. */
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
}
