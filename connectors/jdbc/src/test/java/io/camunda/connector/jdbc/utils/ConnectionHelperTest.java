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
import io.camunda.connector.jdbc.model.request.JdbcRequest;
import io.camunda.connector.jdbc.model.request.JdbcRequestData;
import io.camunda.connector.jdbc.model.request.SupportedDatabase;
import io.camunda.connector.jdbc.model.request.connection.DetailedConnection;
import io.camunda.connector.jdbc.model.request.connection.JdbcConnection;
import io.camunda.connector.jdbc.model.request.connection.JdbcConnectionConfiguration;
import org.junit.jupiter.api.Test;

/** Verifies the per-connector consumption of a bound connection credential (configuration). */
class ConnectionHelperTest {

  private static final JdbcRequestData DATA = new JdbcRequestData(false, "SELECT 1");

  private static final JdbcConnectionConfiguration CREDENTIAL =
      new JdbcConnectionConfiguration("cred-host", "5432", "cred-db", "cred-user", "cred-pass");

  private static final DetailedConnection INLINE =
      new DetailedConnection(
          "inline-host", "3306", "inline-user", "inline-pass", "inline-db", null);

  @Test
  void usesConfigurationWhenBound() {
    var request = new JdbcRequest(SupportedDatabase.POSTGRESQL, null, DATA, CREDENTIAL);

    JdbcConnection resolved = ConnectionHelper.resolveConnection(request);

    assertThat(resolved).isInstanceOf(DetailedConnection.class);
    var detailed = (DetailedConnection) resolved;
    assertThat(detailed.host()).isEqualTo("cred-host");
    assertThat(detailed.port()).isEqualTo("5432");
    assertThat(detailed.username()).isEqualTo("cred-user");
    assertThat(detailed.password()).isEqualTo("cred-pass");
    assertThat(detailed.databaseName()).isEqualTo("cred-db");
  }

  @Test
  void fallsBackToInlineWhenNoConfiguration() {
    var request = new JdbcRequest(SupportedDatabase.MYSQL, INLINE, DATA);

    JdbcConnection resolved = ConnectionHelper.resolveConnection(request);

    assertThat(resolved).isSameAs(INLINE);
  }

  @Test
  void configurationTakesPrecedenceOverInline() {
    var request = new JdbcRequest(SupportedDatabase.POSTGRESQL, INLINE, DATA, CREDENTIAL);

    var resolved = (DetailedConnection) ConnectionHelper.resolveConnection(request);

    assertThat(resolved.host()).isEqualTo("cred-host");
  }

  @Test
  void throwsWhenNeitherProvided() {
    var request = new JdbcRequest(SupportedDatabase.POSTGRESQL, null, DATA);

    assertThatThrownBy(() -> ConnectionHelper.resolveConnection(request))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("No JDBC connection provided");
  }
}
