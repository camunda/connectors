/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mariadb.jdbc.Configuration;

class MariaDbConnectionOpenerTest {

  private static final String URL = "jdbc:mariadb://db.example.com:3306/app";

  private static Properties properties(Map<String, String> entries) {
    Properties properties = new Properties();
    properties.putAll(entries);
    return properties;
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "jdbc:mariadb://db.example.com:3306/app",
        "jdbc:mariadb://db.example.com:3306/app?useSsl=true",
        "jdbc:mysql://db.example.com:3306/app",
        "jdbc:mysql://db.example.com:3306/app?permitMysqlScheme",
        "jdbc:mysql://db.example.com:3306/app?useSsl=true",
        "jdbc:mariadb:loadbalance://db1.example.com,db2.example.com/app"
      })
  void disablesLocalInfileByDefault(String url) throws SQLException {
    Configuration configuration =
        MariaDbConnectionOpener.toSafeConfiguration(url, new Properties());

    assertThat(configuration.allowLocalInfile()).isFalse();
  }

  @Test
  void driverEnablesLocalInfileOnItsOwn() throws SQLException {
    // The reason the option has to be set at all: this is the driver's default.
    assertThat(Configuration.parse(URL, new Properties()).allowLocalInfile()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"allowLocalInfile", "ALLOWLOCALINFILE", "allowlocalinfile"})
  void rejectsLocalInfileEnabledByProperty(String key) {
    for (String value : new String[] {"true", "TRUE", "1", ""}) {
      assertThatThrownBy(() ->
              MariaDbConnectionOpener.toSafeConfiguration(URL, properties(Map.of(key, value))))
          .as("%s=%s", key, value)
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("allowLocalInfile");
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "jdbc:mariadb://db.example.com:3306/app?allowLocalInfile=true",
        "jdbc:mariadb://db.example.com:3306/app?allowLocalInfile",
        "jdbc:mariadb://db.example.com:3306/app?useSsl=true&ALLOWLOCALINFILE=1",
        "jdbc:mariadb://db.example.com:3306/app?allowLocalInfile=false&allowlocalinfile=true",
        "jdbc:mariadb://db.example.com:3306?allowLocalInfile=true",
        "jdbc:mysql://db.example.com:3306/app?allowLocalInfile=true",
        "jdbc:mysql://db.example.com:3306/app?permitMysqlScheme&allowLocalInfile=true"
      })
  void rejectsLocalInfileEnabledByUrl(String url) {
    assertThatThrownBy(() -> MariaDbConnectionOpener.toSafeConfiguration(url, new Properties()))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("allowLocalInfile");
  }

  @Test
  void rejectsLocalInfileEnabledByUrlWhenPropertyDisablesIt() {
    assertThatThrownBy(() ->
            MariaDbConnectionOpener.toSafeConfiguration(
                URL + "?allowLocalInfile=true", properties(Map.of("allowLocalInfile", "false"))))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("allowLocalInfile");
  }

  @ParameterizedTest
  @ValueSource(strings = {"false", "FALSE", "0"})
  void acceptsLocalInfileDisabledExplicitly(String value) throws SQLException {
    assertThat(
            MariaDbConnectionOpener.toSafeConfiguration(
                    URL, properties(Map.of("AllowLocalInfile", value)))
                .allowLocalInfile())
        .isFalse();
    assertThat(
            MariaDbConnectionOpener.toSafeConfiguration(
                    URL + "?allowLocalInfile=" + value, new Properties())
                .allowLocalInfile())
        .isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"ENV", "PROPERTY"})
  void rejectsCredentialsReadFromTheRuntime(String credentialType) {
    assertThatThrownBy(() ->
            MariaDbConnectionOpener.toSafeConfiguration(
                URL, properties(Map.of("credentialType", credentialType, "userKey", "HOME"))))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("credential type");
    assertThatThrownBy(() ->
            MariaDbConnectionOpener.toSafeConfiguration(
                URL + "?CREDENTIALTYPE=" + credentialType + "&userKey=HOME", new Properties()))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("credential type");
  }

  @Test
  void driverOnlyKnowsTheCredentialTypesByTheirExactName() {
    // Why the two names above are the whole list: another spelling never reaches a plugin.
    assertThatThrownBy(() ->
            MariaDbConnectionOpener.toSafeConfiguration(
                URL, properties(Map.of("credentialType", "env"))))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("No identity plugin");
  }

  @Test
  void keepsTheCallersOptions() throws SQLException {
    Properties given =
        properties(Map.of("user", "alice", "password", "s3cret", "connectTimeout", "1500"));

    Configuration configuration =
        MariaDbConnectionOpener.toSafeConfiguration(URL + "?socketTimeout=2500", given);

    assertThat(configuration.user()).isEqualTo("alice");
    assertThat(configuration.password()).isEqualTo("s3cret");
    assertThat(configuration.database()).isEqualTo("app");
    assertThat(configuration.connectTimeout()).isEqualTo(1500);
    assertThat(configuration.socketTimeout()).isEqualTo(2500);
    assertThat(configuration.addresses().get(0).host).isEqualTo("db.example.com");
    assertThat(given).doesNotContainKey("allowLocalInfile").doesNotContainKey("socketTimeout");
  }

  @Test
  void handlesBothSchemesOfTheDriver() {
    assertThat(MariaDbConnectionOpener.handles("jdbc:mariadb://h:1/d")).isTrue();
    assertThat(MariaDbConnectionOpener.handles("jdbc:mysql://h:1/d")).isTrue();
    assertThat(MariaDbConnectionOpener.handles("jdbc:postgresql://h:1/d")).isFalse();
    assertThat(MariaDbConnectionOpener.handles("jdbc:sqlserver://h:1")).isFalse();
    assertThat(MariaDbConnectionOpener.handles(null)).isFalse();
  }
}
