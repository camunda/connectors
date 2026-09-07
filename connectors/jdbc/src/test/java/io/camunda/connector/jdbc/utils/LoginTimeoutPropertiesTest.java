/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LoginTimeoutPropertiesTest {

  @ParameterizedTest
  @EnumSource(SupportedDatabase.class)
  void boundsEverySupportedDatabase(SupportedDatabase database) {
    assertThat(propertiesFor(database, Duration.ofSeconds(10))).isNotEmpty();
  }

  @Test
  void usesSecondsForPostgresql() {
    assertThat(propertiesFor(SupportedDatabase.POSTGRESQL, Duration.ofSeconds(10)))
        .containsExactly(entry("loginTimeout", "10"));
  }

  @Test
  void usesSecondsForSqlServer() {
    assertThat(propertiesFor(SupportedDatabase.MSSQL, Duration.ofSeconds(10)))
        .containsExactly(entry("loginTimeout", "10"));
  }

  @Test
  void usesTheDriverPrefixedSecondsPropertyForOracle() {
    assertThat(propertiesFor(SupportedDatabase.ORACLE, Duration.ofSeconds(10)))
        .containsExactly(entry("oracle.jdbc.loginTimeout", "10"));
  }

  @ParameterizedTest
  @EnumSource(
      value = SupportedDatabase.class,
      names = {"MARIADB", "MYSQL"})
  void usesMillisecondsForTheMariadbDriver(SupportedDatabase database) {
    assertThat(propertiesFor(database, Duration.ofSeconds(10)))
        .containsOnly(entry("connectTimeout", "10000"), entry("socketTimeout", "10000"));
  }

  @ParameterizedTest
  @EnumSource(SupportedDatabase.class)
  void neverRoundsDownToTheDriversNoTimeoutValue(SupportedDatabase database) {
    assertThat(propertiesFor(database, Duration.ofMillis(1)).values()).doesNotContain("0");
  }

  @ParameterizedTest
  @EnumSource(SupportedDatabase.class)
  void leavesAnExplicitValueAlone(SupportedDatabase database) {
    Properties properties = new Properties();
    properties.put("loginTimeout", "600");
    properties.put("oracle.jdbc.loginTimeout", "600");
    properties.put("connectTimeout", "600000");
    properties.put("socketTimeout", "600000");

    LoginTimeoutProperties.applyTo(properties, database, Duration.ofSeconds(10));

    assertThat(properties.values()).containsOnly("600", "600000");
  }

  private static Properties propertiesFor(SupportedDatabase database, Duration timeout) {
    Properties properties = new Properties();
    LoginTimeoutProperties.applyTo(properties, database, timeout);
    return properties;
  }
}
