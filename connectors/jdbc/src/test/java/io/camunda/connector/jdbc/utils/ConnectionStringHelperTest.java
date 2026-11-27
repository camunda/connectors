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
import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import io.camunda.connector.jdbc.model.request.connection.DetailedConnection;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ConnectionStringHelperTest {

  private static DetailedConnection connection(String host, String port, String databaseName) {
    return new DetailedConnection(host, port, "user", "password", databaseName, null);
  }

  @ParameterizedTest
  @CsvSource({
    "MARIADB, db.example.com, 3306, app, jdbc:mariadb://db.example.com:3306/app",
    "MYSQL, db-1.internal, 3306, my_app, jdbc:mysql://db-1.internal:3306/my_app",
    "POSTGRESQL, 10.0.0.12, 5432, app-prod, jdbc:postgresql://10.0.0.12:5432/app-prod",
    "POSTGRESQL, [2001:db8::1], 5432, app, jdbc:postgresql://[2001:db8::1]:5432/app",
    "MSSQL, db.example.com, 1433, My Database, jdbc:sqlserver://db.example.com:1433;databaseName=My Database",
    "MSSQL, db.example.com\\SQLEXPRESS, 1433, app, jdbc:sqlserver://db.example.com\\SQLEXPRESS:1433;databaseName=app",
    "MARIADB, db.example.com, 3306, , jdbc:mariadb://db.example.com:3306/"
  })
  void buildsTheUrl(
      SupportedDatabase database, String host, String port, String databaseName, String expected) {
    assertThat(
            ConnectionStringHelper.buildConnectionString(
                database, connection(host, port, databaseName)))
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "x?allowLocalInfile=true",
        "x&allowLocalInfile=true",
        "x;authentication=ActiveDirectoryManagedIdentity",
        "x#fragment",
        "{x};encrypt=false",
        "x\nallowLocalInfile=true"
      })
  void rejectsDatabaseNameThatAddsDriverOptions(String databaseName) {
    for (SupportedDatabase database : SupportedDatabase.values()) {
      assertThatThrownBy(() ->
              ConnectionStringHelper.buildConnectionString(
                  database, connection("db.example.com", "3306", databaseName)))
          .as("%s %s", database, databaseName)
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("Invalid database name");
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "db.example.com/x?allowLocalInfile=true",
        "db.example.com?allowLocalInfile=true",
        "db.example.com;encrypt=false",
        "user@db.example.com",
        "address=(host=db.example.com)(localSocket=/tmp/s)",
        "db.example.com ",
        ""
      })
  void rejectsHostThatIsNotAHost(String host) {
    for (SupportedDatabase database : SupportedDatabase.values()) {
      assertThatThrownBy(() ->
              ConnectionStringHelper.buildConnectionString(
                  database, connection(host, "3306", "app")))
          .as("%s %s", database, host)
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("Invalid host");
    }
  }

  @ParameterizedTest
  @EnumSource(value = SupportedDatabase.class, names = "MSSQL", mode = EnumSource.Mode.EXCLUDE)
  void rejectsInstanceNameOutsideSqlServer(SupportedDatabase database) {
    assertThatThrownBy(() ->
            ConnectionStringHelper.buildConnectionString(
                database, connection("db.example.com\\SQLEXPRESS", "3306", "app")))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("Invalid host");
  }

  @ParameterizedTest
  @ValueSource(strings = {"3306/x?allowLocalInfile=true", "3306;encrypt=false", "33o6", "", " "})
  void rejectsPortThatIsNotANumber(String port) {
    for (SupportedDatabase database : SupportedDatabase.values()) {
      assertThatThrownBy(() ->
              ConnectionStringHelper.buildConnectionString(
                  database, connection("db.example.com", port, "app")))
          .as("%s %s", database, port)
          .isInstanceOf(ConnectorException.class)
          .hasMessageContaining("Invalid port");
    }
  }
}
